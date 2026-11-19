# 05 — C/C++ and JNI Native Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** 10 C++ files + 2 headers, 8,713 LOC; CMake and Gradle native configuration.
> **Method:** static reading, symbol extraction, JNI reference/free-pattern search. No native build
> was run locally; CI native-test evidence is cited separately.

---

## 1. Native inventory and build graph

| Artifact | Source | Type |
| --- | --- | --- |
| `libnexoragit.so` | `native/config/src/secure_config_jni.cpp` | SHARED, runtime OAuth config |
| `libnexoragit_native.so` | `native/git/src/git_jni.cpp` + `syntax_jni.cpp` | SHARED, Git + syntax JNI |
| `nexoragit_core` | `git_core.cpp`, `advanced_git.cpp`, `lfs_filter.cpp`, `syntax_engine.cpp` | STATIC |
| `nexora_tree_sitter` + 7 grammars | FetchContent pinned SHAs | STATIC |
| libgit2 1.9.7 | pinned `49e408b…` | STATIC via libgit2package |
| Mbed TLS | pinned `068ff08…` | STATIC (mbedcrypto) |

Build flags:

- C++17, exceptions, RTTI (`app/build.gradle.kts:74-78`).
- `USE_SSH OFF`, `USE_HTTPS mbedTLS`, `USE_BUNDLED_ZLIB ON`
  (`native/git/CMakeLists.txt:120-124`).
- Visibility hidden and section GC for both shared libraries
  (`CMakeLists.txt:325-342`).
- `nexoragit_native` links the static core and `log-lib` (`CMakeLists.txt:344-362`).

---

## 2. JNI surface — parity check

The audit extracted every `external fun` from Kotlin and every exported JNI symbol from C++ and
compared them mechanically.

| Kotlin declaration | Count | C++ export | Count | Result |
| --- | ---: | --- | ---: | --- |
| `NativeGitBridge` `external fun` | 50 | `Java_com_nexora_git_core_git_NativeGitBridge_*` | 50 | **Exact 1:1 match** |
| `NativeSyntaxBridge` `external fun` | 2 | `Java_com_nexora_git_core_editor_NativeSyntaxBridge_*` | 2 | **Exact 1:1 match** |
| `SecureRuntimeConfigNative.readConfig` | 1 | `RegisterNatives` in `JNI_OnLoad` | 1 | **Registered by name** |

No missing or extra symbol was found. This is a strong signal that the native boundary was built
deliberately rather than copied from a template.

Relevant files:

- `app/src/main/java/com/nexora/git/core/git/NativeGitBridge.kt`
- `app/src/main/java/com/nexora/git/core/editor/NativeSyntaxBridge.kt`
- `app/src/main/java/com/nexora/git/core/auth/SecureRuntimeConfigNative.kt`
- `native/git/src/git_jni.cpp`
- `native/git/src/syntax_jni.cpp`
- `native/config/src/secure_config_jni.cpp`

R8 keeps the binary names (`app/proguard-rules.pro:10-27`), so release builds preserve the symbol
binding. No `@Keep` annotations are needed for the native methods.

---

## 3. JNI_OnLoad and library initialization

### 3.1 `libnexoragit.so` (config)

`native/config/src/secure_config_jni.cpp:76-98`:

```cpp
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(..., JNI_VERSION_1_6) != JNI_OK || env == nullptr) return JNI_ERR;
    jclass config_class = env->FindClass("com/nexora/git/core/auth/SecureRuntimeConfigNative");
    if (config_class == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(config_class, kMethods, ...);
    env->DeleteLocalRef(config_class);
    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
```

- Registration is correct; `readConfig` signature `()[Ljava/lang/String;` matches Kotlin.
- If the class is renamed by a future refactor, `JNI_OnLoad` returns `JNI_ERR`, and
  `System.loadLibrary` throws `UnsatisfiedLinkError`. The Kotlin loader catches it
  (`SecureRuntimeConfigNative.kt:11-14`), so authentication silently becomes "not configured".
  **BUG-027** (P2, Confirmed design): a release build with a broken config JNI looks like an
  unconfigured build rather than a hard failure.

### 3.2 `libnexoragit_native.so` (Git/syntax)

There is **no `JNI_OnLoad`**; natives bind by exported symbol name. This is valid and simpler. The
trade-off is that any R8 rename or package move breaks every call. The keep rules currently protect
both classes.

`NativeGitBridge` loads the library in a `companion object init`:

```kotlin
// NativeGitBridge.kt:316-323
companion object {
    private const val ANDROID_CA_DIRECTORY = "/system/etc/security/cacerts"
    init { System.loadLibrary("nexoragit_native") }
}
```

`NativeSyntaxBridge` does the same (`NativeSyntaxBridge.kt:15-19`). Neither is guarded.

**BUG-003 (HIGH, Confirmed):** on an ABI for which the APK has no `.so` (the filters exclude 32-bit
x86), or after a partial/incorrect install, class initialization throws `UnsatisfiedLinkError`
(an `Error`, not an `Exception`). The app cannot show an error UI; the process dies. The config
library, by contrast, is guarded — the asymmetry is the defect.

