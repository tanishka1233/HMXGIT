# 03 — Build and Gradle Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **IMPORTANT:** no Gradle command, CMake build, test or APK build was executed locally. The user
> explicitly prohibited it (low-RAM Android/Termux host). All build results below are **external
> CI evidence** for the audited SHA, retrieved from the GitHub check-runs API.

---

## 1. Toolchain matrix

| Component | Declared value | Source |
| --- | --- | --- |
| Gradle wrapper | **9.6.0**, `distributionSha256Sum` pinned | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | **9.4.0** | `gradle/libs.versions.toml` |
| Kotlin | **2.2.10** | same |
| Compose compiler plugin | Kotlin 2.2.10 | same (`org.jetbrains.kotlin.plugin.compose`) |
| KSP | **2.3.12** | same |
| Hilt | **2.60.1** | same |
| AndroidX Hilt | 1.4.0 | same |
| Compose BOM | **2026.09.00** | same |
| Navigation Compose | 2.10.2 | same |
| Room | 2.8.5 | same |
| DataStore | 1.2.1 | same |
| AppCompat | 1.8.0 | same |
| WorkManager | 2.12.0 | same |
| Retrofit / OkHttp | 3.0.0 / 5.5.0 | same |
| compileSdk | **37** (`release(37)`, minor 0) | `app/build.gradle.kts:38-43` |
| targetSdk | **36** | `app/build.gradle.kts:47` |
| minSdk | **26** | `app/build.gradle.kts:46` |
| NDK | **27.2.12479018** | `app/build.gradle.kts:134` |
| CMake | **3.22.1** | `app/build.gradle.kts` |
| Java source/target | **17** | `app/build.gradle.kts` |
| CI JDK | **17 (Temurin)** | `.github/workflows/android-ci.yml`, `production-ci.yml` |
| Gradle JVM args | `-Xmx3g`, parallel, caching, configuration cache | `gradle.properties` |

Observations:

- **JDK 17 is sufficient** for AGP 9.4/Gradle 9.6 in the project's own CI, and is what the workflows
  install. The audit host was not used to verify this.
- `compileSdk 37` uses the new AGP 9 DSL (`compileSdk { version = release(37) { minorApiLevel = 0 } }`).
  This is valid for AGP 9 but is bleeding-edge; older AGP cannot parse it.
- `configuration-cache=true` and `parallel=true` on a 3 GB heap are aggressive. The CI runner has
  more memory than the user's Termux device. **Do not attempt a local build on the Termux host.**

---

## 2. Module and plugin layout

`settings.gradle.kts`:

```kotlin
rootProject.name = "NexoraGit"
include(":app")
include(":baselineprofile")
```

Only two modules. Root `build.gradle.kts` applies six plugins with `apply false`:

- `com.android.application`
- `com.android.test`
- `androidx.baselineprofile`
- `org.jetbrains.kotlin.plugin.compose`
- `com.google.devtools.ksp`
- `com.google.dagger.hilt.android`

The `:app` module applies application, baseline profile, Compose compiler, KSP and Hilt. There is no
separate `:core`, `:feature` or `:native` Gradle module. Consequence: incremental build parallelism
is limited and architectural boundaries are not enforced by the build.

---

## 3. Build types, signing and minification

### 3.1 Debug

- `applicationIdSuffix = ".debug"` → package `com.nexora.git.debug`.
- `versionNameSuffix = "-debug"`.
- Debug signing is the default Android debug key.
- No minification.

This matters for the crash investigation: **a debug install is a different package name** from a
release install. Logcat and `monkey` commands must use the correct one.

### 3.2 Release

`app/build.gradle.kts:102-121`:

