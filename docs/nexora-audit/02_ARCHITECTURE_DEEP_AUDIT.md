# 02 — Architecture Deep Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Method:** static tracing of manifest → Application → Activity → Compose → Hilt → Room → native.

---

## 1. Architectural summary

Nexora Git is a **single-module, layered-by-package** Android app:

```
┌──────────────────────────────────────────────────────────────┐
│ UI            Compose screens (feature/*), navigation, theme │
├──────────────────────────────────────────────────────────────┤
│ Presentation  Hilt ViewModels, UI state, one-shot events     │
├──────────────────────────────────────────────────────────────┤
│ Domain/core   auth, git, repository, storage, editor,         │
│               platform (GitHub), search, work                │
├──────────────────────────────────────────────────────────────┤
│ Data          Room v4, DataStore, SharedPreferences, files    │
├──────────────────────────────────────────────────────────────┤
│ Native        libgit2 1.9.7 + Mbed TLS + Tree-sitter via JNI  │
├──────────────────────────────────────────────────────────────┤
│ External      GitHub REST/GraphQL, self-hosted Go auth broker │
└──────────────────────────────────────────────────────────────┘
```

Strengths:

- Clear package intent: `core/*` has no Compose imports; `feature/*` holds screens + view models.
- Constructor injection throughout; no service locator, no `GlobalScope`.
- Native boundary is a single bridge class per concern (`NativeGitBridge`, `NativeSyntaxBridge`,
  `SecureRuntimeConfigNative`).
- All long-running native work is wrapped in `withContext(Dispatchers.IO)` by
  `Libgit2GitEngine.native` (`Libgit2GitEngine.kt:659-664`).

Weaknesses:

- One Gradle module for 208 main files and 46.7k LOC. No feature modules, no API boundaries, no
  enforced dependency direction.
- `core/git` depends on `core/auth` (credential provider), while `core/auth` depends on
  `core/database` and `core/settings`. This is acceptable at package level but makes the Git engine
  impossible to reuse without the auth stack.
- Several feature files exceed 900 lines / 100 KB, mixing UI, state derivation and formatting.
- Business rules sometimes live in the UI layer (e.g., `GitWorkflowPolicy` under `feature/git`, not
  `core/git`), so direct engine callers can bypass validation.

---

## 2. Application startup path (exact)

### 2.1 Process creation

1. Android creates the process and instantiates `com.nexora.git.NexoraGitApplication`
   (`AndroidManifest.xml:7`).
2. `@HiltAndroidApp` (`NexoraGitApplication.kt:6`) generates the Hilt application component. The
   class itself has **no `onCreate` override** and performs no eager work.
3. WorkManager's `androidx.startup` provider initializes automatically (no custom
   `Configuration.Provider` in the Application). This is library initialization, not app code.

**Conclusion:** there is no application-level code that can crash before the Activity.

### 2.2 Activity creation

`MainActivity.kt`:

```kotlin
@AndroidEntryPoint                                  // line 25
class MainActivity : AppCompatActivity() {          // line 26
    @Inject lateinit var authCallbackBus: AuthCallbackBus       // 28-29
    @Inject lateinit var settingsRepository: SettingsRepository // 31-32

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)       // 35
        enableEdgeToEdge()                       // 36
        observeApplicationLanguage()             // 38
        setContent { ... }                       // 40-58
        intent?.data?.let(authCallbackBus::dispatch) // 60
    }
}
```

Order of operations:

| Step | Code | Failure modes |
| --- | --- | --- |
| 1 | `AppCompatActivity.onCreate` | **Theme validation / delegate setup** — the P0 defect surfaces here or at `setContentView` |
| 2 | `enableEdgeToEdge()` | Rare window/insets exceptions on exotic OEMs |
| 3 | `observeApplicationLanguage()` | Starts a `lifecycleScope` collector; `AppCompatDelegate.setApplicationLocales` may recreate the Activity |
| 4 | `setContent {}` | Creates Compose view, triggers AppCompat `setContentView` → **theme check** |
| 5 | Theme/DataStore collection | DataStore read; initial values are supplied, so first frame renders |
| 6 | `NexoraGitApp()` | Hilt ViewModels → Room/DataStore collection |
| 7 | `intent.data` dispatch | Buffered `Channel`, no loss |

Because step 4 passes through `AppCompatDelegateImpl.ensureSubDecor()`, a non-AppCompat theme
throws **before any Compose content is composed**. This is why the crash appears "immediately" and
why no Compose error handler can catch it.

### 2.3 Compose root

`NexoraGitApp()` (`ui/NexoraGitApp.kt:69-150`):