---

## 4. String handling

### 4.1 `git_jni.cpp` uses modified UTF-8

`native/git/src/git_jni.cpp:11-20`:

```cpp
std::string from_jstring(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    ...
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
```

`GetStringUTFChars` returns **modified UTF-8 (CESU-8)**: characters outside the BMP (emoji,
some CJK extensions) are encoded as two 3-byte surrogate sequences, not as canonical 4-byte UTF-8.
libgit2 expects canonical UTF-8 for paths, commit messages, author names and URLs.

This finding is registered as **BUG-039 — modified UTF-8 boundary** in the master register
(P2, Confirmed). Any file path containing an emoji can be created by Java but not resolved by
libgit2, and commit messages with emoji can be stored as invalid UTF-8.

The syntax JNI is better: `syntax_jni.cpp:75-100` reads UTF-16 with `GetStringChars`, handles
surrogate pairs explicitly, and converts to canonical UTF-8. The inconsistency between the two JNI
layers is itself evidence that the Git path was not reviewed for non-BMP input.

### 4.2 `NewStringUTF` output

All output strings are produced with `NewStringUTF` from `std::string`. For valid UTF-8 this is
correct. If a pathological repository contains invalid UTF-8 in a path or ref, `NewStringUTF` may
produce a replacement string or fail; the code does not detect this. Low risk (P3), noted but not
separately registered.

---

## 5. Exception boundaries

`git_jni.cpp` wraps every operation in `string_call`/`void_call` templates
(`git_jni.cpp:100-150`):

```cpp
try { ... } catch (const nexora::git::GitError& e) {
    throw_native(env, e.code(), e.klass(), e.what());
} catch (const std::exception& e) {
    throw_native(env, -1, 0, e.what());
} catch (...) {
    throw_native(env, -1, 0, "Unknown native Git error");
}
```

This is correct: no C++ exception can cross the JNI boundary. `throw_native` locates
`GitNativeException`, falls back to `RuntimeException` if the class is missing, clears pending
exceptions before throwing, and deletes local references.

**BUG-026 (P2, Suspected):** `throw_native` calls `NewStringUTF(message.c_str())` and passes the
result to `NewObject` without checking for null. Under OOM (or invalid UTF-8 in a libgit2 message),
the constructor receives a null `String` where Kotlin expects non-null; the resulting behavior is a
second JNI exception or a null message. It does not corrupt memory, but the error path is not
robust.

**Pending-exception handling:** `FindClass`/`GetMethodID` failures are followed by
`ExceptionClear()` in `throw_native` (`git_jni.cpp:73-92`). Good.

---

## 6. Local/global reference discipline

- `from_string_array` deletes each array element (`git_jni.cpp:31-42`).
- `throw_native` deletes `type`, `text` and `exception`.
- `secure_config_jni.cpp` deletes each `jstring` and `string_class`.
- **BUG-034 (P3, Confirmed):** in `ReadConfig`, if `NewStringUTF` returns null, the function
  returns `nullptr` **without deleting the `result` local reference** and without deleting the
  array elements already set (`secure_config_jni.cpp:48-56`). One local reference per failed
  config read; the array is also leaked. This is a rare OOM path, not a normal leak.
- No global references are created; no `NewGlobalRef` was found. That avoids the classic leak.

---

## 7. Memory ownership and libgit2 handles

The core consistently frees repository, remote, index, reference, commit, tree, status-list,
conflict-iterator, signature and rebase handles on both success and error paths. Examples:

- `git_repository_free` on every early return in `remote_url`/`remotes`.
- `git_index_free` + `git_index_conflict_iterator_free` paired in conflict code
  (`advanced_git.cpp:191-253`).
- `git_signature_free` + `git_rebase_free` on all paths (`advanced_git.cpp:303-368`).

**Confirmed leak (BUG-033, P3):** `rename_remote` (`git_core.cpp:985-1012`) disposes the
`git_strarray problems` **only when `rc == 0`**:

```cpp
if (rc == 0) {
    git_strarray_dispose(&problems);
}
git_repository_free(repository);
check(rc, "Rename remote");
```

When the rename fails, the array returned by libgit2 is never disposed. Low impact, easy fix.

**No `git_libgit2_shutdown` (BUG-035, P3):** `initialize` calls `git_libgit2_init` once via
`std::call_once` (`git_core.cpp:788-822`) and never shuts libgit2 down. For a process-lifetime
engine this is acceptable, but it means:
- repeated `initialize` failures retry `git_libgit2_init` and increment the refcount each time;
- tests/embedded reuse cannot fully release native resources.

No use-after-free, double-free or obvious buffer overflow was found. The audit did **not** perform
exhaustive path-by-path native proof; see limitations.

---

## 8. Threading and callbacks

