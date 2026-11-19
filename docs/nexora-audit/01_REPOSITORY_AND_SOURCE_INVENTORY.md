# 01 — Repository and Source Inventory

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Branch:** `main` · **Audit date:** 2026-11-19
> **Method:** `git status`, `git log`, `git submodule status`, `git lfs ls-files`, `find`, `wc -l`, tree inspection. No build.

---

## 1. Repository identity

| Property | Value |
| --- | --- |
| URL | https://github.com/Gh0stDeveloper/Nexora-Git |
| Default branch | `main` |
| Audited SHA | `f4befb6c1954d956f0a67b14f4ab1642aa185833` |
| Commit subject | `ci(s17.3): qualify isolated Android Gradle rebuilds (#56)` |
| Author | Ghost Developer |
| Author date | 2026-10-09 03:18:46 -0600 |
| Commit date | 2026-10-09 03:18:46 -0600 |
| Repository created | 2026-10-04 |
| Commits at HEAD | 409 |
| Contributors at HEAD | 1 (`Ghost Developer`) |
| Remote branches | 51 (main + Dependabot + archive branches) |
| Tags | 0 |
| GitHub Releases | 0 |
| Stars / forks | 4 / 0 (API, 2026-11-19) |
| Open issues | 18 (API; includes PRs) |
| License | Apache-2.0 |
| Checkout size | 8.1 MB including `.git` |
| Git LFS | Not used (`.gitattributes` has no LFS filters; `git lfs ls-files` empty) |
| Submodules | None (`git submodule status` empty; no `.gitmodules`) |
| Working tree | Clean, `main...origin/main` |

The repository is **five days old at the audited commit** (created 2026-10-04, HEAD 2026-10-09).
The GitHub "pushed_at" is 2026-11-16 because of Dependabot activity, but `main` has not advanced
past `f4befb6`. This matters for maturity and abandonment-risk assessment: there is no long
production history behind the code.

---

## 2. Complete file inventory

Excluding `.git`, the checkout contains **519 files**.

| Type | Count | Notes |
| --- | --- | --- |
| Kotlin `.kt` | **272** | 208 main + 38 unit tests + 24 instrumentation + 2 baseline profile |
| Java `.java` | **0** | No Java source at all |
| C++ `.cpp` | **10** | 6 Git/pipeline sources, 1 secure-config source, 2 native tests, 1 fuzz target |
| C/C++ `.h` | **2** | `git_core.h`, `syntax_engine.h` |
| XML `.xml` | **11** | 10 resources + `AndroidManifest.xml` |
| Kotlin DSL `.kts` | **5** | root, settings, app, buildSrc, baselineprofile |
| Version catalog `.toml` | **1** | `gradle/libs.versions.toml` |
| CMake `.cmake` | **2** | secure-config generator, FindmbedTLS |
| CMake `CMakeLists.txt` | **1** | `native/git/CMakeLists.txt` |
| YAML workflows `.yml` | **23** | 18 workflows + issue templates/dependabot |
| Python `.py` | **10** | CI/F-Droid/VPS tooling |
| Shell `.sh` | **35** | CI, VPS, signing, screenshots |
| Markdown `.md` | **81** | 77 under `docs/`, plus root README/CONTRIBUTING/SECURITY/etc. |
| ProGuard `.pro` | **1** | `app/proguard-rules.pro` |
| JSON `.json` | **3** | F-Droid lock, metadata |
| Text `.txt` | **7** | F-Droid/Play metadata |

There is **no Java code**, despite the task brief listing Java. All app logic is Kotlin; the Go
auth broker and Next.js site are separate sub-projects inside the same repository.

---

## 3. Top-level structure

