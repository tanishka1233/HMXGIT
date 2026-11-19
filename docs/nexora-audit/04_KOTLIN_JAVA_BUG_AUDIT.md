# 04 — Kotlin / Java Bug Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** all 272 Kotlin files; **0 Java files exist**.
> **Method:** static reading + targeted greps. No runtime execution.

---

## 1. Summary

| Metric | Value |
| --- | --- |
| Kotlin files | 272 (208 app, 38 unit test, 24 instrumentation, 2 baseline) |
| Java files | **0** |
| `GlobalScope` usages | **0** |
| `runBlocking` usages | **0** |
| `TODO()` / `FIXME` markers in app code | **0** |
| `runCatching` call sites | 96 |
| Broad `catch (Exception)` sites | 21 |
| `!!` usages | **0** in app code |
| `lateinit` | 2, both Hilt-injected Activity fields |
| Kotlin bugs registered here | 15 |

The Kotlin is generally clean: no `GlobalScope`, no `!!`, no TODO stubs, explicit DI, and
`collectAsStateWithLifecycle` is used consistently. The defects below are real but mostly
P2/P3; the P0 startup defect is in resources/manifest interaction, documented in report 14.

---

## 2. BUG-002 — In-app theme selection never changes the Android theme (P0, Confirmed)

**Files:** `app/src/main/java/com/nexora/git/MainActivity.kt:41-55`,
`app/src/main/java/com/nexora/git/feature/settings/SettingsViewModel.kt:48-50`,
`app/src/main/java/com/nexora/git/core/settings/AppPreferences.kt:3-8`.

**Evidence:**

- `AppThemeMode` has `SYSTEM`, `LIGHT`, `DARK`, `AMOLED`.
- `SettingsViewModel.setTheme()` only calls `repository.setThemeMode(mode)`.
- `MainActivity` collects `themeMode` and passes it to `NexoraGitTheme`, which selects a **Compose
  color scheme**.
- `AppCompatDelegate.setDefaultNightMode(...)` is **never called anywhere in the app**:
  `grep -rn "setDefaultNightMode" app/src/main/java` returns nothing.

**Problem:** The platform Activity theme still follows the system `uiMode`. If the user selects
"Light" in the app while the device is dark, Android still inflates `values-night/styles.xml` and
the AppCompat crash (BUG-001) still occurs. Conversely, selecting "Dark" on a light device gives
dark Compose colors over a light platform window until the system changes.

**Impact:** The in-app setting cannot rescue a user from the P0 crash; the theme setting is
cosmetic only. The AMOLED mode has the same issue.

**Fix direction (not applied):** map `AppThemeMode` to `AppCompatDelegate.setDefaultNightMode`
(`MODE_NIGHT_NO` / `MODE_NIGHT_YES` / `MODE_NIGHT_FOLLOW_SYSTEM`) in a single place, or stop using
`AppCompatActivity` and use `ComponentActivity` with a Compose-only theme strategy.

**Verification:** toggle the setting on a dark-mode device; the Activity must be recreated with the
matching platform theme and the AppCompat check must pass.

---

## 3. BUG-011 — Startup auth/data collectors have no exception boundary (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/feature/auth/AuthViewModel.kt:32-53`.

```kotlin
init {
    viewModelScope.launch {
        combine(
            authSessionRepository.accounts,
            authSessionRepository.activeAccountId,
        ) { ... }.collect { (accounts, active) ->
            mutableState.update { ... }
        }
    }
    ...
}
```

**Problem:** `accounts` reads Room; `activeAccountId` reads DataStore. Neither flow catches
`SQLiteException`, `IllegalStateException` (Room schema mismatch) or `IOException`/
`CorruptionException` (DataStore). An exception in `viewModelScope` reaches the default
`CoroutineExceptionHandler` and **crashes the process** on the main thread.

**Trigger conditions:** corrupt database, schema identity mismatch after an upgrade, corrupt
DataStore file, storage full, revoked file permissions.

**Impact:** launch-time crash with a stack trace pointing at Room/DataStore, not at auth. This is
the second most plausible "immediate crash" after BUG-001, but it requires a damaged data
directory; a fresh install would not hit it.

**Fix direction:** `.catch { emit(emptyList()) }` / map errors into `AuthUiState.errorMessage`;
never let storage exceptions escape a `viewModelScope` collector.