1. Creates `AuthViewModel` and `OnboardingViewModel` via `hiltViewModel()`.
2. Observes `AuthUiState` and `OnboardingUiState` with `collectAsStateWithLifecycle`.
3. Opens a Custom Tab when `state.authorizationUri` becomes non-null.
4. Branches:
   - `state.loading` → spinner
   - `!state.signedIn` → `LoginScreen`
   - `!onboardingState.loaded` → spinner
   - `!onboardingState.completed` → `OnboardingScreen`
   - else → `AuthenticatedNexoraGitApp` (bottom bar + `NavHost`)

`AuthenticatedNexoraGitApp` asserts `requireNotNull(state.activeAccount)` at line 162. Because
`AuthUiState.signedIn` is defined as `activeAccount != null`, this assertion is logically
redundant; it is a latent invariant failure, not a normal path.

### 2.4 Authentication path at startup

`AuthViewModel` construction (`feature/auth/AuthViewModel.kt:18-30`):

- Injects `AuthSessionRepository` and `AuthCallbackBus`.
- Calls `authSessionRepository.isConfigured()` **synchronously** in the property initializer.
- `AuthConfig` (`core/auth/AuthConfig.kt:7-26`) calls `SecureRuntimeConfigNative.read()` in its
  constructor.
- `SecureRuntimeConfigNative` loads `libnexoragit.so` inside `runCatching` and falls back to an
  empty config if the library or JNI call fails (`SecureRuntimeConfigNative.kt:10-34`).

**Therefore:** missing broker/client configuration does **not** crash startup. It sets
`configured = false`, and `LoginScreen` disables the sign-in button.

`AuthViewModel.init` launches two collectors in `viewModelScope`:

- `combine(authAccountDao.observeAll(), settingsRepository.activeAccountId)` → Room + DataStore.
- `callbackBus.callbacks` → OAuth callback processing.

Neither collector has an exception handler. A Room open failure, a corrupted DataStore file or a
schema mismatch would propagate to the coroutine's default handler and terminate the process
(report 04).

### 2.5 Post-login path

After sign-in and onboarding, `HomeViewModel` is created. It injects:

- `WorkspaceRegistry` → Room DAO, SAF permission manager, scanners, sync engine.
- `RepositoryGateway` → GitHub repository cache.
- `GitEngine` → bound to `Libgit2GitEngine` → `NativeGitBridge`.

`Libgit2GitEngine` construction is lazy; the native library `libnexoragit_native.so` is loaded when
`NativeGitBridge` is first initialized (class initialization), not at Application start.

`HomeViewModel.state` combines workspace and repository flows, then calls `gitEngine.status()` and
`divergence()` **sequentially per workspace** inside `mapLatest` (`HomeViewModel.kt:78-140`). Each
call is dispatched to IO, but the sequential loop makes home loading O(workspaces) round-trips.

---

## 3. Dependency-injection graph

12 `@Module` classes (all `@InstallIn(SingletonComponent::class)` except the two editor modules):

| Module | Provides / binds |
| --- | --- |
| `DatabaseModule` | `NexoraDatabase` (Room v4), 3 DAOs |
| `NetworkModule` | `HttpLoggingInterceptor`, `OkHttpClient`, `Retrofit`, unused `GitHubApi` |
| `GitEngineModule` | `GitEngine` → `Libgit2GitEngine`, `GitCredentialProvider` → GitHub provider |
| `RepositoryGatewayModule` | Repository gateway/cache |
| `IssueGatewayModule` | Issues REST client |
| `PullRequestGatewayModule` | PR REST client |
| `ReleasesGatewayModule` | Releases REST client |
| `GitHubActionsGatewayModule` | Actions REST client |
| `AdvancedGitHubGatewayModule` | Advanced GitHub surface |
| `SocialGatewayModule` | Social graph |
| `EditorIntelligenceModule` | Formatter, syntax engine, file store |
| `LanguageServerModule` | LSP interface/registry (no transport) |

Scopes:

- All provided dependencies are `@Singleton`, including `GitEngine`, `GitCredentialProvider`,
  repositories and caches.
- ViewModels are `@HiltViewModel` with no custom scope.
- No `@ActivityScoped`/`@FragmentScoped` services; no Fragment usage at all.

Circular dependencies: none reported by compilation. `GitHubGitCredentialProvider` →
`AuthSessionRepository` → `AuthBrokerClient`/`GitHubIdentityClient`; `Libgit2GitEngine` →
`GitCredentialProvider`. The direction is acyclic.

Risk: **singleton Git engine with no repository lock.** A single engine instance is shared by UI
and WorkManager. Singleton-ness does not serialize calls.

---

## 4. Navigation architecture

`NexoraDestination` defines 6 top-level routes: Home, Explore, Repositories, Activity, Profile,
Settings (Settings hidden from bottom bar).

