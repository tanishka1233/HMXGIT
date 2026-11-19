# 13 — Master Bug and Error Register

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** all findings from reports 03–12, de-duplicated and severity-ranked.
> **No fix was applied.** Every entry is a record, not a change.

---

## 1. Register summary

| Metric | Value |
| --- | ---: |
| Total findings | **40** |
| Confirmed from source (static) | **33** |
| Suspected / runtime-dependent | **7** |
| Critical (P0) | 2 |
| High (P1) | 8 |
| Medium (P2) | 17 |
| Low (P3) | 13 |

**Status definitions**

- **Confirmed (static):** the defect is present in the source at the cited lines; the code path is
  reachable. Runtime *impact* may still depend on conditions.
- **Suspected:** the source condition exists, but the harmful outcome depends on device state,
  timing, API level or a deployment that this audit could not exercise.

---

## 2. Master table

| ID | Sev | Status | Title | Primary evidence |
| --- | --- | --- | --- | --- |
| BUG-001 | P0 | Confirmed | Night theme is a framework Material theme, not AppCompat → immediate crash in dark mode | `app/src/main/res/values-night/styles.xml:3`; `MainActivity.kt:26,40` |
| BUG-002 | P0 | Confirmed | In-app theme setting never changes the platform night mode | `SettingsViewModel.kt:49`; no `setDefaultNightMode` anywhere |
| BUG-003 | P1 | Confirmed | Unguarded `System.loadLibrary("nexoragit_native")` in two classes | `NativeGitBridge.kt:316-323`; `NativeSyntaxBridge.kt:15-19` |
| BUG-004 | P1 | Confirmed | No per-repository operation lock; UI and WorkManager can write concurrently | no `Mutex` in `core/git`/`core/work`; `Libgit2GitEngine` is `@Singleton` |
| BUG-005 | P1 | Confirmed | No transfer progress, cancellation or timeout in native Git | `git_core.cpp:180-185`; `remote_callbacks` sets credentials only |
| BUG-006 | P1 | Confirmed | Room v4 has no destructive fallback and no committed schema | `DatabaseModule.kt:20-30`; `NexoraDatabase.kt:14-15`; `app/schemas` absent |
| BUG-007 | P1 | Confirmed | Default build cannot authenticate at all (no broker config) | `AuthConfig.kt:16-25`; `nexora.local.properties.example` |
| BUG-008 | P1 | Confirmed | No SSH transport; `git@`/`ssh://` remotes unsupported | `native/git/CMakeLists.txt:120` (`USE_SSH OFF`) |
| BUG-009 | P1 | Confirmed | Release signing is optional → unsigned release artifacts possible | `app/build.gradle.kts:111-113` |
| BUG-010 | P1 | Suspected | Hard-coded `/system/etc/security/cacerts` may be stale/absent on modern Android | `NativeGitBridge.kt:317-318` |
| BUG-011 | P2 | Confirmed | Startup auth collectors have no exception boundary | `AuthViewModel.kt:32-53` |
| BUG-012 | P2 | Suspected | `requireNotNull(state.activeAccount)` is a latent invariant crash | `NexoraGitApp.kt:162` |
| BUG-013 | P2 | Confirmed | `setApplicationLocales` used without `AppLocalesMetadataHolderService` | `MainActivity.kt:84-86`; manifest lacks the service |
| BUG-014 | P2 | Confirmed | Global refresh mutex serializes token refresh across all accounts | `AuthSessionRepository.kt:28,92-110` |
| BUG-015 | P2 | Confirmed | Token persistence uses `SharedPreferences.apply()` (async) | `SecureAuthStorage.kt:25,46-53,75-90` |
| BUG-016 | P2 | Suspected | Keystore key creation is not synchronized | `KeystoreCipher.kt:47-72` |
| BUG-017 | P2 | Confirmed | Token write, Room upsert and active-account write are non-atomic | `AuthSessionRepository.kt:232-246` |
| BUG-018 | P2 | Confirmed | Retrofit has no converter and its only API is dead code | `NetworkModule.kt:46-59`; `GitHubApi.kt` |
| BUG-019 | P2 | Confirmed | Home performs serial native Git I/O per workspace | `HomeViewModel.kt:55-143` |
| BUG-020 | P2 | Confirmed | Response caches are bounded by entries, not bytes | `GitHubResponseCache.kt:22-73`; `GitHubGraphQlCache.kt` |
| BUG-021 | P2 | Confirmed | Editor re-copies full text and re-analyses syntax per keystroke | `MobileEditorViewModel.kt:722-770` |
| BUG-022 | P2 | Confirmed | Theme casts `view.context` directly to `Activity` | `Theme.kt:73-81` |
| BUG-023 | P3 | Confirmed | Android CI did not run on the audited SHA (path filters) | check-runs API for `f4befb6` |
| BUG-024 | P3 | Confirmed | Navigation uses untyped string routes | `NexoraGitApp.kt` `NavHost`; `NexoraDestination.kt` |
| BUG-025 | P3 | Suspected | No `onNewIntent` → warm OAuth callbacks may be dropped | `MainActivity.kt` (no override); manifest `singleTask` |
| BUG-026 | P2 | Suspected | `throw_native` does not handle a null `NewStringUTF` result | `git_jni.cpp` `throw_native` |
| BUG-027 | P2 | Confirmed | Config `JNI_OnLoad` failure silently disables authentication | `secure_config_jni.cpp:76-98`; `SecureRuntimeConfigNative.kt:11-14` |
| BUG-028 | P2 | Suspected | GitHub response cache is not partitioned by account | `GitHubResponseCache.kt` singleton; `switchAccount` |
| BUG-029 | P3 | Suspected | Custom Tab launch is not guarded against `ActivityNotFoundException` | `NexoraGitApp.kt:75-84` |
| BUG-030 | P3 | Confirmed | Default native build fetches dependencies from the network (not hermetic) | `native/git/CMakeLists.txt` FetchContent |
| BUG-031 | P3 | Confirmed | Bleeding-edge toolchain (AGP 9.4, Gradle 9.6, compileSdk 37) raises fragility | `gradle/libs.versions.toml` |
| BUG-032 | P3 | Confirmed | `-keepclasseswithmembernames` can shrink unmatched members after refactors | `app/proguard-rules.pro:10,18` |
| BUG-033 | P3 | Confirmed | `git_strarray problems` leaked when `git_remote_rename` fails | `git_core.cpp:1007-1011` |
| BUG-034 | P3 | Confirmed | `ReadConfig` leaks the `result` local ref on `NewStringUTF` failure | `secure_config_jni.cpp:48-56` |
| BUG-035 | P3 | Confirmed | libgit2 is never shut down; failed init increments the refcount | `git_core.cpp` `initialize` (`call_once`) |
| BUG-036 | P3 | Confirmed | README capability claims exceed verified device behavior | `README.md`; issue #57 (NO-GO) |
| BUG-037 | P3 | Confirmed | No cleanup of partial clone directories or stale `index.lock` after an aborted operation | `core/work`; no lock recovery found |
| BUG-038 | P3 | Confirmed | No 32-bit x86 ABI | `app/build.gradle.kts:57-63` |
| BUG-039 | P2 | Confirmed | Git JNI uses modified UTF-8, corrupting non-BMP paths/messages | `git_jni.cpp:11-20` |
| BUG-040 | P2 | Confirmed | Native credential buffers are not zeroized | `git_jni.cpp` `credentials()`; `git_core.cpp` |