---

## 4. BUG-012 — `requireNotNull(state.activeAccount)` is a latent invariant crash (P2, Suspected)

**File:** `app/src/main/java/com/nexora/git/ui/NexoraGitApp.kt:162`.

```kotlin
private fun AuthenticatedNexoraGitApp(state: AuthUiState, ...) {
    val activeAccount = requireNotNull(state.activeAccount)
```

**Problem:** `AuthenticatedNexoraGitApp` is only reached when `state.signedIn` is true, and
`signedIn` is defined as `activeAccount != null` (`AuthUiState.kt:15-16`). The assertion is
therefore logically redundant in a single immutable state snapshot. It becomes a crash only if a
future refactor decouples `signedIn` from `activeAccount`, or if a `StateFlow` emission is observed
with inconsistent fields.

**Impact:** no crash on the current code path; a fragile invariant that should be modelled as a
non-null state object instead of an assertion.

**Fix direction:** split the UI state into `SignedOut` / `SignedIn(account)` sealed states, or
return early when the account is null.

---

## 5. BUG-013 — Per-app language API used without the AppCompat metadata service (P2, Confirmed)

**Files:** `app/src/main/java/com/nexora/git/MainActivity.kt:69-87`,
`app/src/main/AndroidManifest.xml` (no `AppLocalesMetadataHolderService`),
`app/src/main/res/xml/locales_config.xml`.

```kotlin
if (AppCompatDelegate.getApplicationLocales() != locales) {
    AppCompatDelegate.setApplicationLocales(locales)
}
```

**Problem:** AppCompat's pre-API-33 per-app language persistence uses
`AppLocalesMetadataHolderService` with the `autoStoreLocales` metadata. The manifest declares
neither. AppCompat's own documentation (source Javadoc around
`AppCompatDelegate.setApplicationLocales`, `AppCompatDelegate.java:741-766`) requires this service
for persistence. Behavior varies by API level:

- API 33+: framework `LocaleManager` handles it; `android:localeConfig` is present.
- API 26–32: the call is applied in-process but the requested locale may not survive process death,
  and the app can recreate the Activity when the locale is applied.

**Impact:** language selection may not persist on Android 8–12, and the Activity can be recreated
during startup when a stored non-system language is applied. The missing declaration is
**Confirmed (static)**; the persistence failure is API-dependent, and no crash was proven.

**Fix direction:** declare the metadata service, or implement per-app language only on API 33+ and
hide the setting below that.

---

## 6. BUG-014 — Global refresh mutex serializes network I/O across all accounts (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/core/auth/AuthSessionRepository.kt:28,92-110`.

```kotlin
private val refreshMutex = Mutex()

suspend fun getValidAccessToken(accountId: Long): AuthResult<String> =
    refreshMutex.withLock {
        ...
        refreshAccountLocked(account)   // performs broker network call
    }
```

**Problem:** the mutex is intended to prevent double refresh for one account, but it is a single
repository-wide lock. A slow or hanging broker refresh for account A blocks token access for
account B and for every Git operation waiting on credentials.

