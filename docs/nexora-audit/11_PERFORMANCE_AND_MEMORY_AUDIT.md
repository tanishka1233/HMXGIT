# 11 — Performance and Memory Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** startup path, Compose recomposition, Git I/O, caches, editor, search, memory.
> **Method:** static cost analysis. **No benchmark, profiler, macrobenchmark run or on-device
> measurement was performed**, so all statements are complexity/ownership analysis, not measured
> numbers.

---

## 1. Startup cost

### 1.1 Cold start sequence

| Step | Work | Cost class |
| --- | --- | --- |
| 1 | Process + Zygote fork | OS |
| 2 | `NexoraGitApplication` (`@HiltAndroidApp`) | Hilt component init, no eager work |
| 3 | AndroidX Startup providers (WorkManager) | Library init |
| 4 | `MainActivity.onCreate` → `AppCompatActivity` | AppCompat delegate + **theme validation** |
| 5 | `enableEdgeToEdge` | Cheap |
| 6 | `observeApplicationLanguage` → `setApplicationLocales` if changed | **Can recreate the Activity** |
| 7 | `setContent` → Compose host | Compose init, first composition |
| 8 | `NexoraGitApp` → `hiltViewModel` Auth + Onboarding | Hilt graph, DataStore reads |
| 9 | `AuthViewModel.init` → Room + DataStore collectors | First Room **open** (disk + migrations) |
| 10 | `AuthConfig` construction | `System.loadLibrary` + JNI read |
| 11 | First frame | Compose |

### 1.2 Findings

**A. Activity recreation on locale change (in startup path).**
`MainActivity.observeApplicationLanguage()` applies `AppCompatDelegate.setApplicationLocales`
whenever the stored locale differs from the current one. On a fresh process the current locale is the
system default, so a user who selected Spanish (for example) triggers a locale application **during
onCreate**, which recreates the Activity. That doubles startup work and, with the night-theme defect,
adds a second path into the crash.

**B. Room open is on the startup critical path.**
`AuthViewModel` starts collecting `authAccountDao.observeAll()` immediately. Room opens the database,
runs `onOpen`/migrations and reads on a background dispatcher, but the first emission gates the
`state.loading` → `Login`/`Home` transition. On a large workspace table this is a real cost.
It is also where BUG-006/BUG-011 can crash startup.

**C. DataStore first read.**
Preferences DataStore performs file I/O on first read on each process. Four flows are collected
(`accounts`, `activeAccountId`, `onboardingCompleted`, plus `productPreferences` later). Each
`SettingsRepository` property maps the same underlying `dataStore.data` flow, so they share one read,
which is efficient. Good.

**D. Native library load.**
`libnexoragit.so` loads when `AuthConfig` is constructed (startup). `libnexoragit_native.so` loads
only when `NativeGitBridge`/`NativeSyntaxBridge` is first touched (lazy). This is a sensible split:
the Git engine is not paid for on the login screen.

**E. Baseline Profile.**
`baselineprofile/PerformanceTests.kt` defines `BaselineProfileGenerator` and `StartupBenchmark`.
The wiring exists: `app/build.gradle.kts:6` applies `libs.plugins.baselineprofile` and
`app/build.gradle.kts:211` consumes `"baselineProfile"(project(":baselineprofile"))`, so AGP will
consume a generated profile for release builds. The **generation** step, however, is only executed by
`performance.yml` on manual dispatch / `v*-rc.*` tags, and no generated profile is committed, so a
release built without that workflow uses no measurement-backed profile. Optimisation is therefore
*wired but unmeasured*.

**F. Startup UI gate chain.**
Four sequential gates (loading → signedIn → onboarding loaded → completed) mean a returning user sees
a spinner until Room **and** DataStore both emit. Acceptable, but a splash screen with cached state
would be faster.

---

## 2. Compose and recomposition

| Concern | Finding |
| --- | --- |
| State reads | Mostly `collectAsStateWithLifecycle`, good |
| Large composables | `GitWorkspaceScreen` 100,982 B; six screens > 28 KB |
| Unstable parameters | List items built from Room entities mapped to data classes; several are not annotated `@Immutable` |
| `derivedStateOf` usage | Not systematically used for filtered lists |
| `key` in lazy lists | Present in the main lists (spot-checked) |
| Paging | **Not used** (`grep Paging` empty); lists are fully materialised in memory |
| Image loading | No Coil/Glide; avatars are likely loaded manually or as text. Large avatar lists are a risk |