```kotlin
release {
    isDebuggable = false
    isMinifyEnabled = true
    isShrinkResources = true
    if (ephemeralCiSigning) { applicationIdSuffix = ".ci"; versionNameSuffix = "-ci" }
    if (releaseSigning != null) { signingConfig = signingConfigs.getByName("release") }
    ndk { debugSymbolLevel = "SYMBOL_TABLE" }
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

- **Signing is optional.** If `NexoraBuildSecrets.releaseSigning(project)` returns null, the release
  build is still produced but **unsigned**. An unsigned APK cannot be installed on a normal device.
  This is a release-process defect, not a compile error.
- Ephemeral CI signing requires a full temporary keystore and changes the package to
  `com.nexora.git.ci`.
- `isMinifyEnabled` + `isShrinkResources` are both on. R8 correctness therefore matters for JNI and
  reflection paths.

### 3.3 R8 rules

`app/proguard-rules.pro`:

| Rule | Purpose | Assessment |
| --- | --- | --- |
| `-keepclasseswithmembernames,includedescriptorclasses class ...NativeGitBridge { native <methods>; }` | Preserve JNI symbol names | Standard rule; class/method names preserved. JNI exports use `Java_com_nexora_git_core_git_NativeGitBridge_*`, so this is required and present |
| `-keep class ...GitNativeException { <init>(int,int,String); }` | Native code constructs this exception by signature | Correct |
| `-keepclasseswithmembernames ...NativeSyntaxBridge` | JNI names | Correct |
| `-keep,allowoptimization class ...SecureRuntimeConfigNative { native <methods>; }` | `RegisterNatives` by binary name | Correct |

No rule was found for `GitHubApi`, Retrofit interfaces, Hilt or Room, but their libraries ship
consumer rules. Compose has its own rules. The R8 configuration looks **deliberately minimal and
auditable**, which is a strength. The residual risk is that `-keepclasseswithmembernames` permits
shrinking of any member not matched; all currently matched classes are referenced, so this is a
P3 maintenance concern rather than a present defect.

### 3.4 Signing/secret resolution

`buildSrc/src/main/kotlin/com/nexora/build/NexoraBuildSecrets.kt`:

- Reads `NEXORA_SIGNING_*` environment variables.
- Reads `nexora.local.properties` (git-ignored).
- Validates the auth broker URL: HTTPS, no path/query/fragment.
- Throws `GradleException` for malformed production values.

This is good build hygiene. The risk is operational: the **default developer build has no secrets**,
so authentication is intentionally disabled unless the developer configures a broker. That is a
product-availability issue (BUG-007), not a build failure.

---

## 4. Manifest and resource configuration

`app/src/main/AndroidManifest.xml`:

| Item | Value | Assessment |
| --- | --- | --- |
| Application | `.NexoraGitApplication` | Correct |
| `allowBackup` | `false` | Good for token-bearing app |
| Backup rules | both legacy and data-extraction rules exclude everything | Consistent |
| Icon | vector drawable `@drawable/ic_nexora_git` | Valid, though adaptive icon is preferable |
| Locale config | `en`, `es-MX` | Valid |
| Network security | cleartext false; system CAs; user CAs only in debug | Good |
| Theme | `@style/Theme.NexoraGit` | **Broken in night mode (BUG-001)** |
| Activity | `.MainActivity`, exported, `singleTask` | Correct for OAuth callback |
| App link | `https://${nexoraAuthHost}/oauth/android/callback`, `autoVerify` | Host defaults to `nexora.invalid` if no broker is configured |
| FileProvider | `${applicationId}.files`, non-exported, cache path only | Correct |
| Permissions | `INTERNET` only | Minimal and appropriate |

No `android:enableOnBackInvokedCallback` is declared; Android 13+ predictive back uses the default.
No `android:hardwareAccelerated` override.

The manifest has **no** `AppLocalesMetadataHolderService` declaration, even though
`MainActivity` calls `AppCompatDelegate.setApplicationLocales` (BUG-013). That is a configuration
gap for per-app language persistence on API < 33.

---

## 5. Native build

`app/build.gradle.kts`:

- CMake path: `../native/git/CMakeLists.txt`.
- CMake version 3.22.1.
- `cppFlags`: `-std=c++17 -fexceptions -frtti`.
- libgit2 and Mbed TLS pinned by commit via CMake arguments.
- ABI filters: `arm64-v8a`, `armeabi-v7a`, `x86_64`.

`native/git/CMakeLists.txt`:

- `FetchContent` downloads libgit2, Mbed TLS, Tree-sitter and seven grammars unless
  `NEXORA_NATIVE_SOURCE_ROOT` is supplied (`CMakeLists.txt:12-107`).
- Offline mode verifies every checkout against a hard-pinned SHA and applies a libgit2 patch check.
- `USE_SSH OFF` (line 120), `USE_HTTPS mbedTLS` (line 123), `USE_BUNDLED_ZLIB ON`.
- Static core `nexoragit_core` (line 267) links libgit2, mbedcrypto and Tree-sitter.
- Shared libraries `nexoragit` (runtime config) and `nexoragit_native` (Git + syntax) are produced by
  the config CMake include and target definitions in lines 243-410.