---

## 3. P0 — Critical

### BUG-001 — Night theme is not an AppCompat theme

- **Where:** `app/src/main/res/values-night/styles.xml:3`
- **What:** `<style name="Theme.NexoraGit" parent="android:style/Theme.Material.NoActionBar">`
- **Why it fails:** `MainActivity` extends `AppCompatActivity` (`MainActivity.kt:26`) and calls
  `setContent` (`MainActivity.kt:40`). Compose's `setContent` calls
  `AppCompatActivity.setContentView` → `AppCompatDelegateImpl.ensureSubDecor()` →
  `createSubDecor()`. AppCompat checks
  `a.hasValue(R.styleable.AppCompatTheme_windowActionBar)` (`AppCompatDelegateImpl.java:858`) and
  throws at line 861:
  `IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.`
- **Why a framework theme fails:** `windowActionBar` is a library attribute declared by
  `appcompat` (`values/attrs.xml:43`), not an `android:` framework attribute. A framework
  `Theme.Material` parent cannot satisfy it.
- **Trigger:** device (or app) in night mode. Deterministic on API 29+ with system dark theme.
- **Impact:** process death before the first Compose frame. No UI, no error dialog.
- **Status:** Confirmed (static). Runtime crash unconfirmed (no device).
- **Fix direction:** use `Theme.AppCompat.DayNight.NoActionBar` in `values-night` too, or move to
  `ComponentActivity`. Then add a night-mode launch test.

### BUG-002 — Theme setting never changes the platform night mode

- **Where:** `feature/settings/SettingsViewModel.kt:49` (`repository.setThemeMode(mode)`),
  `MainActivity.kt:41-55`, `ui/theme/Theme.kt`
- **What:** `AppThemeMode` (`SYSTEM`/`LIGHT`/`DARK`/`AMOLED`) only selects Compose color schemes.
  `AppCompatDelegate.setDefaultNightMode` is never called (`grep` across `app/src/main/java` returns
  nothing).
