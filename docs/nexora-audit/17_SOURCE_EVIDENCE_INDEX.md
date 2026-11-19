# 17 — Source Evidence Index

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
>
> Every claim in reports 00–16 maps to one of the entries below. Paths are repository-relative.
> Line numbers refer to the audited commit. External evidence (CI, GitHub API) is marked
> **[EXT]** and was retrieved on 2026-11-19.

---

## 1. Startup crash evidence

| Claim | File:line | What it shows |
| --- | --- | --- |
| Application class | `app/src/main/AndroidManifest.xml:7` | `android:name=".NexoraGitApplication"` |
| App theme | `app/src/main/AndroidManifest.xml:17` | `android:theme="@style/Theme.NexoraGit"` |
| Activity theme | `app/src/main/AndroidManifest.xml:24` | Same theme on `.MainActivity` |
| Activity is exported + singleTask | `app/src/main/AndroidManifest.xml:22-23` | `exported=true`, `launchMode=singleTask` |
| Day theme is AppCompat | `app/src/main/res/values/styles.xml:3` | `parent="Theme.AppCompat.DayNight.NoActionBar"` |
| **Night theme is framework Material** | `app/src/main/res/values-night/styles.xml:3` | `parent="android:style/Theme.Material.NoActionBar"` |
| Activity extends AppCompatActivity | `app/src/main/java/com/nexora/git/MainActivity.kt:26` | `class MainActivity : AppCompatActivity()` |
| `setContent` call | `app/src/main/java/com/nexora/git/MainActivity.kt:40` | First Compose content |
| Locale application | `app/src/main/java/com/nexora/git/MainActivity.kt:84-86` | `setApplicationLocales` |
| No `onNewIntent` | `app/src/main/java/com/nexora/git/MainActivity.kt` (whole file) | Absence of override |
| AppCompat guard | `androidx AppCompatDelegateImpl.java:858-861` **[EXT]** | `hasValue(AppCompatTheme_windowActionBar)` → throw |
| `windowActionBar` is a library attr | `androidx appcompat values/attrs.xml:43` **[EXT]** | `<attr name="windowActionBar">` |
| Theme history | `git log -- values-night/styles.xml` | Introduced `1418cd3`, re-added `374ed80` (2026-10-04) |

---

## 2. Gradle and build

