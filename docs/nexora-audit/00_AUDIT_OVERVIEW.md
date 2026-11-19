# 00 — Audit Overview

> **Repository:** https://github.com/Gh0stDeveloper/Nexora-Git
> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833`
> **Branch:** `main` · **Commit date:** 2026-10-09 · **Audit date:** 2026-11-19
> **Mode:** read-only static audit · **No build, no install, no launch, no Logcat**

---

## 1. Objective

Nexora Git is proposed as the upstream foundation for **HMX Git** (`com.hmx.git`). Before any
fork or rebranding work, this audit had to establish:

1. whether the reported **"crashes immediately after opening"** is a reproducible,
   source-explainable defect;
2. which other defects could corrupt data, break Git operations or compromise credentials;
3. whether the codebase is structurally capable of carrying the HMX Git scope;
4. what must be fixed **before** any implementation task begins.

This audit does not fix anything. It records evidence.

---

## 2. Scope and method

| Item | Value |
| --- | --- |
| Files in checkout | 519 (excluding `.git`) |
| Kotlin files | 272 (208 main, 38 unit test, 24 instrumentation, 2 baseline profile) |
| Java files | **0** |
| C++ files | 10 |
| C/C++ headers | 2 |
| Kotlin LOC (all) | 53,127 |
| Kotlin LOC (main) | 46,729 |
| Native LOC (`.cpp` + `.h`) | 8,713 |
| Gradle Kotlin DSL files | 5 |
| XML files | 11 |
| CI workflows | 18 |
| Markdown docs (`.md`) | 81 total; 77 under `docs/` |
| Commits at HEAD | 409, single author |
| Tags / releases | 0 / 0 |
| License | Apache-2.0 |

Method:

1. **Identity and inventory** — clone verification, tree walk, language counts, module map.
2. **Static tracing** — manifest → `Application` → `MainActivity` → Compose root → Hilt graph →
   Room/DataStore → native bridge → editor → Git engine.
3. **Pattern search** — `loadLibrary`, `require`/`check`/`!!`, coroutine scopes, `Mutex`,
   `runCatching`, `catch (Exception)`, `TODO`, dispatchers, JNI reference calls, libgit2 frees.
4. **Cross-checks** — manifest vs code, Gradle ABI vs native targets, ProGuard keeps vs JNI symbol
   names, exported schema vs committed schema, documented capability vs actual implementation.
5. **External CI evidence** — GitHub check-runs API for the audited SHA. This is not a local build.

---

## 3. What the application actually is

Nexora Git is a **single-module** Android application:

- Kotlin + Jetpack Compose + Material 3, Hilt, Room, DataStore, OkHttp/Retrofit, WorkManager.
- A real **libgit2 1.9.7** engine compiled with C++17 and Mbed TLS through the NDK
  (`native/git/CMakeLists.txt`, `USE_SSH OFF`).
- A **Tree-sitter** syntax engine with pinned Kotlin, Java, JSON, Python, JavaScript, TypeScript
  and TSX grammars.
- A GitHub platform layer (REST/GraphQL clients, issues, PRs, Actions, releases, social).
- A GitHub App OAuth flow through a **self-hosted Go auth broker**.
- A Compose code editor with a 512 KiB safety limit, atomic save and optimistic-concurrency check.
- A Go auth broker, a Next.js website, F-Droid metadata and 18 CI workflows in the same repository.

It is **not** a WebView wrapper and it does not shell out to Git.

---

## 4. Startup crash: summary verdict

| Question | Answer |
| --- | --- |
| Was the crash reproduced by this audit? | **No** — no device, no emulator, no APK build permitted. |
| Is there a source-level defect that explains an immediate crash? | **Yes.** Night theme is not an AppCompat theme. |
| Is that defect deterministic? | **Yes, when the device is in night/dark mode.** |
| Does it affect all devices? | No. Light-mode devices take the valid AppCompat theme. |
| Does it depend on authentication or network? | **No.** It fails before login. |
| Is it a native crash? | No. It is a Java `IllegalStateException` inside AppCompat. |
| Is it a Compose crash? | No. It fails during `setContentView`, inside `AppCompatActivity`. |
| Confidence | **High (static)**, runtime **unconfirmed**. |

Exact mechanism:

- `app/src/main/java/com/nexora/git/MainActivity.kt:26` — `class MainActivity : AppCompatActivity()`
- `app/src/main/java/com/nexora/git/MainActivity.kt:40` — `setContent { ... }`
- `app/src/main/res/values-night/styles.xml:3` —
  `parent="android:style/Theme.Material.NoActionBar"`
- `app/src/main/res/values/styles.xml:3` —
  `parent="Theme.AppCompat.DayNight.NoActionBar"`

When the `night` resource qualifier is active, the framework theme replaces the AppCompat theme.
AppCompat's `createSubDecor()` rejects it:

```
java.lang.IllegalStateException: You need to use a Theme.AppCompat theme
(or descendant) with this activity.
    at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(...)
    at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(...)
    at androidx.appcompat.app.AppCompatActivity.setContentView(...)
    at androidx.activity.compose.ActivityComposeKt.setContent(...)
    at com.nexora.git.MainActivity.onCreate(MainActivity.kt:40)
