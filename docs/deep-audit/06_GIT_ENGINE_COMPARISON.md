# 06 — Git Engine Comparison

**Question:** Which Git implementation should HMX Git use on Android?

**Engines in scope:**

| Engine | Used by | Language | Delivery |
| --- | --- | --- | --- |
| **JGit 7.3.0** | Gitling (and MGit/SGit lineage) | Java | Pure JVM |
| **libgit2 1.9.7 + hand-written JNI** | Nexora Git | C + C++17 bridge | NDK-built `.so` |
| **libgit2 1.9.7 + git24j (JNI)** | PuppyGit | C + Java/C++ generator | NDK-built `.so` |

Also considered: native `git` binary (needs Termux-style packaging — rejected), Rust `gitoxide`
(no mature Android JNI binding — rejected), GitHub API-only (cannot do local Git — rejected).

---

## 1. Summary verdict

| Criterion | JGit | libgit2 + custom JNI (Nexora) | libgit2 + git24j |
| --- | --- | --- | --- |
| Time to first working op | **Fast** (pure Gradle dep) | Slow (NDK, CMake, FETCHCONTENT) | Medium (prebuilt bindings, still NDK) |
| Android runtime compatibility | **Poor below API 33 without patching** | **Good** (minSdk 26 fine) | Good |
| Native build complexity | None | **High** | High |
| APK size impact | ~10–15 MB of dex (unshrunk) | ~2–4 MB per ABI `.so` | similar |
| Memory per operation | **Higher** (JVM heap, object graph) | **Lower** (C heap, can be tuned) | Lower |
| Large-repo performance | Medium | **High** | High |
| SSH | Via JSch (third-party, works) | **Requires libssh2 (not enabled)** | Via libssh2 |
| Thread safety | Good (JGit designed for it) | **Requires app-level locking** | Requires app-level locking |
| Progress callbacks | **Yes** (`ProgressMonitor`) | **No (not implemented)** | Available in git24j |
| Cancellation | Yes (transport + monitor) | **No (not implemented)** | Available |
| Testability on JVM | **Excellent** (pure JVM unit tests) | Poor (needs device/emulator) | Poor |
| Maintenance burden | Low | **High** | High |

> **Recommendation:** For **HMX Git**, **libgit2 is the correct engine**, but the *binding* matters:
> Nexora's hand-written JNI is clean and auditable but missing SSH/progress/cancel; git24j is
> feature-complete but produces a 385 KB god-object helper and opaque generated code. Nexora's
> binding is the better **starting point** precisely because it is only 7,058 LOC and fully readable.
> JGit is the correct engine **only if** the team will not own an NDK toolchain — and even then it
> requires the bytecode-patching workaround Gitling already ships.

---

## 2. JGit on Android

### 2.1 The Java-version problem (decisive)

| JGit line | Java requirement | Android implication |
| --- | --- | --- |
| 5.x | Java 8 | Works with desugaring |
| 6.x | Java 11 | Needs desugaring + ShutdownHook workarounds |
| **7.x (Gitling)** | **Java 17** | Uses `InputStream.transferTo()` (API 29), `readNBytes`/`readAllBytes` (API 33) |

Gitling's `app/build.gradle.kts` documents the consequence in detail and ships a **build-time ASM
rewrite** of JGit bytecode:

> "JGit 6.3+ uses `InputStream.transferTo()` (API 29) and JGit 6.7+ uses `readNBytes(int)`,
> `readNBytes(byte[], int, int)`, and `readAllBytes()` (all API 33). None of these are available on
> Android 12 (API 31) or older, and desugar_jdk_libs 2.x does not backport any of them."

Verification: `app/build.gradle.kts:187-190` sets `sourceCompatibility/targetCompatibility = 17` and
`isCoreLibraryDesugaringEnabled = true`; the ASM plugin additionally rewrites JGit's
`INVOKEVIRTUAL` calls to `StreamCompat` shims.

> **Finding E-JG-01** — JGit 7 on Android requires an **in-build bytecode transformer** that is
> coupled to JGit internals. It is proven to work on Gitling (Play + F-Droid, 68 releases) but it is
> fragile across JGit upgrades and hard to reason about in a security review. **VERIFIED.**
> Risk: **HIGH** engineering risk for a new HMX Git team. Confidence: High.

### 2.2 Strengths of JGit

- **Pure JVM**: no NDK, no ABI matrix, no `.so` per architecture → simplest CI.
- **Battle-tested** (`eclipse-jgit/jgit`, Eclipse Distribution License, active).
- **Designed for concurrency**: `Repository` handles, `RevWalk`, thread-safe object database.
- **Good API ergonomics** for tooling (status, diff, revwalk, submodules).
- **Excellent testability**: unit tests run on the JVM without a device.
- **Progress + cancellation** are first-class (`ProgressMonitor`, `TransportCommand`).

### 2.3 Weaknesses of JGit on Android