```
.
├── app/                 Android application (Compose, Hilt, Room, native bridges)
├── native/
│   ├── git/             libgit2 + Mbed TLS + Tree-sitter engine and JNI
│   └── config/          obfuscated runtime OAuth configuration JNI library
├── auth-broker/         Go OAuth broker (GitHub App token exchange/refresh/revoke)
├── buildSrc/            Gradle signing/auth-host secret resolution
├── baselineprofile/     Macrobenchmark / Baseline Profile module
├── gradle/              Wrapper + version catalog
├── scripts/             CI, F-Droid, VPS, GitHub automation
├── web/                 Next.js website
├── fastlane/            Store metadata
├── metadata/            F-Droid metadata
├── docs/                77 design/security/release documents
└── .github/             18 workflows + issue templates
```

---

## 4. Android application map

`app/src/main/java/com/nexora/git/` — **208 Kotlin files, 46,729 LOC**.

### 4.1 Root

| File | Role |
| --- | --- |
| `NexoraGitApplication.kt` | `@HiltAndroidApp`, 6 lines, no `onCreate` logic |
| `MainActivity.kt` | `AppCompatActivity`, edge-to-edge, locale observer, Compose `setContent` |

### 4.2 `core/` — 18,658 LOC, 128 files

| Package | Files | Purpose | Notable classes |
| --- | --- | --- | --- |
| `core/actions` | 6 | GitHub Actions runs, logs, artifacts | `GitHubActionsBinaryClient`, gateway |
| `core/advanced` | 5 | Advanced GitHub account/repo surfaces | `GitHubAdvancedGitHubGateway` |
| `core/auth` | 14 | OAuth, PKCE, broker, token storage | `AuthSessionRepository`, `AuthConfig`, `SecureAuthStorage`, `KeystoreCipher` |
| `core/common` | 2 | Result wrapper | `AppResult` |
| `core/database` | 8 | Room v4 | `NexoraDatabase`, 3 DAOs, 3 entities |
| `core/editor` | 15 | Editor engine, syntax, formatting | `EditorFileStore`, `TreeSitterSyntaxEngine`, `NativeSyntaxBridge` |
| `core/explore` | 3 | GitHub search/explore | JSON parsers |
| `core/files` | 4 | File browsing and path policy | `WorkspacePathPolicy`, `CodeBrowserFileSystem` |
| `core/git` | 11 | libgit2 bridge, models, credentials | `Libgit2GitEngine`, `NativeGitBridge`, `GitWorkflowPolicy` |
| `core/issues` | 5 | Issues gateway | REST client |
| `core/network` | 4 | OkHttp/Retrofit | `NetworkModule`, `GitHubHeadersInterceptor` |
| `core/platform` | 20 | GitHub platform primitives | caches, rate limit, error mapper, URL resolver |
| `core/pulls` | 5 | Pull requests | REST client |
| `core/releases` | 6 | Releases/assets | `ReleaseAssetBinaryClient` |
| `core/repository` | 8 | Repository cache and gateway | `RepositoryGateway` |
| `core/search` | 2 | Project-wide search | `ProjectSearchEngine` |
| `core/settings` | 2 | DataStore preferences | `SettingsRepository`, `AppPreferences` |
| `core/social` | 5 | Social graph | gateway |
| `core/storage` | 12 | Workspaces, SAF, direct access | `WorkspaceRegistry`, scanners, sync engine |
| `core/templates` | 2 | Project templates | catalog |
| `core/work` | 7 | WorkManager durability | `DurableGitWorker`, `RepositoryCloneWorker` |

### 4.3 `feature/` — 27,056 LOC, 59 files

18 feature packages: actions (6), activity (2), advanced (2), auth (3), editor (3), explore (2),
files (3), git (3), home (2), issues (5), onboarding (2), profile (2), pulls (5), releases (5),
repositories (5), settings (2), social (1).

### 4.4 `ui/` — 920 LOC, 7 files

Root navigation (`NexoraGitApp`), destination enum, theme (`Theme`, `Color`, `Type`, `Shapes`) and
a `FoundationScreen` component.