```

This stack is **reconstructed from source and the AppCompat implementation**, not captured from a
device. The exact AppCompat guard is visible in `androidx-main`:
`AppCompatDelegateImpl.java:858` checks
`a.hasValue(R.styleable.AppCompatTheme_windowActionBar)` and throws at line 861 when it is absent.
`windowActionBar` is an **AppCompat library attribute** (`appcompat/.../values/attrs.xml:43`), so a
framework `android:style/Theme.Material.*` parent does not satisfy it.

Report 14 ranks nine other possible causes and explains why each is weaker.

---

## 5. Finding counts (as recorded in report 13)

| Status | Count |
| --- | --- |
| **Confirmed source defects** | **33** |
| **Suspected / runtime-dependent** | **7** |
| **Total findings** | **40** |

Severity distribution:

| Severity | Count |
| --- | --- |
| CRITICAL (P0) | 2 |
| HIGH (P1) | 8 |
| MEDIUM (P2) | 17 |
| LOW (P3) | 13 |

> Counts are deliberately conservative. Cosmetic observations and "could be nicer" items are not
> registered as bugs. The register separates confirmed code defects from runtime hypotheses so the
> two are never conflated.

---

## 6. Strongest positive findings

The codebase is not a mock-up. The audit verified:

- **Real native engine.** 50 `external fun` declarations in `NativeGitBridge.kt` match 50 JNI
  exports in `git_jni.cpp` one-for-one; `NativeSyntaxBridge` 2/2. No missing symbol was found.
- **Pinned dependencies.** libgit2 `49e408b…` (v1.9.7), Mbed TLS `068ff08…`, Tree-sitter and all
  grammars pinned by full commit SHA (`native/git/CMakeLists.txt:12-20`).
- **Credentials isolated to GitHub HTTPS.** Native code only supplies credentials to
  `https://github.com` (`git_core.cpp` `is_github_https_url`); Kotlin mirrors this in
  `GitRemoteSecurityPolicy.kt:10-21`.
- **PKCE S256 + state validation + 10-minute authorization window.**
- **Tokens encrypted with Android Keystore AES-GCM**, not stored in plaintext.
- **Editor safety.** 512 KiB cap, truncated-file rejection, atomic `AtomicFile` write, and
  external-change detection before save (`EditorFileStore.kt:26-110`).
- **Path traversal protection.** Canonical-root containment and `.git` exclusion
  (`WorkspacePathPolicy.kt:10-51`).
- **CI depth.** 18 workflows, including native smoke tests, ASan/UBSan, fuzzing, pinned offline
  native builds, unit tests, lint and release gates.