| Weakness | Consequence |
| --- | --- |
| JVM object overhead | Higher per-op memory; `largeHeap=true` in Gitling (`AndroidManifest.xml`) |
| No native pack acceleration | Slower clone/fetch of huge repos than libgit2 |
| Java-17 bytecode | Requires desugaring + ASM patch (see above) |
| SSH via JSch | Works but is a separate dependency with its own crypto surface |
| DEX bloat | `isMinifyEnabled=false` → 30 MB `classes.dex`, 7 dex total in Gitling |
| GC pressure on 3–4 GB devices | JGit allocates many short-lived objects |

> **Finding E-JG-02** — Gitling sets `largeHeap=true`, which is a direct symptom of JVM-heap
> pressure from a pure-Java Git engine on low-RAM devices. **VERIFIED** (manifest). Risk:
> **MEDIUM** for 3–4 GB devices. Confidence: High.

---

## 3. libgit2 on Android

### 3.1 Build requirements (Nexora, verified)

`native/git/CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22.1)
set(NEXORA_LIBGIT2_REF "49e408b...")      # v1.9.7
set(NEXORA_MBEDTLS_REF "068ff08...")
set(USE_SSH OFF CACHE STRING "" FORCE)     # ← no SSH
set(USE_HTTPS "mbedTLS" CACHE STRING "" FORCE)
set(USE_BUNDLED_ZLIB ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF ...)
```

| Requirement | Nexora value |
| --- | --- |
| NDK | 27.2.12479018 |
| CMake | ≥ 3.22.1 |
| Toolchain | Clang (NDK) |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 |
| TLS | Mbed TLS (static) |
| SSH | **disabled** |

> **Finding E-L2-01** — libgit2 builds cleanly through the NDK with Mbed TLS and bundled zlib;
> Nexora proves the toolchain works (`native-git-ci.yml` "Build and test libgit2 core" success on
> `3d0f933`). **VERIFIED.** Confidence: High.

> **Finding E-L2-02** — `USE_SSH OFF` is a **configuration choice**, not a libgit2 limitation.
> libgit2 supports libssh2 (`USE_SSH=libssh2`). Enabling it is the single highest-value engine change
> for HMX Git. **VERIFIED.** Confidence: High.

### 3.2 Memory and performance characteristics

libgit2 operates on a compact C heap, memory-maps pack files, and does not create a JVM object per
object in a history. On-device measurements were **not possible** in this audit (no device, no
build). The following are **estimates**, explicitly labelled:

| Metric | JGit (estimate) | libgit2 (estimate) | Basis |
| --- | --- | --- | --- |
| Clone 500 MB repo, peak RSS | 300–600 MB | 150–300 MB | object graph vs mmap; **estimate** |
| `log` on 100k commits | Higher GC churn | mmap walk | **estimate** |
| Index/status on 50k files | 200–400 ms | 100–250 ms | **estimate** |
| Resident code (.so vs dex) | dex loaded lazily | one `.so` per ABI | measured APK composition |

> **Finding E-L2-03** — No on-device benchmark exists for any candidate in this audit. All
> memory/performance figures in this report are **estimates** and are marked as such. Actual
> HMX Git planning must include a device benchmark phase. **UNKNOWN** (measured data). Confidence:
> Low for numbers, High for direction.

### 3.3 libgit2 safety properties

| Property | State |
| --- | --- |
| Thread safety | **Not thread-safe for concurrent writes to one repository** — caller must lock |
| Error model | int return codes + `git_error_last()` |
| JNI exception mapping (Nexora) | `throw_native` → typed `GitNativeException` — correct |
| Cancellation | `git_remote_stop()` exists; **Nexora does not call it** |
| Progress | `git_transfer_progress_cb` exists; **Nexora does not install it** |
| Sanitizers | Nexora runs **ASan + UBSan** in CI (`3d0f933` success) |
| Fuzzing | Nexora fuzzes the secure native config decoder (`3d0f933` success) |
| Native pinning | Immutable commit SHAs (verified) |

> **Finding E-L2-04** — libgit2 provides the hooks for progress and cancellation; the gap is in
> Nexora's *binding*, not the library. **VERIFIED.** Confidence: High.

---

## 4. git24j (PuppyGit)

| Property | Value |
| --- | --- |
| Origin | `git24j/git24j` + `Frank997/git24j` fork, locally edited |
| Artifact | `lib_src/git24j-1.0.4.20241114-sources.jar` |
| Native | `libgit24j.so` + `libgit2.so` + `libssh2.so` + OpenSSL |
| API style | Object-oriented wrapper (`Git.Repository`, `Git.CloneOptions`, …) |

### 4.1 Strengths

- **Feature-complete binding**: LFS, reflog, patch, squash, submodules, SSH host-key callbacks.
- **SSH via libssh2** already wired.
- Less hand-written JNI than Nexora (generated from headers).

### 4.2 Weaknesses

- PuppyGit concentrates ~all engine calls in `Libgit2Helper.kt` — **385,601 bytes**. The binding
  offers no architectural pressure toward a clean `GitEngine` interface.
- Generated bindings are harder to audit for JNI reference leaks than Nexora's 50 hand-written
  functions.
- libgit2 version provenance is ambiguous (README 1.9.4 vs script 1.9.7).
- Few tests.