**No Paging 3** means issue/PR/commit lists accumulate entire pages in memory. For repositories with
thousands of issues this is a memory and jank risk on low-RAM devices.

**Monolith screens** (`GitWorkspaceScreen`, `PullRequestDetailScreen`, `AdvancedGitHubScreen`,
`MobileEditorScreen`, `RepositoriesScreen`, `RepositoryDetailScreen`) each hold many composables in
one file. This does not by itself cause recomposition, but it makes accidental unstable captures and
whole-file reads inevitable.

**BUG-022** (`view.context as Activity` inside a `SideEffect` in the theme) runs on every
recomposition of the theme's `SideEffect` — cheap, but it also means the theme cannot be previewed or
tested outside an Activity host.

---

## 3. Git I/O performance

### 3.1 Home screen — serial native calls (BUG-019, P2, Confirmed)

`HomeViewModel.kt:55-143`:

```kotlin
// HomeViewModel.kt:55-63
for (workspace in workspaces.take(MAX_RECENT_WORKSPACES)) {   // MAX = 3
    add(workspaceSummary(workspace))                          // suspend, sequential
}
```

`workspaceSummary` performs, per workspace:

- `File(root, ".git").isDirectory`
- `gitEngine.status()` (full working-tree scan)
- `gitEngine.branches()`
- `gitEngine.divergence()`

Each `GitEngine` call is `withContext(Dispatchers.IO)` and a JNI round-trip. The loop is capped at
`MAX_RECENT_WORKSPACES = 3` (`HomeViewModel.kt:146`), so the worst case is ~9 sequential native
calls, including a full working-tree `status()` scan per workspace. Every emission of either source
flow re-runs all of them.

Impact: Home can take seconds on a device with 10 large repositories; battery and I/O churn.

Fix direction (not applied): parallel `async` per workspace, cache per workspace keyed by HEAD/mtime,
or push status refresh to a background worker.

### 3.2 `status()` has no pathspec

`nativeStatus` builds a full `git_status_list`. On a repository with a large working tree (e.g.
`node_modules` not ignored) this is expensive. No `GIT_STATUS_OPT_*` tuning was found for
`RECURSE_UNTRACKED_DIRS` or ignore rules beyond libgit2 defaults. P2.

### 3.3 `history()` default limit

`history(limit = 50)` — good default. No pagination beyond that in the engine; the UI likely re-queries
with a larger limit. Fine for v1.

### 3.4 Diff size is unbounded

`nativeDiff` returns the entire patch as a JSON string. A large binary-ish change or a huge
refactor can produce a multi-megabyte string that is then parsed with `org.json` into Kotlin objects.
There is **no diff size cap**. This is a real OOM/jank risk on a phone. P2.

### 3.5 Clone/fetch/push

- No shallow/partial clone (report 06 §5.5).
- No progress callbacks (BUG-005), so the UI cannot show bytes/objects.
- No cancellation, so a wrong clone cannot be stopped without killing the app.
- No foreground service, so backgrounding a clone risks process death.

### 3.6 No per-repository lock (BUG-004)

Concurrency is a correctness issue first (report 06) and a performance issue second: `index.lock`
contention causes retries or failures.

---

## 4. Caches and memory

### 4.1 `GitHubResponseCache` (BUG-020, P2, Confirmed)

```kotlin
private val entries = object : LinkedHashMap<String, GitHubCachedResponse>(MAX_ENTRIES, 0.75f, true) {
    override fun removeEldestEntry(...) = size > MAX_ENTRIES
}
```

- Bounded by **entry count** (128), not bytes.
- Each entry holds `body: String?` — a full JSON payload, potentially hundreds of KB.
- Worst case: 128 × 500 KB ≈ **64 MB** retained for the process lifetime.
- No TTL eviction sweep; `isFresh` is checked on read, but stale entries still occupy the map.

`GitHubGraphQlCache` has the same design with a 64-entry bound.

**On the user's target device class (low-RAM Android), this is a plausible `OutOfMemoryError`
source during heavy browsing.**

### 4.2 Other memory holders