**Impact:** multi-account users can see long stalls in Git operations when one account's refresh is
slow. No timeout is set on the broker client beyond OkHttp defaults (10 s connect/read in
`AuthBrokerClient`'s own client).

**Fix direction:** per-account mutex (`Map<Long, Mutex>`), or refresh outside the lock with a
double-check.

---

## 7. BUG-015 — Token persistence uses `SharedPreferences.apply()` (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/core/auth/SecureAuthStorage.kt:25,46-53,75-90`.

`putEncrypted()` writes with `.apply()`, which is asynchronous. `completeAuthorizationCode` then
upserts the Room account. If the process is killed between the in-memory `apply()` and the disk
flush, Room contains an account but the encrypted token is missing. The user appears signed in but
every Git operation fails with `SECURE_STORAGE_FAILED`.

**Impact:** rare post-login inconsistency, especially during immediate force-stop or low-memory
kill.

**Fix direction:** use `commit()` for token writes, or persist a single atomic record in Room/
DataStore; reconcile missing tokens at startup by signing the account out.

---

## 8. BUG-016 — Keystore key creation is not synchronized (P2, Suspected)

**File:** `app/src/main/java/com/nexora/git/core/auth/KeystoreCipher.kt:47-72`.

`getOrCreateKey()` does `getKey` then `generateKey` with no lock. Concurrent first-use calls (for
example token read during a Git operation and token write during refresh) can both observe a
missing key and race. Android Keystore generally serializes key generation, but the loser can throw
`KeyStoreException`/`ProviderException`, which surfaces as a decrypt failure.

**Impact:** rare authentication failure under concurrency; `SecureAuthStorage.getDecrypted` deletes
the unreadable token on failure.

**Fix direction:** synchronize key creation or perform it once at app start.

---

## 9. BUG-017 — Token write and account upsert are not atomic (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/core/auth/AuthSessionRepository.kt:232-246`.

```kotlin
secureAuthStorage.saveTokenBundle(identity.id, tokenBundle)
authAccountDao.upsert(entity)
settingsRepository.setActiveAccountId(identity.id)
```

If the Room upsert fails, the catch block deletes the token. If the process dies between token save
and upsert, the token remains orphaned. If the active-account write fails, the account exists but
no account is active.

**Impact:** inconsistent multi-store state after crashes; user may need to sign in again.

**Fix direction:** write the account row first with a `pending` flag, then token, then mark active;
or use a single encrypted store as the source of truth.

---

## 10. BUG-018 — Retrofit has no converter and the only API is dead (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/core/network/NetworkModule.kt:46-59`,
`app/src/main/java/com/nexora/git/core/network/GitHubApi.kt`.

```kotlin
Retrofit.Builder()
    .baseUrl(GitHubApiConfig.REST_BASE_URL)
    .client(client)
    .build()          // no converter factory
```

```kotlin
interface GitHubApi {
    @GET("rate_limit")
    suspend fun getRateLimit(): Response<ResponseBody>
}
```

**Problem:** no `GsonConverterFactory`/`MoshiConverterFactory`/`KotlinSerializationConverterFactory`
is installed. The single endpoint returns `ResponseBody`, so it *could* work today, but it is not
injected anywhere. Any future JSON-returning method would fail at runtime with
`IllegalArgumentException: Unable to create converter`. The Retrofit dependency is currently dead
weight.

**Impact:** no runtime failure today; a trap for the next developer, and unnecessary APK size.

**Fix direction:** delete `GitHubApi`/Retrofit until a typed API is actually used, or add a converter
and real endpoints.

---

## 11. BUG-019 — Home screen performs serial Git I/O per workspace (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/feature/home/HomeViewModel.kt:55-143`.

`mapLatest` iterates `workspaces.take(MAX_RECENT_WORKSPACES)` (capped at **3**,
`HomeViewModel.kt:146`) and calls `gitEngine.status()`, `branches()` and `divergence()`
**sequentially**. Each call is a JNI round-trip on `Dispatchers.IO`, so opening Home performs up to
~9 native calls (including a full working-tree `status()` scan per workspace) before the list
settles, and re-runs them on every workspace/repository flow emission.

**Impact:** slow Home rendering, battery/IO churn, and repeated work on every workspace/repository
flow emission.

**Fix direction:** parallelize with `coroutineScope { map { async { ... } } }`, cache status, and
refresh only on repository changes.

---

## 12. BUG-020 — GitHub response cache bounds entries, not body size (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/core/platform/GitHubResponseCache.kt:22-73`.

`MAX_ENTRIES = 128` but each `GitHubCachedResponse.body` is an arbitrary `String` (raw GitHub JSON,
possibly hundreds of KB). 128 large responses can hold tens of MB for the process lifetime.

**Impact:** memory pressure on low-RAM devices (the user's target class of device), possible
`OutOfMemoryError` during heavy browsing.

**Fix direction:** bound total bytes, not only entries; drop bodies above a threshold; use an
on-disk cache for large payloads.

---

## 13. BUG-021 — Editor recomputes syntax over a full copy of the text (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/feature/editor/MobileEditorViewModel.kt:722-770`.

`syntaxJob?.cancel()` is correct, and analysis runs on `Dispatchers.Default`
(`TreeSitterSyntaxEngine.kt:23`). However every keystroke passes a full `String` copy of the file
(up to 512 KiB) and the entire `TextFieldValue` is replaced in a `MutableStateFlow`. On a low-end
device this causes jank and GC churn even though the debounce limits native calls.

**Impact:** typing latency in large files; not a crash.

**Fix direction:** debounce already exists; additionally cap syntax analysis to a window around the
viewport or to files below a smaller size, and use `TextFieldState` (Compose 1.7+) to avoid full
string copies.

---

## 14. BUG-022 — Theme casts `view.context` to `Activity` (P2, Confirmed)

**File:** `app/src/main/java/com/nexora/git/ui/theme/Theme.kt:73-81`.

```kotlin
SideEffect {
    val window = (view.context as Activity).window
    ...
}
```

The root host is `MainActivity`, so this works today. It throws `ClassCastException` if the theme
is ever hosted by a `ContextThemeWrapper`, a dialog window, a `ComposeView` inside a different
context, or a test that uses `createComposeRule()` with a non-Activity host. Compose previews are
guarded by `view.isInEditMode`.

**Impact:** latent crash when the theme is reused outside the Activity; a trap for HMX UI work.

**Fix direction:** walk the context chain to the first `Activity`, or use
`LocalActivity.current`/`WindowInsetsControllerCompat` from the Activity.

---

## 15. BUG-029 — Custom Tab launch has no `ActivityNotFoundException` handling (P3, Suspected)

**File:** `app/src/main/java/com/nexora/git/ui/NexoraGitApp.kt:75-84`.

```kotlin
LaunchedEffect(state.authorizationUri) {
    val uri = state.authorizationUri ?: return@LaunchedEffect
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    authViewModel.authorizationUriConsumed()
}
```

On a device with no browser/Custom Tabs provider, `launchUrl` throws `ActivityNotFoundException`.
This happens on tapping "Continue with GitHub", not at app launch, and would crash the app.

**Fix direction:** `runCatching { ... }` and surface `AuthFailure.NO_BROWSER`.

---

## 16. BUG-036 — README capability claims exceed verified behavior (P3, Confirmed documentation risk)

`README.md` advertises a broad feature set (clone/fetch/push/merge/rebase/cherry-pick/stash/LFS,
GitHub Issues/PRs/Actions/Releases, editor) while:

- zero releases exist,
- the upstream gate issue #57 is explicitly **NO-GO**,
- there are no product screenshots,
- no instrumentation tests are executed in CI.

This is not a code defect, but it is a **due-diligence risk** for anyone adopting the project based
on the README alone. HMX Git documentation must not repeat these claims before device QA.

---

## 17. Positive patterns worth preserving

| Pattern | File | Why it matters |
| --- | --- | --- |
| Atomic editor save + external-change check | `EditorFileStore.kt:53-110` | Prevents silent overwrite and partial files |
| 512 KiB edit cap / 2 MiB browse cap | `EditorFileStore.kt:113`, `CodeBrowserFileSystem.kt:321` | Bounds memory on mobile |
| Canonical path containment | `WorkspacePathPolicy.kt:10-71` | Blocks traversal and `.git` access |
| Search limits + cancellation | `ProjectSearchEngine.kt:28-120` | Bounded CPU/IO |
| PKCE S256 + constant-time state compare + 10-min window | `PkceGenerator.kt`, `PendingAuthorizationValidator.kt` | Correct OAuth hygiene |
| GitHub-only credential policy | `GitRemoteSecurityPolicy.kt:10-21` | Prevents token exfiltration to arbitrary remotes |
| Logging redaction | `NetworkModule.kt:28-30` | Authorization/Cookie never logged |
| No `GlobalScope`, no `!!`, no TODO | grep | Clean baseline |

---

## 18. Kotlin/Java conclusion

The Kotlin layer is **above average for a young project** and does not contain the startup crash.
The crash is caused by the resource/manifest theme interaction (report 14).

The most important Kotlin-side risks for HMX Git are:

1. **BUG-011** (uncaught storage exceptions in startup collectors) — the only other plausible
   launch-crash path.
2. **BUG-014** (global refresh lock) and **BUG-017** (non-atomic auth state) — multi-account
   reliability.
3. **BUG-019/020/021** — performance on low-RAM devices.
4. **BUG-002** — theme setting does not control the platform theme, which keeps BUG-001 alive.

No Kotlin or Java change was made.