- All JNI calls run on the caller thread; Kotlin dispatches to `Dispatchers.IO`.
- No `AttachCurrentThread`/`DetachCurrentThread` usage; no native-created threads.
- Libgit2 credential callbacks are synchronous and use a stack-owned `Credentials` object whose
  lifetime covers the `git_clone`/`git_remote_push` call
  (`git_core.cpp:180-185,856-884,2132-2185`). This is correct.
- **No progress callbacks** are installed: `remote_callbacks()` sets only `credentials` and
  `payload`. No `transfer_progress`, `sideband_progress`, `push_transfer_progress`, `update_tips`
  or `certificate_check` callback. Consequences: no progress UI, no cancellation hook, no
  certificate override (BUG-005).
- **No cancellation token** is passed into native code. Cancelling the Kotlin coroutine does not
  abort an in-flight libgit2 network call.
- **No per-repository lock** (BUG-004): the native layer also has no file lock; concurrent calls
  from two threads on the same repository can race the index/refs.

---

## 9. Secure runtime config library

`secure_config_jni.cpp`:

- Values are generated at build time into `nexora_secure_config.generated.hpp` by
  `NexoraSecureConfig.cmake`.
- `ReadConfig` builds a 4-element `String[]`.
- `SecureErase` overwrites each `std::string` with `'\0'` via a `volatile char*` before clearing
  (`secure_config_jni.cpp:11-17`). This is a reasonable best-effort wipe.
- The values are **public OAuth configuration** (client ID, broker URL, callback URL), not secrets.
  The obfuscation is anti-scraping, not cryptographic protection. The real client secret stays on
  the Go broker. This is the correct design.
- `JNI_OnLoad` is minimal and safe.

Residual: when `NewStringUTF` fails, the already-created Java strings are not wiped (they are
immutable Java objects anyway); the C++ copies are wiped. Acceptable.

---

## 10. Credential lifetime in native memory

Access tokens are passed as `jstring` → `std::string` into `nativeClone`, `nativeFetch`,
`nativePush`, etc. They are stored in `GitTransportCredentials`/`Credentials` and destroyed by
normal `std::string` destruction. **They are never zeroized** (unlike config values). On a
compromised/rooted device, the token can remain in freed native heap pages.

Registered as **BUG-040 (P2, Confirmed)**: native credential buffers are not wiped. This is a
defense-in-depth gap, not a remote vulnerability. The Kotlin side stores the token encrypted at
rest, but the runtime copy must exist in memory to authenticate.

---

## 11. Native tests and CI evidence

- `native/git/tests/native_smoke.cpp` — 1,084 lines, exercises core Git operations against
  temporary repositories.
- `native/config/tests/secure_config_smoke.cpp` — config decoder test.
- `native/config/fuzz/secure_config_decode_fuzz.cpp` — LibFuzzer target.
- `native-git-ci.yml` runs:
  - normal native build + `ctest`;
  - ASan/UBSan build + `ctest` (lines 95-107);
  - a bounded fuzz smoke campaign (lines 162-202).
- `fdroid-offline-native-ci.yml` builds with no network and runs `ctest` (lines 85-87).
- For the audited SHA, the GitHub check `Pinned native build without network` concluded
  **success** (check-runs API, 2026-11-19).

This is materially stronger native QA than most Android Git projects. It does not replace
on-device JNI testing.

---

## 12. Native findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-003 | HIGH | Confirmed | Unguarded `System.loadLibrary("nexoragit_native")` in two classes |
| BUG-005 | HIGH | Confirmed | No progress/cancel callbacks in native transfers |
| BUG-010 | HIGH | Suspected | Hard-coded CA directory may be stale on Android 14+ |
| BUG-026 | MEDIUM | Suspected | `throw_native` does not handle null `NewStringUTF` result |
| BUG-027 | MEDIUM | Confirmed | Config `JNI_OnLoad` failure silently disables auth |
| BUG-033 | LOW | Confirmed | `problems` array leaked on failed remote rename |
| BUG-034 | LOW | Confirmed | `result` local ref leaked on config string failure |
| BUG-035 | LOW | Confirmed | libgit2 never shut down; init refcount on retry |
| BUG-039 | MEDIUM | Confirmed | Git JNI uses modified UTF-8 for non-BMP paths/messages |
| BUG-040 | MEDIUM | Confirmed | Native credential strings not zeroized |

---

## 13. Native verdict

The native layer is **the strongest part of Nexora Git**:

- real libgit2, real Tree-sitter, pinned dependencies;
- complete JNI symbol parity;
- correct C++ exception boundaries;
- disciplined handle cleanup in the common paths;
- native smoke tests, sanitizers and fuzzing in CI.

It is **not yet production-hardened**:

- two unguarded library loads turn ABI/packaging mistakes into process death;
- no progress/cancel/locking primitives for long network operations;
- non-BMP string handling is inconsistent and can corrupt paths/messages;
- CA trust configuration relies on a legacy path;
- credentials are not wiped from native memory.

None of these explains an immediate crash on a normal arm64 device with the correct APK. The
startup crash remains the theme defect (report 14).