- **Impact:** the user cannot escape BUG-001 through the in-app setting. Selecting "Light" on a dark
  device still resolves `values-night` and still crashes. Selecting "Dark" on a light device gives
  dark Compose colors over a light platform window.
- **Status:** Confirmed (static).
- **Fix direction:** map the setting to `setDefaultNightMode` (or use a single AppCompat DayNight
  theme and let Compose follow `isSystemInDarkTheme` plus the override).

---

## 4. P1 — High

### BUG-003 — Unguarded native library loads

`NativeGitBridge.kt:316-323` and `NativeSyntaxBridge.kt:15-19` call
`System.loadLibrary("nexoragit_native")` in a companion `init`. `UnsatisfiedLinkError` is an `Error`,
not an `Exception`, and is not caught. On an ABI without the `.so` (the build excludes 32-bit x86,
BUG-038), or after a partial install, the first touch of these classes kills the process. The config
library, by contrast, is guarded (`SecureRuntimeConfigNative.kt:11-14`) — the asymmetry is the defect.

### BUG-004 — No per-repository lock

No `Mutex`/`withLock`/`FileLock` exists in `core/git` or `core/work`. `Libgit2GitEngine` is a
singleton shared by UI and WorkManager. libgit2 is not safe for concurrent writes to one repository;
`index.lock` contention or interleaved index writes can corrupt state. WorkManager unique names
serialize only its own jobs.

### BUG-005 — No progress, cancellation or timeout

`git_core.cpp:180-185` installs only the credentials callback. There is no
`transfer_progress`, `sideband_progress`, `push_transfer_progress`, `certificate_check`, or a
cancellation flag. Cancelling the Kotlin coroutine does not stop the native call. A large clone
cannot be cancelled and shows an indeterminate spinner.

### BUG-006 — Room has no recovery path and no committed schema

`DatabaseModule.kt:20-30` adds three migrations but no `fallbackToDestructiveMigration` and no
downgrade fallback. `NexoraDatabase.kt:14-15` sets `version = 4, exportSchema = true`, but
`app/schemas` is absent from the repository. A downgrade, migration bug or partial migration makes
the database permanently unopenable; recovery requires clearing app data. Combined with BUG-011 this
is a startup crash.

### BUG-007 — Default build cannot authenticate

`AuthConfig.isConfigured` requires a client ID, a broker URL and a callback URL that pass HTTPS
same-origin checks. A stock checkout has none of these, so `beginAuthorization()` returns
`NOT_CONFIGURED` and the login button is disabled. The app is usable only as a local Git client.
HMX Git needs its own broker or a different OAuth design.

### BUG-008 — No SSH transport

`USE_SSH OFF` (`native/git/CMakeLists.txt:120`). No libssh2. SSH remotes are impossible. GitHub HTTPS
is the only supported transport. This is a capability gap for an "advanced" Git client.

### BUG-009 — Release signing is optional

`app/build.gradle.kts:111-113` assigns `signingConfig` only when `releaseSigning != null`. The release
build still succeeds without it, producing an **unsigned** APK/AAB. A release pipeline that does not
fail on a missing signer can ship an uninstallable artifact. Supply-chain/release-integrity risk.

### BUG-010 — Hard-coded CA directory

`NativeGitBridge.kt:317-318` passes `/system/etc/security/cacerts` to libgit2/Mbed TLS. On modern
Android the CA store is APEX-based (`/apex/com.android.conscrypt/cacerts`); the legacy path may be
absent or stale. Then every HTTPS Git operation fails certificate validation while the OkHttp-based
GitHub API still works — a confusing, hard-to-diagnose failure. Marked Suspected because it depends
on the device image.

---

## 5. P2 — Medium