> **Finding E-G24-01** — git24j proves libgit2 + libssh2 on Android is viable, which de-risks
> adding SSH to Nexora. **VERIFIED** (source + README). Confidence: High.

---

## 5. Operation coverage comparison

| Operation | JGit (Gitling) | libgit2 (Nexora binding) | libgit2 (git24j/PuppyGit) |
| --- | --- | --- | --- |
| Init | Yes | Yes | Yes |
| Clone | Yes (+progress, shallow) | Yes (no progress) | Yes (+progress, shallow) |
| Fetch | Yes | Yes | Yes |
| Pull | Yes | Yes | Yes |
| Push | Yes (non-FF bug) | Yes (+force-with-lease) | Yes |
| Commit | Yes | Yes | Yes |
| Stage/unstage | Yes | Yes | Yes |
| Status | Yes | Yes | Yes |
| Branch create/delete | Yes | Yes | Yes |
| Checkout | Yes | Yes | Yes |
| Merge | Yes | Yes | Yes |
| Rebase | Yes (no continue/abort) | **Yes (continue/abort, `advance_rebase`)** | Yes |
| Cherry-pick | Yes | **Yes (continue/abort)** | Yes |
| Revert | **No dedicated task** | **Yes (continue/abort)** | Yes |
| Reset | Yes | Yes | Yes |
| Stash | Console only | **Yes (list/save/apply/drop)** | Yes |
| Tags | Yes | **Yes (list/create/delete)** | Yes |
| Remotes | Yes | Yes | Yes |
| History/graph | Yes (lane graph) | Yes (revwalk) | Yes |
| Diff | Yes | Yes | Yes |
| Blame | Yes | Yes | Yes |
| Conflict detect | Yes | Yes | Yes |
| Conflict resolve | File ours/theirs | **Ours/theirs only** | Yes (hunks) |
| **Submodules** | Yes | **Yes (init/sync/update)** | Yes |
| **Git LFS** | **No** | **Custom LFS transport + filter** | Yes |
| **SSH** | **Yes (JSch)** | **No** | Yes |
| **Reflog/patch/squash** | No | No | Yes |

> **Finding E-OP-01** — Coverage is complementary, not hierarchical:
> - Nexora's binding has the **widest advanced-Git surface** (rebase/cherry-pick/revert
>   continue-abort, stash, tags, submodules) but **no SSH and only ours/theirs conflicts**.
> - Gitling's JGit has **SSH** and console flexibility but **no revert, no rebase recovery**.
> - PuppyGit has **the widest surface including LFS/reflog/patch/squash and SSH**, but **no GitHub
>   layer and no clean architecture**.
> **VERIFIED.** Confidence: High.

---

## 6. Which engine is more suitable for lower-memory Android devices?

**libgit2**, for these reasons:

1. **No JVM object graph per Git object** — history walks operate on mmap'd pack data.
2. **No `largeHeap` requirement** — Gitling needs it; libgit2 apps (Nexora, PuppyGit) do not set it.
3. **Smaller resident working set per operation** — C heap is bounded and can be released.
4. **Per-ABI `.so`** — only one architecture is installed per device (with ABI splits), unlike
   multi-dex JVM code that is all present.
5. **No desugaring/ASM complexity** — fewer runtime surprises on old ART.

**Caveat:** libgit2 must be paired with:
- an app-level **repository lock** (single writer),
- **progress callbacks** and `git_remote_stop` cancellation,
- **libssh2** if SSH is required,
- careful **JNI reference management** (Nexora's 50 functions are a good template).

> **Finding E-EN-01** — For 3–4 GB RAM devices, libgit2 is the better fit; JGit is acceptable only
> with `largeHeap` and R8 shrinking, and even then memory pressure is higher.
> **PARTIALLY VERIFIED** (architectural reasoning + manifest evidence; no device measurement).
> Confidence: Medium-High.

---

## 7. Engine recommendation for HMX Git

| Option | Verdict |
| --- | --- |
| **Nexora's libgit2 + custom JNI** | **Adopt as the base.** Add libssh2, progress, cancel, lock. |
| Nexora libgit2 → swap to git24j | Viable if SSH/LFS/reflog are needed immediately; costs architecture. |
| JGit (new integration) | Only if NDK ownership is refused; must re-solve the Java-17 problem. |
| JGit (copy Gitling) | Not recommended: GPL-3.0 + no GitHub layer + 5 tests. |
| Native `git` binary | Rejected: packaging, sandboxing, Play policy. |
| gitoxide (Rust) | Rejected: Android JNI maturity. |

**Concrete Phase-0 engine gate for HMX Git:**

1. Build Nexora's `native/git` for all three ABIs with NDK 27.2.12479018.
2. Add `USE_SSH=libssh2` and verify SSH clone/push against a test host.
3. Install `git_transfer_progress_cb` + `git_remote_stop` and prove cancellation mid-clone.
4. Add a per-repository mutex in `Libgit2GitEngine` and a concurrency stress test.
5. Benchmark clone/log/status on a 4 GB device before committing to the engine.