### 4.5 Largest files

| Bytes | File |
| ---: | --- |
| 100,982 | `feature/git/GitWorkspaceScreen.kt` |
| 48,925 | `feature/pulls/PullRequestDetailScreen.kt` |
| 39,549 | `feature/advanced/AdvancedGitHubScreen.kt` |
| 38,926 | `feature/editor/MobileEditorScreen.kt` |
| 38,759 | `feature/repositories/RepositoriesScreen.kt` |
| 38,337 | `feature/git/GitWorkspaceViewModel.kt` |
| 35,631 | `feature/releases/ReleaseDetailScreen.kt` |

The 100 KB `GitWorkspaceScreen` is a maintainability and recomposition hotspot (report 11).

---

## 5. Native inventory

| File | LOC | Purpose |
| --- | ---: | --- |
| `native/git/src/git_core.cpp` | 2,979 | Core libgit2 operations |
| `native/git/src/advanced_git.cpp` | 1,588 | Reset, revert, cherry-pick, stash, tags, submodules |
| `native/git/src/git_jni.cpp` | 963 | 50 JNI entry points |
| `native/git/src/lfs_filter.cpp` | 363 | Git LFS filter |
| `native/git/src/syntax_engine.cpp` | 625 | Tree-sitter analysis |
| `native/git/src/syntax_jni.cpp` | 279 | 2 JNI entry points |
| `native/config/src/secure_config_jni.cpp` | 98 | Runtime OAuth config (`RegisterNatives`) |
| `native/git/include/nexora/git_core.h` | — | Engine API |
| `native/git/include/nexora/syntax_engine.h` | — | Syntax API |
| `native/git/tests/native_smoke.cpp` | 1,084 | Native smoke tests |
| `native/config/tests/secure_config_smoke.cpp` | — | Config decode test |
| `native/config/fuzz/secure_config_decode_fuzz.cpp` | — | LibFuzzer target |

Total native `.cpp` + `.h`: **8,713 lines**.

**JNI parity (verified by symbol extraction):**

- `NativeGitBridge.kt` declares **50** `external fun`; `git_jni.cpp` exports **50** matching
  `Java_com_nexora_git_core_git_NativeGitBridge_*` symbols. No mismatch.
- `NativeSyntaxBridge.kt` declares **2**; `syntax_jni.cpp` exports **2**.
- `SecureRuntimeConfigNative` uses `RegisterNatives` from `libnexoragit.so`, not name mangling;
  `JNI_OnLoad` registers `readConfig`.

This is a real engine, not a stub.

---

## 6. Build inventory

| Item | Value | Source |
| --- | --- | --- |
| Gradle wrapper | 9.6.0 (SHA-256 pinned) | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 9.4.0 | `gradle/libs.versions.toml` |
| Kotlin | 2.2.10 | same |
| KSP | 2.3.12 | same |
| Compose BOM | 2026.09.00 | same |
| Hilt | 2.60.1 | same |
| Room | 2.8.5 | same |
| DataStore | 1.2.1 | same |
| Retrofit / OkHttp | 3.0.0 / 5.5.0 | same |
| compileSdk | 37 (minor 0) | `app/build.gradle.kts:38-43` |
| targetSdk | 36 | `app/build.gradle.kts:47` |
| minSdk | 26 | `app/build.gradle.kts:46` |
| NDK | 27.2.12479018 | `app/build.gradle.kts:134` |
| CMake | 3.22.1 | `app/build.gradle.kts` |
| ABI filters | arm64-v8a, armeabi-v7a, x86_64 | `app/build.gradle.kts:57-63` |
| Java source/target | 17 | `app/build.gradle.kts` |
| Release minify/shrink | true / true | `app/build.gradle.kts:105-106` |
| Room schema export | `true`, location `app/schemas` | `NexoraDatabase.kt:15`, `app/build.gradle.kts` |
| Committed Room schemas | **none** (`app/schemas` absent) | `find app -path '*schemas*'` |

