# 12 — Testing and CI Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** `app/src/test`, `app/src/androidTest`, `native/*/tests`, `baselineprofile`, 18 workflows.
> **Method:** file inventory and static reading of tests and workflow commands.
> **No test was executed locally.** CI conclusions are external evidence from the GitHub API.

---

## 1. Test inventory

| Suite | Files | Framework | Executed in CI? |
| --- | ---: | --- | --- |
| JVM unit tests | **38** | JUnit 4 | ✅ `:app:testDebugUnitTest` (Android CI + Production CI) |
| Instrumentation tests | **24** | Compose UI Test + Espresso + AndroidJUnit4 | ❌ **not run** by the standard workflows (compiled only) |
| Native smoke tests | 2 | CTest + custom asserts | ✅ `ctest` (native/fdroid workflows) |
| Native fuzz target | 1 | LibFuzzer | ✅ bounded campaign (`native-git-ci.yml`) |
| Baseline Profile / macrobenchmark | 1 | `BaselineProfileRule`, `MacrobenchmarkRule` | ⚠️ only `performance.yml` (manual/tag) |
| Marketing screenshots | 1 | Compose screenshot test | ⚠️ `marketing-screenshots.yml` (manual) |

### 1.1 Unit test distribution

| Area | Files |
| --- | ---: |
| `core/platform` | 6 |
| `core/editor` | 5 |
| `core/git` | 5 |
| `core/auth` | 3 |
| `core/storage` | 3 |
| `core/repository` | 3 |
| `core/actions`, `core/advanced`, `core/common`, `core/explore`, `core/files`, `core/issues`, `core/pulls`, `core/releases`, `core/search`, `core/social`, `core/templates` | 1 each |
| `feature/actions`, `feature/git` | 1 each |

**What is well covered:** JSON parsers, Git workflow policy, LFS pointer parsing, remote security
policy, auth endpoint policy, PKCE, pending authorization validation, path policy, search engine,
Git ignore, project risk detection, rate-limit manager, URL resolver, permission resolver,
error mapper, formatters, LSP JSON-RPC codec.

**What is not covered at all:**

- `NexoraGitApplication`
- `MainActivity`
- Hilt graph/startup
- Theme resolution (`values` vs `values-night`)
- `NexoraGitApp` navigation gating
- Room migrations (no `MigrationTestHelper`)
- DataStore corruption/recovery
- `SecureAuthStorage` and `KeystoreCipher` (Keystore is not available on the JVM; would need a fake)
- `AuthSessionRepository` refresh/multi-account flows
- `Libgit2GitEngine` (all Git operations are native; no fake engine tests)
- `EditorFileStore` save/atomicity
- `WorkspaceRegistry`

**Consequence:** the **startup crash (BUG-001) is not detectable by this test suite**, because no test
launches an Activity with a themed `AppCompat` context and no test inspects resource qualifiers.
This is the single most important testing gap in the project.

---

## 2. Instrumentation tests

24 files under `app/src/androidTest/java/com/nexora/git/`:

- 20 `*ContentTest` classes covering the main screens (Compose content assertions with a fake state).
- `LoginScreenTest`, `OnboardingScreenTest`, `SettingsContentTest`,
  `SettingsAccessibilityTest`, `MobileEditorContentTest`, `GitWorkspaceContentTest`.
- `MarketingScreenshotTest`.
- `FoundationScreenTest` (for the unused `FoundationScreen`).

Characteristics:

- They use `createComposeRule()`/activity-scoped Compose rules with **state-only** screens.
- They do **not** start `MainActivity`.
- They do **not** use Hilt (`@HiltAndroidTest` not found in the list).
- They do **not** exercise navigation, Room, DataStore or native code.

**Critical CI fact:** the standard workflows compile instrumentation tests
(`:app:assembleDebugAndroidTest`) but **never execute them**. There is no emulator/`managedDevices`
step in `android-ci.yml`, `production-ci.yml` or `foundation-ci.yml`. Only
`performance.yml`/`marketing-screenshots.yml` boot an emulator, and those are manual or tag-only.

Therefore: **24 instrumentation tests are compiled, never run, in the normal path.** They cannot
protect against regressions.

---

## 3. Native tests

`native/git/tests/native_smoke.cpp` is 1,084 lines and exercises real libgit2 operations against
temporary repositories: init, clone from a local path, add/commit, branches, merge, conflict, stash,
tags, diff, history. `native/config/tests/secure_config_smoke.cpp` decodes the generated config.

CI:

- `native-git-ci.yml` — normal build + `ctest`; **ASan/UBSan** build + `ctest`; a bounded fuzz smoke
  campaign.
- `fdroid-offline-native-ci.yml` — builds with **no network interfaces** (`unshare --net`) and runs
  `ctest`.
- `codeql.yml` — builds C/C++ for analysis.

This is **materially better native QA than most Android projects**, and it is the reason the native
verdict in report 05 is positive.

Gap: native tests run on the **host** (x86_64 Linux), not on Android. The JNI layer itself
(`git_jni.cpp`, `syntax_jni.cpp`) is compiled for Android but never exercised on a device or
emulator. JNI reference handling, modified UTF-8 (BUG-039) and `System.loadLibrary` behavior are
therefore untested at runtime.

---

## 4. CI workflow inventory