| Claim | File:line | Value |
| --- | --- | --- |
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties` | `gradle-9.6.0-bin.zip`, SHA-256 pinned |
| AGP / Kotlin / KSP | `gradle/libs.versions.toml` | 9.4.0 / 2.2.10 / 2.3.12 |
| Compose BOM | `gradle/libs.versions.toml` | 2026.09.00 |
| Hilt / Room / DataStore | `gradle/libs.versions.toml` | 2.60.1 / 2.8.5 / 1.2.1 |
| Retrofit / OkHttp | `gradle/libs.versions.toml` | 3.0.0 / 5.5.0 |
| compileSdk / targetSdk / minSdk | `app/build.gradle.kts:38-49` | 37 / 36 / 26 |
| ABI filters | `app/build.gradle.kts:57-63` | arm64-v8a, armeabi-v7a, x86_64 |
| CMake args (pins) | `app/build.gradle.kts:68-70` | libgit2 + Mbed TLS SHAs |
| C++ flags | `app/build.gradle.kts:74-78` | `-std=c++17 -fexceptions -frtti` |
| Release minify/shrink | `app/build.gradle.kts:105-106` | both true |
| Signing optional | `app/build.gradle.kts:111-113` | `if (releaseSigning != null)` |
| NDK version | `app/build.gradle.kts:134` | 27.2.12479018 |
| Modules | `settings.gradle.kts` | `:app`, `:baselineprofile` |
| Gradle memory | `gradle.properties` | `-Xmx3g`, parallel, caching, config cache |
| R8 JNI keeps | `app/proguard-rules.pro:10-27` | NativeGitBridge, GitNativeException, NativeSyntaxBridge, SecureRuntimeConfigNative |

---

## 3. Native engine

| Claim | File:line | What it shows |
| --- | --- | --- |
| libgit2 pin | `native/git/CMakeLists.txt:12` | `49e408b3208bc3093757a1c2db938d3590f3f412` |
| Mbed TLS pin | `native/git/CMakeLists.txt:13` | `068ff080…` |
| Tree-sitter pins | `native/git/CMakeLists.txt:14-20` | 7 grammars + core |
| **No SSH** | `native/git/CMakeLists.txt:120` | `USE_SSH OFF` |
| HTTPS via Mbed TLS | `native/git/CMakeLists.txt:123` | `USE_HTTPS mbedTLS` |
| Bundled zlib | `native/git/CMakeLists.txt:124` | `USE_BUNDLED_ZLIB ON` |
| Native test option | `native/git/CMakeLists.txt:79` | `NEXORA_BUILD_NATIVE_TESTS` |
| Shared targets | `native/git/CMakeLists.txt:315-349` | `nexoragit`, `nexoragit_native` |
| JNI exports | `native/git/src/git_jni.cpp` | 50 `JNIEXPORT` symbols |
| Syntax JNI exports | `native/git/src/syntax_jni.cpp` | 2 symbols |
| Kotlin externals | `app/src/main/java/com/nexora/git/core/git/NativeGitBridge.kt` | 50 `external fun` |
| Syntax externals | `app/src/main/java/com/nexora/git/core/editor/NativeSyntaxBridge.kt` | 2 |
| **Unguarded load (Git)** | `NativeGitBridge.kt:316-323` | `System.loadLibrary` in `init` |
| CA directory | `NativeGitBridge.kt:317-318` | `/system/etc/security/cacerts` |
| **Unguarded load (syntax)** | `NativeSyntaxBridge.kt:15-19` | `System.loadLibrary` in `init` |
| Config load (guarded) | `app/src/main/java/com/nexora/git/core/auth/SecureRuntimeConfigNative.kt:11-14` | `runCatching` |
| Credential callbacks only | `native/git/src/git_core.cpp:180-185` | `remote_callbacks()` |
| Credential URL gate | `native/git/src/git_core.cpp` `is_github_https_url` | GitHub HTTPS only |
| Rename leak | `native/git/src/git_core.cpp:985-1012` | dispose only on `rc == 0` (1007-1009) |
| **Modified UTF-8** | `native/git/src/git_jni.cpp:11-20` | `GetStringUTFChars` |
| Correct UTF-16 handling | `native/git/src/syntax_jni.cpp:75-100` | `GetStringChars` + surrogates |
| Config `ReadConfig` | `native/config/src/secure_config_jni.cpp:19-72` | Local refs |
| Config wipe | `native/config/src/secure_config_jni.cpp:11-17` | `SecureErase` |
| Config `JNI_OnLoad` | `native/config/src/secure_config_jni.cpp:76-98` | `RegisterNatives` |

---

## 4. Git engine behaviour

| Claim | File:line | What it shows |
| --- | --- | --- |
| Engine interface | `app/src/main/java/com/nexora/git/core/git/GitEngine.kt:3-248` | 43 operations |
| Engine implementation | `app/src/main/java/com/nexora/git/core/git/Libgit2GitEngine.kt` | All ops → JNI |
| Force-with-lease | `Libgit2GitEngine.kt:293-325` | Requires `expectedRemoteOid` |
| Credential provider | `app/src/main/java/com/nexora/git/core/git/GitCredentialProvider.kt:13-40` | `x-access-token` |
| Remote policy | `app/src/main/java/com/nexora/git/core/git/GitRemoteSecurityPolicy.kt:10-21` | GitHub HTTPS only |
| **No locks** | `grep Mutex/withLock/FileLock core/git core/work` | Empty |
| WorkManager durability | `app/src/main/java/com/nexora/git/core/work/DurableGitOperations.kt` | Unique names only |
| Workflow policy (UI layer) | `app/src/main/java/com/nexora/git/feature/git/GitWorkflowPolicy.kt:1-80` | Branch/remote validation |
| LFS inspect | `app/src/main/java/com/nexora/git/core/git/GitLfsManager.kt` | `.gitattributes`, pointers |
| LFS native filter | `native/git/src/lfs_filter.cpp:329-341` | `git_filter_register` |

---

## 5. Authentication and storage

| Claim | File:line | What it shows |
| --- | --- | --- |
| Config from native | `app/src/main/java/com/nexora/git/core/auth/AuthConfig.kt:9-25` | `read()` + `isConfigured` |
| Endpoint validation | `AuthConfig.kt:28-60` | HTTPS same-origin checks |
| PKCE | `app/src/main/java/com/nexora/git/core/auth/PkceGenerator.kt` | S256 |
| State validation | `app/src/main/java/com/nexora/git/core/auth/PendingAuthorizationValidator.kt:15-19` | Constant-time + 10 min |
| Callback bus | `app/src/main/java/com/nexora/git/core/auth/AuthCallbackBus.kt:12-18` | `Channel(BUFFERED)` |
| **Global refresh mutex** | `AuthSessionRepository.kt:28,92-110` | `refreshMutex` |
| **Non-atomic writes** | `AuthSessionRepository.kt:232-246` | token → Room → active id |
| **`apply()` tokens** | `app/src/main/java/com/nexora/git/core/auth/SecureAuthStorage.kt:25,46-53,75-90` | Async persistence |
| **Keystore race** | `app/src/main/java/com/nexora/git/core/auth/KeystoreCipher.kt:47-72` | get-then-generate |
| Startup collectors | `app/src/main/java/com/nexora/git/feature/auth/AuthViewModel.kt:32-53` | No `.catch` |
| Retrofit | `app/src/main/java/com/nexora/git/core/network/NetworkModule.kt:46-59` | No converter |
| Dead API | `app/src/main/java/com/nexora/git/core/network/GitHubApi.kt` | `getRateLimit` only |
| Header interceptor | `app/src/main/java/com/nexora/git/core/network/GitHubHeadersInterceptor.kt:20-27` | API version + UA |
| Log redaction | `NetworkModule.kt:28-30` | `redactHeader` |
| Settings DataStore | `app/src/main/java/com/nexora/git/core/settings/SettingsRepository.kt:16` | `preferencesDataStore` |
| Theme enum | `app/src/main/java/com/nexora/git/core/settings/AppPreferences.kt:3-8` | SYSTEM/LIGHT/DARK/AMOLED |
| Theme setter | `app/src/main/java/com/nexora/git/feature/settings/SettingsViewModel.kt:49` | Only persists |

---

## 6. Room and lifecycle

| Claim | File:line | What it shows |
| --- | --- | --- |
| DB version 4 + export | `app/src/main/java/com/nexora/git/core/database/NexoraDatabase.kt:14-15` | `version = 4, exportSchema = true` |
| Migrations | `NexoraDatabase.kt:23-129` | 1→2, 2→3, 3→4 |
| **No fallback** | `app/src/main/java/com/nexora/git/core/database/DatabaseModule.kt:20-30` | `addMigrations` only |
| No committed schemas | `find app -path '*schemas*'` | Empty |
| Entities | `core/database/*Entity.kt` | 3 tables |

---

## 7. UI, editor, performance

| Claim | File:line | What it shows |
| --- | --- | --- |
| Destinations | `app/src/main/java/com/nexora/git/ui/navigation/NexoraDestination.kt:14-30` | 6 bottom-bar routes |
| `requireNotNull` | `app/src/main/java/com/nexora/git/ui/NexoraGitApp.kt:162` | Active account assertion |
| Custom Tab | `NexoraGitApp.kt:75-84` | Unguarded `launchUrl` |
| Theme context cast | `app/src/main/java/com/nexora/git/ui/theme/Theme.kt:73-81` | `view.context as Activity` |
| Editor syntax job | `app/src/main/java/com/nexora/git/feature/editor/MobileEditorViewModel.kt:722-770` | Full-text analysis |
| Editor store | `app/src/main/java/com/nexora/git/core/editor/EditorFileStore.kt:26-110` | AtomicFile + mtime |
| Editor cap | `EditorFileStore.kt:113` | 512 KiB |
| Browse cap | `app/src/main/java/com/nexora/git/core/files/CodeBrowserFileSystem.kt:321` | 2 MiB |
| Path policy | `app/src/main/java/com/nexora/git/core/files/WorkspacePathPolicy.kt:10-51` | Canonical containment |
| Search bounds | `app/src/main/java/com/nexora/git/core/search/ProjectSearchEngine.kt:241-244` | 256/5000/500/1 MiB |
| Home serial I/O | `app/src/main/java/com/nexora/git/feature/home/HomeViewModel.kt:55-143` | `mapLatest` + sequential |
| Response cache | `app/src/main/java/com/nexora/git/core/platform/GitHubResponseCache.kt:22-73` | 128 entries |
| GraphQL cache | `app/src/main/java/com/nexora/git/core/platform/GitHubGraphQlCache.kt:16-35` | 64 entries |

---

## 8. Manifest and security config

| Claim | File:line | What it shows |
| --- | --- | --- |
| Permissions | `AndroidManifest.xml:4` | `INTERNET` only |
| Backup disabled | `AndroidManifest.xml:8-10` | `allowBackup=false` + rules |
| Network config | `AndroidManifest.xml:14` | Cleartext false, system CAs |
| Locale config | `AndroidManifest.xml:13` | `en`, `es-MX` |
| App links | `AndroidManifest.xml:25-40` | HTTPS OAuth callback |
| FileProvider | `AndroidManifest.xml:42-50` | non-exported, cache path |
| Backup rules | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Exclude all domains |
| **No locale service** | `AndroidManifest.xml` (whole) | Absence of `AppLocalesMetadataHolderService` |

---

## 9. CI and external evidence

| Claim | Source | Result |
| --- | --- | --- |
| HEAD SHA | `git rev-parse HEAD` | `f4befb6c1954d956f0a67b14f4ab1642aa185833` |
| Branch | `git status` | `main...origin/main`, clean |
| Commits | `git rev-list --count HEAD` | 409 |
| Contributors | `git shortlog -sne` | 1 |
| Tags / releases | `git tag`, GitHub API | 0 / 0 |
| Stars / forks | GitHub API | 4 / 0 |
| License | `LICENSE` | Apache-2.0 |
| Production gate | check-runs API **[EXT]** | success |
| Pinned native build | check-runs API **[EXT]** | success |
| Foundation gate | check-runs API **[EXT]** | success |
| S.17 metadata gate | check-runs API **[EXT]** | success |
| CodeQL Kotlin/Java (latest) | check-runs API **[EXT]** | success |
| CodeQL Kotlin/Java (earlier same SHA) | check-runs API **[EXT]** | failure |
| CodeQL C/C++, Go, TS/JS | check-runs API **[EXT]** | success |
| Android CI on this SHA | check-runs API **[EXT]** | **not present** (path filters) |
| Release gate issue | `issues/57` **[EXT]** | explicitly **NO-GO** |
| Production workflow | `.github/workflows/production-ci.yml:79-86` | assemble/bundle/test/lint |
| Offline native workflow | `.github/workflows/fdroid-offline-native-ci.yml:85-87` | cmake/build/ctest, no network |
| Android CI triggers | `.github/workflows/android-ci.yml:5-24` | path filters |

---

## 10. Claims that are NOT backed by evidence

The following are explicitly **not** claimed, and no report should be read as claiming them:

| Not claimed | Why |
| --- | --- |
| "The APK was built" | No build was run; prohibited |
| "The app was installed/launched" | No device |
| "The crash was reproduced" | No device |
| "Logcat shows X" | No Logcat |
| "The night theme is the proven cause" | It is the highest-confidence static cause, not device-confirmed |
| "Unit tests pass locally" | Only external CI evidence exists |
| "Native tests pass locally" | Only external CI evidence exists |
| "The broker is secure in deployment" | No broker was deployed or probed |
| "The app has no other bugs" | Static audit with stated limits (report 18) |
| "HMX Git will work if forked" | Needs Phase 0 + Phase 1 verification |

---

## 11. Index integrity

- 40 findings, each with at least one file:line anchor above or in report 13.
- Every external claim carries **[EXT]** and a retrieval date.
- Where a line number is version-dependent (AppCompat internals), the dependency version is named.
- No citation points to a file that does not exist at the audited commit.