---

## 7. Tests and CI inventory

| Item | Count | Location |
| --- | ---: | --- |
| Unit test files (JVM) | 38 | `app/src/test/java/...` |
| Instrumentation test files | 24 | `app/src/androidTest/java/...` |
| Native smoke tests | 2 | `native/git/tests`, `native/config/tests` |
| Fuzz target | 1 | `native/config/fuzz` |
| CI workflows | 18 | `.github/workflows` |
| Baseline profile test | 1 | `baselineprofile/.../PerformanceTests.kt` |

Unit tests cover JSON parsers, auth policy, PKCE, pending authorization, path policy, Git LFS
parsing, Git workflow policy, platform helpers, storage scanners and editor formatting. They do
**not** cover `MainActivity`, the Application class, Hilt startup, theme resolution or the startup
crash path.

Instrumentation tests are Compose UI content tests. They are **compiled** by CI
(`:app:assembleDebugAndroidTest`) but are **not executed** by the standard workflows. Only
`performance.yml` (manual/tag) and the marketing screenshot workflow run an emulator.

---

## 8. Documentation and auxiliary projects

- 81 Markdown documents (77 under `docs/`), including architecture, auth, security, Git engine, editor, performance,
  accessibility, F-Droid readiness and release process.
- `auth-broker/` — Go service for GitHub App OAuth exchange, refresh, revoke and identity.
- `web/` — Next.js marketing/legal site.
- `fastlane/`, `metadata/` — store and F-Droid metadata.
- `scripts/` — 35 shell + 10 Python scripts for CI, VPS, signing, screenshots.

Docs are unusually thorough for a five-day-old project, but they describe intended/target behavior,
not device-verified behavior. Several documents explicitly mark QA and release as open
(`docs/PHASE_S_PRODUCTION_HARDENING_ROADMAP.md`, issue #57).

---

## 9. Dead code, duplication and unused dependencies

| Observation | Evidence | Assessment |
| --- | --- | --- |
| `GitHubApi` has a single unused `getRateLimit()` endpoint | `core/network/GitHubApi.kt`; no injection site (`grep GitHubApi`) | Dead code |
| Retrofit `provideRetrofit` has **no converter factory** | `NetworkModule.kt:46-53` | Any future JSON endpoint fails at runtime; currently dead |
| Retrofit dependency exists only to build the dead API | `app/build.gradle.kts` | Removable once dead code is removed |
| `Typography()` is instantiated repeatedly inside `NexoraTypography` | `ui/theme/Type.kt:9-24` | Minor waste; constant could be extracted |
| `TODO` / `FIXME` markers | `grep` → **0** | Positive: no abandoned placeholders in app code |
| Java code | **0 files** | Not applicable |
| LSP | interface + registry only, no transport/server implementation | Placeholder feature |
| `FoundationScreen.kt` | generic title/description screen | Possibly unused/legacy; verify before deletion |

No circular package dependency was proven. The application is a single Gradle module, so all
"feature" code shares one compilation unit; this is an architectural scale limit, not a defect.

---

## 10. Source inventory conclusions

1. **272 Kotlin files, 0 Java files, 10 C++ files, 2 headers.** The brief's Java expectation does
   not match reality.
2. **The native engine is complete enough to audit seriously**: 50/50 JNI parity and real libgit2
   calls.
3. **The app is a single-module monolith** with large feature files (up to 101 KB).
4. **Test volume is respectable but mis-targeted**: parser/unit coverage is good; startup,
   Activity, theme and device behavior are untested.
5. **No committed Room schema** despite `exportSchema = true`; migration confidence is lower than it
   should be.
6. **Docs and CI are more mature than the runtime evidence.** The project itself says the release
   gate is NO-GO.

Continue with `02_ARCHITECTURE_DEEP_AUDIT.md`.