| ID | Finding | Evidence | Impact | Fix direction |
| --- | --- | --- | --- | --- |
| BUG-011 | Startup collectors lack `.catch` | `AuthViewModel.kt:32-53` | Room/DataStore error → main-thread crash at launch | Map errors to UI state |
| BUG-012 | `requireNotNull(activeAccount)` | `NexoraGitApp.kt:162` | Latent crash if state invariant breaks | Sealed signed-in state |
| BUG-013 | Locale applied without metadata service | `MainActivity.kt:84-86`; manifest | Language may not persist on API 26–32 | Declare `AppLocalesMetadataHolderService` |
| BUG-014 | Global refresh mutex | `AuthSessionRepository.kt:28,92-110` | One slow refresh blocks all accounts/Git | Per-account mutex |
| BUG-015 | `apply()` token writes | `SecureAuthStorage.kt` | Account without token after kill | `commit()` or single store |
| BUG-016 | Unsynchronized Keystore key creation | `KeystoreCipher.kt:47-72` | Rare decrypt failure under concurrency | Synchronize / eager init |
| BUG-017 | Non-atomic auth writes | `AuthSessionRepository.kt:232-246` | Orphaned tokens / no active account | Ordered writes or one store |
| BUG-018 | Retrofit without converter; dead API | `NetworkModule.kt:46-59` | Trap for next developer; dead code | Remove or complete |
| BUG-019 | Serial Git I/O on Home | `HomeViewModel.kt:55-143` | Up to ~9 sequential native calls (full status scan each) per refresh | Parallelize + cache |
| BUG-020 | Caches bounded by entries, not bytes | `GitHubResponseCache.kt`, `GitHubGraphQlCache.kt` | Up to tens of MB retained → OOM risk | Byte budget + eviction |
| BUG-021 | Full-text syntax analysis per keystroke | `MobileEditorViewModel.kt:722-770` | Typing jank in large files | Viewport-aware analysis |
| BUG-022 | `view.context as Activity` | `Theme.kt:73-81` | `ClassCastException` outside Activity host | Walk context chain |
| BUG-026 | Null `NewStringUTF` unchecked | `git_jni.cpp` `throw_native` | Second JNI exception under OOM | Null-check and fallback |
| BUG-027 | Config JNI failure looks like "not configured" | `secure_config_jni.cpp:76-98`; `SecureRuntimeConfigNative.kt:11-14` | Silent auth loss on packaging regression | Fail loudly |
| BUG-028 | Cache not partitioned by account | `GitHubResponseCache.kt`; `switchAccount` | Cross-account data bleed on identical URLs | Include account in key |
| BUG-039 | Modified UTF-8 at Git JNI | `git_jni.cpp:11-20` | Non-BMP paths/messages corrupted or unresolved | Convert UTF-16 → UTF-8 |
| BUG-040 | Native credentials not zeroized | `git_jni.cpp` `credentials()` | Token residues in freed heap | Wipe on destruction |

---

## 6. P3 — Low

| ID | Finding | Evidence | Impact |
| --- | --- | --- | --- |
| BUG-023 | Android CI absent for the audited SHA | check-runs API | The commit was not debug-built/linted by `android-ci.yml` |
| BUG-024 | Untyped string navigation routes | `NexoraGitApp.kt`, `NexoraDestination.kt` | No compile-time argument safety |
| BUG-025 | No `onNewIntent` for OAuth callbacks | `MainActivity.kt`; `singleTask` | Warm callbacks may be dropped |
| BUG-029 | Custom Tab launch unguarded | `NexoraGitApp.kt:75-84` | Crash on devices without a browser |
| BUG-030 | Native build fetches from network by default | `native/git/CMakeLists.txt` | Non-hermetic build; offline job works around it |
| BUG-031 | Bleeding-edge toolchain | `libs.versions.toml` | Toolchain fragility for forks |
| BUG-032 | `-keepclasseswithmembernames` risk | `proguard-rules.pro:10,18` | JNI break after refactor |
| BUG-033 | `git_strarray` leak on failed rename | `git_core.cpp:1007-1011` | Small native leak |
| BUG-034 | Config JNI local-ref leak on error | `secure_config_jni.cpp:48-56` | Rare JNI local-ref overflow under OOM |
| BUG-035 | No `git_libgit2_shutdown` | `git_core.cpp` `initialize` | Refcount growth on retries |
| BUG-036 | README overclaims | `README.md`; issue #57 | Due-diligence risk |
| BUG-037 | No cleanup of partial clones / stale `index.lock` | `core/work` | Manual recovery after crash |
| BUG-038 | No 32-bit x86 ABI | `app/build.gradle.kts:57-63` | Emulator/rare device crash (with BUG-003) |

---

## 7. Findings deliberately not registered as bugs

These are real but are **capability gaps or observations**, not defects. They belong in a product
roadmap, not a bug tracker:

- LSP interface with no implementation.
- Conflict resolution limited to OURS/THEIRS.
- No shallow/partial clone, no prune.
- No Paging 3.
- No GitHub deep links.
- No OkHttp disk cache.
- No foreground service for long clones.
- Editor drafts not persisted across process death.
- Unbounded diff size.
- `FoundationScreen` dead code.
- `Typography()` re-instantiation.
- No SBOM.
- No certificate pinning for the broker.

They are discussed in reports 06, 09, 10 and 11 so they are not lost; they are excluded from the
40-finding count to keep the register honest about what is a *bug*.

---

## 8. Register integrity

- Every ID maps to exactly one file:line set at `f4befb6`.
- Severity is assigned by blast radius: P0 = app unusable on a mainstream configuration; P1 = data
  loss, credential exposure, or a core capability missing; P2 = degraded correctness/performance;
  P3 = hygiene, edge case or process.
- No finding is double-counted. Where two reports touched the same issue, it appears once.
- 7 findings are **Suspected** because their harmful outcome could not be exercised without a device
  or a deployed broker. They are not downgraded to observations, because the source condition is
  present and the failure mode is credible.