Consequences:

1. **Network is required at native configure time by default.** The build is not hermetic unless the
   offline source root is provided. The F-Droid workflow does provide it.
2. **No `x86` (32-bit) ABI.** An x86 emulator or rare 32-bit x86 device will not have the `.so`.
   Combined with the unguarded `System.loadLibrary` calls, that is a crash (BUG-003/BUG-038).
3. **No SSH.** `USE_SSH OFF` means `ssh://`/`git@` remotes cannot be used (BUG-008).

---

## 6. CI evidence for the audited SHA

Source: `GET /repos/Gh0stDeveloper/Nexora-Git/commits/f4befb6.../check-runs` (2026-11-19).

| Check name | Conclusion |
| --- | --- |
| `Release build and production gate` | **success** |
| `Pinned native build without network` | **success** |
| `Validate repository foundation` | **success** |
| `Validate S.17 development metadata` | **success** |
| `Analyze Kotlin/Java` | **success** (latest run) |
| `Analyze C/C++` | **success** (latest run) |
| `Analyze Go Auth Broker` | **success** (latest run) |
| `Analyze TypeScript/JavaScript` | **success** (latest run) |
| `Build, lint and unit test` (Android CI) | **not present for this SHA** |

Interpretation:

- `production-ci.yml` runs `:app:assembleRelease`, `:app:bundleRelease`, `:app:testDebugUnitTest`,
  `:app:lintRelease` (`.github/workflows/production-ci.yml:79-86`). Its success is evidence that the
  **release APK/AAB compiles, unit tests pass and release lint passes** in CI.
- `fdroid-offline-native-ci.yml` runs `cmake`, `cmake --build` and `ctest` with
  `NEXORA_BUILD_NATIVE_TESTS=ON` in a network namespace
  (`.github/workflows/fdroid-offline-native-ci.yml:85-87`). Its success is evidence that the
  **native engine builds and native smoke tests pass** in CI.
- `android-ci.yml` did not run on `f4befb6` because the commit did not change `app/**`, `native/**`
  or Gradle files. Its job does run `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
  `:app:testDebugUnitTest`, `:app:lintDebug`, but not for this exact commit.

**This is not a local build result.** It does not verify installation, startup, dark mode, JNI on a
real device, or instrumented tests.

---

## 7. Build findings

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| BUG-009 | P1 | Release signing optional → unsigned release artifacts possible | `app/build.gradle.kts:111-113` |
| BUG-023 | P3 | No Android CI run for HEAD (path filters) → the audited commit was not debug-built/linted by the standard workflow | check-runs for `f4befb6` |
| BUG-030 | P3 | Default native build fetches dependencies from the network; not hermetic | `native/git/CMakeLists.txt` FetchContent |
| BUG-031 | P3 | Bleeding-edge AGP 9.4 / Gradle 9.6 / compileSdk 37 increases toolchain fragility | version catalog |
| BUG-032 | P3 | `-keepclasseswithmembernames` allows shrinking of unmatched members; verify after refactors | `proguard-rules.pro:10,18` |
| BUG-038 | P3 | No 32-bit x86 ABI | `app/build.gradle.kts:57-63` |
| — | INFO | Room schema export enabled but `app/schemas` absent | `NexoraDatabase.kt:15`; `find` |

### Suggested build-side verification commands (for a suitable host, not the Termux device)

```bash
./gradlew --version
./gradlew :app:assembleDebug --stacktrace
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease   # requires signing env or produces unsigned
```

Do **not** run these on the low-RAM Termux host. Use a workstation, a cloud runner or the project's
GitHub Actions.

---

## 8. Build verdict

- **Declared build configuration is coherent and CI-proven** at the source level.
- **Release hardening is incomplete**: unsigned releases are possible, no committed Room schema,
  no device test execution.
- **The build does not explain the startup crash.** The app compiles and packages; the crash is a
  runtime resource/theme mismatch.
- **The native build is the riskiest part for a fork**: pinned SHA fetches, three ABIs, no SSH, and
  Mbed TLS CA configuration (report 05).