18 workflows:

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `android-ci.yml` | push/PR with `app/**`, `native/**`, Gradle paths | Android SDK 37, JDK 17, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`, `:app:lintDebug` |
| `production-ci.yml` | push/PR app/native/gradle | `scripts/ci/validate-production.sh`, `:app:assembleRelease`, `bundleRelease`, `testDebugUnitTest`, `lintRelease` |
| `foundation-ci.yml` | push/PR | repository foundation validation (docs/scripts) |
| `native-git-ci.yml` | push/PR native paths | native build, `ctest`, ASan/UBSan, fuzz |
| `fdroid-offline-native-ci.yml` | push/PR | pinned sources, **network-isolated** native build + `ctest` |
| `fdroid-offline-android-ci.yml` | PR | network-isolated unsigned APK/AAB rebuild (experimental) |
| `ci-ephemeral-signing.yml` | PR | ephemeral signed APK with `com.nexora.git.ci` |
| `codeql.yml` | push + schedule | Kotlin/Java, C/C++, Go, TS/JS analyses |
| `dependency-review.yml` | PR | dependency diff review |
| `auth-broker-ci.yml` | push/PR | Go tests + build |
| `web-ci.yml` | push/PR | Next.js lint/build |
| `vps-installer-ci.yml` | push/PR | VPS installer tests |
| `store-distribution-ci.yml` | push/PR | store metadata validation |
| `release.yml` | tag push | signed release |
| `performance.yml` | manual + `v*-rc.*` tags | macrobenchmark + baseline profile |
| `marketing-screenshots.yml` | manual | screenshot generation |
| `render-store-artwork.yml` | manual | store artwork |
| `community-readiness.yml` | push | community docs validation |

### 4.1 Strengths

- Actions pinned by commit SHA (supply-chain hygiene).
- Concurrency groups with `cancel-in-progress`.
- Minimal `permissions: contents: read` on most workflows.
- Network-isolated native build proves the pinned sources are sufficient.
- CodeQL across four languages.
- Dependency Review on PRs.
- Explicit release gate script (`validate-production.sh`).

### 4.2 Findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-023 | P3 | Confirmed | Path filters mean the audited SHA was **not** debug-built/linted by `android-ci.yml` |
| — | P1 (process) | Confirmed | Instrumentation tests are compiled, never executed |
| — | P1 (process) | Confirmed | No startup/theme test exists |
| — | P2 | Confirmed | No Room migration tests (`MigrationTestHelper`) |
| — | P2 | Confirmed | No device JNI smoke test |
| — | P2 | Confirmed | No coverage reporting/enforcement |
| — | P2 | Confirmed | No dependency vulnerability scan beyond Dependabot (no OSV/Trivy/Grype) |
| — | P3 | Confirmed | `performance.yml` and screenshots are manual/tag-only |
| — | P3 | Confirmed | `native-git-ci.yml`, `release.yml`, `performance.yml` etc. are extra workflows not listed in the headline count; only 18 files exist but several are duplicates of the same gate |

### 4.3 CI evidence for the audited SHA

From the check-runs API on 2026-11-19:

| Check | Conclusion |
| --- | --- |
| `Release build and production gate` | success |
| `Pinned native build without network` | success |
| `Validate repository foundation` | success |
| `Validate S.17 development metadata` | success |
| `Analyze Kotlin/Java` (latest) | success |
| `Analyze C/C++` (latest) | success |
| `Analyze Go Auth Broker` (latest) | success |
| `Analyze TypeScript/JavaScript` (latest) | success |
| `Build, lint and unit test` | **absent for this SHA** (path filters) |

Note: an **earlier** CodeQL Kotlin/Java run for the same commit **failed**; the latest run passed.
This is recorded for honesty: CI history for the SHA is not uniformly green. The production gate and
native gate did pass.

Also note: the CI evidence is **external**. This audit did not run any of these workflows locally and
cannot attest to the runner environment beyond the reported conclusions.

---

## 5. Test quality observations

**Positive:**

- Unit tests are small, deterministic and focused (e.g. `AuthEndpointPolicyTest` covers exact HTTPS,
  explicit ports, different origins, bad callback shapes).
- `GitLfsTransportTest` and `GitLfsManagerTest` cover pointer parsing and transport headers.
- `WorkspacePathPolicyTest` covers traversal and `.git` exclusion.
- Native tests use real repositories, not mocks.
- ASan/UBSan in CI.

**Negative:**

- No fake `GitEngine`. Every Git test is native; the Kotlin `Libgit2GitEngine` mapping (JSON →
  models, credential resolution, force-with-lease validation) is only covered indirectly by parser
  tests.
- No `MainDispatcherRule`/`TestDispatcher` found in a quick scan; coroutine timing is not controlled.
- Instrumentation tests assert static content, not behavior (no clicks through a flow).
- No screenshot baselines committed for regression.
- No coverage gate.

---

## 6. Testing and CI verdict

**CI breadth is outstanding for a six-day-old project.** 18 workflows, pinned actions, CodeQL across
four languages, sanitizers, fuzzing, offline native builds and an ephemeral signing path is more than
most production Android apps have.

**Test efficacy is misaligned with risk:**

1. The unit suite tests parsers and helpers, not startup, theme, DI, Room or the Git engine facade.
2. The 24 instrumentation tests never execute in the standard CI path.
3. No test can catch BUG-001, BUG-002, BUG-006 or BUG-011.
4. JNI is tested on the host, not on Android.

For HMX Git, the first three tests to add are:

1. an instrumentation test that launches `MainActivity` in **night mode**
   (`AppCompatDelegate.setDefaultMode(MODE_NIGHT_YES)` before launch) and asserts no crash;
2. a `MigrationTestHelper` test for 1→4;
3. a JVM test for `AuthViewModel` startup with a failing Room/DataStore flow, asserting the app
   surfaces an error instead of crashing.

Until then, green CI does not mean "the app opens".