These strengths are why the foundation verdict is conditional rather than negative.

---

## 7. Strongest negative findings

| ID | Severity | Finding |
| --- | --- | --- |
| BUG-001 | CRITICAL | Night theme is framework Material, not AppCompat → immediate crash in dark mode |
| BUG-002 | CRITICAL | In-app theme setting never calls `AppCompatDelegate.setDefaultNightMode`; users cannot escape the night theme, and LIGHT/DARK settings disagree with the platform theme |
| BUG-003 | HIGH | Unguarded `System.loadLibrary("nexoragit_native")` in two classes → `UnsatisfiedLinkError` on unsupported ABI/partial install |
| BUG-004 | HIGH | No per-repository operation lock; WorkManager serializes some network work, but foreground Git operations are not serialized |
| BUG-005 | HIGH | No transfer progress and no native cancellation for clone/fetch/push |
| BUG-006 | HIGH | Room v4 has no destructive fallback and no committed schema; downgrade/schema corruption is unrecoverable |
| BUG-007 | HIGH | A default build with no broker configuration cannot authenticate at all |
| BUG-008 | HIGH | No SSH transport (`USE_SSH OFF`); GitHub HTTPS is the only supported transport |
| BUG-009 | HIGH | Release signing is optional; a release build can be produced unsigned |
| BUG-010 | HIGH | Hard-coded `/system/etc/security/cacerts` may be stale on newer Android CA stores |

Full details, reproduction conditions and fixes are in report 13.

---

## 8. Build and test result

**This audit did not run Gradle, CMake, tests or the application.** That is a hard constraint from
the user, not an omission.

External evidence for `f4befb6` (GitHub check-runs API, 2026-11-19):

| Check | Conclusion |
| --- | --- |
| `Release build and production gate` | **success** |
| `Pinned native build without network` | **success** |
| `Validate repository foundation` | **success** |
| `Validate S.17 development metadata` | **success** |
| `Analyze Kotlin/Java`, `Analyze C/C++`, `Analyze Go Auth Broker`, `Analyze TypeScript/JavaScript` | **success** |
| `Build, lint and unit test` (Android CI) | **not run on this commit** (path-filtered; no `app/**` or `native/**` change) |

The production gate runs `:app:assembleRelease`, `:app:bundleRelease`, `:app:testDebugUnitTest`
and `:app:lintRelease`. The offline native job runs `cmake`, `cmake --build` and `ctest` with
`NEXORA_BUILD_NATIVE_TESTS=ON` (`.github/workflows/fdroid-offline-native-ci.yml:85-87`).

Therefore: **the source compiles in CI; unit tests pass in CI; native smoke tests pass in CI.**
This does **not** prove runtime correctness, and it does not reproduce the startup crash.

---

## 9. Foundation verdict (preview)

Nexora Git is the **best available open-source base for HMX Git's stated scope** among the
candidates examined previously, but it is **not production-ready**:

- The P0 theme defect must be fixed and verified in dark mode before any fork is called usable.
- The P1 engine gaps (locking, progress/cancel, SSH) must be planned before claiming a "full Git
  client".
- Authentication is inert without a deployed broker; HMX Git needs its own broker or a different
  OAuth design.
- There are 0 releases and 0 device-QA evidence; the upstream release gate is explicitly
  **NO-GO** (issue #57).

Recommendation: **fork conditionally**, after a Phase 0 that fixes and verifies BUG-001/002 and
adds a startup smoke test. Full reasoning in `19_FINAL_TECHNICAL_ASSESSMENT.md`.

---

## 10. Report integrity statement

- Every report in this directory carries the same commit SHA.
- No report claims a local build, local test, installed APK or captured Logcat.
- The only runtime conclusion presented as "confirmed" is the **existence of the theme defect**;
  the crash itself is labelled **unconfirmed until the user supplies Logcat**.
- Where evidence is absent (for example, no device), the report says so instead of guessing.