| Holder | Bounded? |
| --- | --- |
| Editor text (512 KiB cap) | ✅ |
| Code browser text (2 MiB cap) | ✅ |
| Syntax snapshot | ✅ per analysis, replaced |
| `ProjectSearchEngine` matches (500) + preview (256 chars) | ✅ |
| Issue/PR/commit lists | ❌ no Paging |
| Diff patch string | ❌ unbounded |
| Avatar images | ❌ no image library |
| Room cursors | Room-managed |
| Native Git index/mmap | libgit2-managed; freed per call |

### 4.3 `ProjectSearchEngine` limits (positive)

`MAX_PATTERN_LENGTH = 256`, `MAX_FILES = 5_000`, `MAX_MATCHES = 500`,
`MAX_FILE_BYTES = 1 MiB`, skipped directories, `ensureActive()` for cancellation. This is a
well-bounded search. **Good example of how the rest of the engine should be bounded.**

---

## 5. Editor performance (BUG-021, P2, Confirmed)

- `onValueChange` stores the full `TextFieldValue` in a `MutableStateFlow`.
- Every change schedules syntax analysis with a debounce; cancelled jobs are discarded.
- Analysis copies the full text string (up to 512 KiB) and calls into Tree-sitter on
  `Dispatchers.Default`.
- `TextFieldValue` holds selection + composition + text; replacing it on each keystroke is standard
  but expensive at large sizes.

Mitigations already present: debounce, cancellation, 512 KiB cap, `Dispatchers.Default`.

Remaining risk: typing latency in a 500 KiB file on a low-end device, GC churn, and a possible
one-frame lag between text and highlight.

---

## 6. Network performance

| Concern | Finding |
| --- | --- |
| Connection reuse | OkHttp shared client for API; **separate** client for `AuthBrokerClient` (no pooling with API — acceptable) |
| HTTP/2 | OkHttp default |
| GraphQL batching | Not used; multiple REST calls per screen (e.g., repository detail likely issues N calls) |
| Caching | In-memory only; no HTTP disk cache configured (`Cache` not installed on OkHttp) |
| Rate-limit handling | Present, unit-tested |
| Compression | OkHttp default `Accept-Encoding` |
| Avatar loading | No image loader; repeated downloads possible |

**No OkHttp disk cache** means every process restart re-downloads API responses. Combined with the
in-memory-only cache, this is wasteful on metered connections. P2.

**Serial per-screen API calls.** Repository detail, PR detail and issue detail typically fetch the
entity plus comments/checks/files in separate calls. Where those are sequential, screen load is
N round-trips. The audit did not exhaustively verify each ViewModel; report 18 records this as
partially verified.

---

## 7. Performance findings summary

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-005 | HIGH | Confirmed | No progress/cancel/timeout on transfers → perceived hang |
| BUG-019 | P2 | Confirmed | Serial Git I/O per workspace on Home |
| BUG-020 | P2 | Confirmed | Response caches bounded by entries, not bytes |
| BUG-021 | P2 | Confirmed | Full-text syntax analysis per keystroke |
| BUG-004 | HIGH | Confirmed | No repository lock → contention and retries |
| — | P2 | Confirmed | Unbounded diff size |
| — | P2 | Confirmed | No Paging 3 for long lists |
| — | P2 | Confirmed | No OkHttp disk cache |
| — | P2 | Confirmed | No foreground service for long clones |
| — | P3 | Confirmed | Baseline Profile is wired but only generated in manual/tag workflows |
| — | P3 | Confirmed | Activity recreation on locale change during startup |

---

## 8. Performance verdict

Nexora Git is **not obviously slow on a small dataset**, but it has several unbounded or
serial-by-design paths that will hurt on the low-RAM devices HMX Git targets:

1. **Memory**: entry-bounded caches with whole-body strings, unbounded diffs, no Paging.
2. **I/O**: sequential native status/divergence calls on Home, full-tree status, full clones.
3. **Responsiveness**: no transfer progress or cancellation, so long operations look hung.
4. **Startup**: Room + DataStore + possible locale recreation on the critical path.

None of this is measured — there is no local or device benchmark in this audit. The project applies
the Baseline Profile plugin and consumes the `:baselineprofile` module, defines macrobenchmarks, and
ships `performance.yml`, but profile generation is manual/tag-only and no release has ever been
measured.

For HMX Git, the performance baseline should be re-established after the P0 theme fix, on the target
device class, with `StartupTimingMetric` and a memory profile of a 128-entry API cache.