`AuthenticatedNexoraGitApp` builds a `NavHost` with:

| Route | Screen | Arguments |
| --- | --- | --- |
| `home` | Home | — |
| `explore` | Explore | — |
| `repositories` | Repositories | — |
| `git/{workspaceId}` | Git workspace | workspace ID |
| `code/{workspaceId}` | Code browser | workspace ID |
| `editor/{workspaceId}?path={path}` | Editor | workspace ID + URI-encoded path |
| `repository/{owner}/{name}` | Repository detail | owner/name |
| `issues/{owner}/{name}` | Issues | owner/name |
| `pulls/{owner}/{name}` | Pull requests | owner/name |
| `actions/{owner}/{name}` | Actions | owner/name |
| `advanced/{owner}/{name}` | Advanced GitHub | owner/name |
| `releases/{owner}/{name}` | Releases | owner/name |
| `profile` | Profile | — |
| `settings` | Settings | — |

Observations:

- Deep links from GitHub repo URLs are **not** registered in the manifest; only the OAuth callback
  app link exists.
- Routes are built by string concatenation. Owner/repo names cannot contain `/` and workspace IDs
  are UUIDs, so traversal is not possible; the editor path is URI-encoded. This is acceptable but
  not enforced by a typed route layer.
- The start destination depends on a pending onboarding action (Repositories instead of Home), while
  the bottom bar `popUpTo(graph.findStartDestination())` always references the graph's start
  destination. This is legal Navigation behavior but makes state restoration harder to reason about.

---

## 5. Data flow

### 5.1 Accounts

```
Room auth_accounts ──observeAll()──┐
                                   ├─ combine ─→ AuthUiState ─→ Compose
DataStore active_account_id ───────┘
```

`AuthSessionRepository.completeAuthorizationCode` writes the encrypted token bundle first, then
upserts the Room account, then sets the active account ID. On failure after the token write it
deletes the tokens; the ordering is defensible but not transactional across three stores.

### 5.2 Git operations

```
UI / WorkManager
      ↓
GitEngine (interface)
      ↓
Libgit2GitEngine.native { }  → withContext(IO)
      ↓
NativeGitBridge.ensureInitialized()
      ↓
JNI (git_jni.cpp) → libgit2 → filesystem / HTTPS
```

Credentials are resolved **per remote** by `GitHubGitCredentialProvider`; the native callback only
uses them when the host is `github.com`.

### 5.3 Editor

```
CodeBrowserFileSystem (2 MiB display cap)
      ↓
EditorFileStore (512 KiB edit cap, AtomicFile write, external-change check)
      ↓
MobileEditorViewModel (TextFieldValue + undo/redo + syntax job)
      ↓
TreeSitterSyntaxEngine → NativeSyntaxBridge → libnexoragit_native.so
```

The editor is one of the better-isolated subsystems.

---

## 6. Architectural risks relevant to HMX Git

| Risk | Why it matters | Evidence |
| --- | --- | --- |
| **No repository operation serialization** | Concurrent WorkManager + UI Git calls on one repo can corrupt the index | no `Mutex`/lock in `core/git` or `core/work`; `DurableGitOperations` only serializes its own unique work |
| **Single module** | Fork/rebrand touches 208 files; no compile-time boundary prevents feature coupling | `settings.gradle.kts` includes only `:app`, `:baselineprofile` |
| **UI-owned policy** | Direct engine callers can bypass branch/remote validation | `GitWorkflowPolicy` lives in `feature/git` |
| **Singleton engine without progress** | Long clone/fetch has no cancel, no progress, no timeout | `remote_callbacks` sets credentials only (`git_core.cpp:180-185`) |
| **Auth coupled to Git** | Replacing GitHub auth for HMX requires touching `core/git` credential provider | `GitHubGitCredentialProvider` binds `GitCredentialProvider` |
| **No device-facing tests** | Startup/theme regressions are invisible to CI | 24 instrumentation files compiled, not run |
| **Monolith screens** | Rebranding/HMX UI work will be expensive | 101 KB `GitWorkspaceScreen.kt` |

---

## 7. Architecture verdict

The architecture is **coherent for a v1 prototype** and significantly better than a typical
"Compose + Retrofit" demo: the native boundary is clean, DI is consistent, and storage/security
concerns are separated.

It is **not yet an HMX-grade architecture**:

- The startup path contains a fatal configuration defect (P0).
- The Git engine lacks the concurrency and cancellation primitives a production Git client needs.
- Feature code is too large and too coupled to be rebranded safely without a modularization step.

None of these is a reason to reject the codebase; all are reasons to gate the fork on a Phase 0.
See `16_FIX_PRIORITY_AND_RECOMMENDATIONS.md`.
