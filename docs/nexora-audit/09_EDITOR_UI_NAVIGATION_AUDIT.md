# 09 — Editor, UI and Navigation Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** 18 feature packages, 21 screen composables, `ui/` root, 15 `core/editor` files,
> `core/files`, `core/search`.
> **Method:** static reading, route tracing, feature-completeness comparison against declared UI.
> No UI was rendered; no screenshot or emulator was used.

---

## 1. Screen inventory

21 screen composables across 18 feature packages:

| Feature | Screens | Lines | Notes |
| --- | --- | ---: | --- |
| actions | `ActionsScreen`, `WorkflowRunDetailScreen` | large | Workflow dispatch dialog, job log dialog |
| activity | `ActivityScreen` | — | GitHub notifications/events |
| advanced | `AdvancedGitHubScreen` | 39,549 B | Account/repo advanced surface |
| auth | `LoginScreen` | — | Configured/not-configured states, Custom Tab |
| editor | `MobileEditorScreen` | 38,926 B / 1,174 lines | Code editor |
| explore | `ExploreScreen` | — | Repository search |
| files | `CodeBrowserScreen` | 28,808 B | Tree, file preview, markdown |
| git | `GitWorkspaceScreen` | **100,982 B** | The monolith: status, diff, branches, commits, push/pull, conflicts |
| home | `HomeScreen` | — | Recent workspaces + repositories |
| issues | `IssuesScreen`, `IssueDetailScreen` | 28,971 B detail | |
| onboarding | `OnboardingScreen` | — | First-run flow |
| profile | `ProfileScreen` | 29,537 B | |
| pulls | `PullRequestsScreen`, `PullRequestDetailScreen` | 48,925 B detail | |
| releases | `ReleasesScreen`, `ReleaseDetailScreen` | 35,631 B detail | |
| repositories | `RepositoriesScreen`, `RepositoryDetailScreen` | 38,759 / 28,388 B | |
| settings | `SettingsScreen` | — | Theme, language, editor, privacy |
| social | (no dedicated screen) | — | gateway only |

`ui/components/FoundationScreen.kt` exists but is **never referenced** — dead code.

**Verdict:** the screen surface is broad and covers the GitHub client feature set. The problem is
not missing screens; it is **screen size and state ownership**.

---

## 2. Navigation

### 2.1 Graph

`NexoraDestination` (6 bottom-bar destinations) + `NavHost`:

| Route | Screen |
| --- | --- |
| `home` | Home |
| `explore` | Explore |
| `repositories` | Repositories |
| `activity` | Activity |
| `profile` | Profile |
| `settings` | Settings (hidden from bottom bar) |
| `git/{workspaceId}` | Git workspace |
| `code/{workspaceId}` | Code browser |
| `editor/{workspaceId}?path={path}` | Editor |
| `repository/{owner}/{name}` | Repository detail |
| `issues/{owner}/{name}` | Issues |
| `pulls/{owner}/{name}` | Pull requests |
| `actions/{owner}/{name}` | Actions |
| `advanced/{owner}/{name}` | Advanced GitHub |
| `releases/{owner}/{name}` | Releases |

### 2.2 Findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-024 | P3 | Confirmed | Navigation uses raw string routes; no typed routes/arguments validation |
| BUG-012 | P2 | Suspected | `requireNotNull(state.activeAccount)` in the authenticated host |
| BUG-022 | P2 | Confirmed | `NexoraGitTheme` casts `view.context as Activity` |
| BUG-029 | P3 | Suspected | Custom Tab launch has no `ActivityNotFoundException` handling |
| — | P3 | Confirmed | Editor path argument is URI-encoded but not length-bounded |
| — | P3 | Confirmed | Bottom-bar `popUpTo` uses graph start destination while start can be recalculated for pending onboarding action |

Not found:

- No deep links for GitHub repository URLs (only the OAuth app link). A user tapping a GitHub link
  opens the browser, not the app. For an "advanced" client this is a **capability gap (P2)**.
- No predictive-back customisation.
- No state restoration beyond Compose `rememberSaveable` / Navigation saved state.

### 2.3 Startup interaction with navigation

At cold start `NexoraGitApp` chooses between:

```
loading → spinner
!signedIn → LoginScreen
!onboarding.loaded → spinner
!onboarding.completed → OnboardingScreen
else → AuthenticatedNexoraGitApp
```

This is a clean gating chain. **However**, all four branches are reached only after
`MainActivity.setContent` succeeds. Because `setContent` calls `AppCompatDelegateImpl.ensureSubDecor`
and the night theme is not AppCompat-compatible, the process dies **before** the `loading` spinner is
drawn. That is why the user perceives an instant crash with no UI. See report 14.

---

## 3. Editor audit

### 3.1 Architecture

```
MobileEditorScreen (1,174 lines)
   ↓ hiltViewModel
MobileEditorViewModel (845 lines)
   ├── EditorHistory (undo/redo)
   ├── EditorTextOperations (indent, replace, search)
   ├── EditorFormatter
   ├── EditorFileStore (AtomicFile, 512 KiB cap, mtime check)
   ├── TreeSitterSyntaxEngine → NativeSyntaxBridge
   └── LSP registry (empty)
```

### 3.2 Implemented features

| Feature | Status | Evidence |
| --- | --- | --- |
| Open / edit / save | ✅ | `EditorFileStore.load/save` |
| Atomic save | ✅ | `AtomicFile` |
| External-change detection | ✅ | `lastModifiedEpochMillis` comparison |
| File size limit (512 KiB) | ✅ | `EditorFileStore` |
| Truncated-file rejection | ✅ | read cap |
| Undo / redo | ✅ | `EditorHistory` |
| Search / replace / replace-all | ✅ | `EditorTextOperations` |
| Match case | ✅ | `setMatchCase` |
| Indent style (spaces/tabs, width) | ✅ | `EditorIndentStyle` |
| Formatter | ✅ | `EditorFormatter` |
| Diff view | ✅ | `loadDiff` / `dismissDiff` |
| Commit from editor | ✅ | `prepareCommit` / `commit` |
| Syntax highlighting | ✅ | Tree-sitter, Kotlin/Java/JSON/Python/JS/TS/TSX |
| Multi-cursor | ❌ | Not found |
| LSP (diagnostics, completion, rename) | ❌ | Interface only, no implementation |
| Word wrap / line numbers | ⚠️ | Verify in screen; no VM support found |

### 3.3 Findings

**BUG-021 (P2, Confirmed) — full-text copy per keystroke.** `onValueChange` copies the whole
`TextFieldValue` into a `MutableStateFlow`; `scheduleSyntaxAnalysis` passes a full `String` copy of
the file (up to 512 KiB) to the native analyzer. Debounce (`SYNTAX_DEBOUNCE_MILLIS`) and
`Dispatchers.Default` limit the native calls, but typing on a low-end device still allocates heavily.

**LSP is a placeholder.** `LanguageServerClient` defines `openDocument`, `changeDocument`,
`formatting`, `closeDocument`, `stop`; `LanguageServerModule` declares an empty `@Multibinds` set.
There is **no implementation** and no transport. The README's "language intelligence" claim is
therefore aspirational. Registered as a **capability gap (P2)**, not a bug, because no UI promises
it at runtime.

**No editor crash path was found** for a normal file. `EditorFileStore.load` wraps read in
`runCatching`, validates size and binary-ness, and rejects control-character-heavy files.

**Path handling** uses `WorkspacePathPolicy` + `CodeBrowserFileSystem`, both canonicalising. Good.

### 3.4 Syntax engine

- `TreeSitterSyntaxEngine` selects a grammar by file extension/language
  (`TreeSitterGrammarSelectionTest.kt` covers selection).
- `syntax_jni.cpp` reads UTF-16 with `GetStringChars` and handles surrogate pairs correctly — the
  **correct** counterpart to the Git JNI's modified-UTF-8 defect (BUG-039).
- `ts_parser_delete` and `ts_tree_delete` are paired (`syntax_engine.cpp:463-621`). No leak found.
- Analysis runs on `Dispatchers.Default`; cancellation via `syntaxJob?.cancel()`.

---

## 4. Code browser / file preview

`CodeBrowserScreen` + `CodeBrowserFileSystem`:

- 2 MiB text display cap (`MAX_TEXT_BYTES`), binary detection via control-character ratio
  (`MAX_CONTROL_RATIO = 0.10`).
- Markdown rendering is a **custom block parser** (`CodeBrowserRenderers.kt:286+`), not a full
  CommonMark engine. It handles headings, bullets, quotes, code fences and links. Raw HTML is not
  rendered, which is a **security positive** (no WebView, no XSS surface).
- No image viewer for binary blobs beyond icons; no PDF/video.
- No syntax highlighting in the preview-only mode (highlighting is editor-only).

Verdict: functional and safe; presentation-limited.

---

## 5. UI quality and maintainability

| Observation | Evidence | Impact |
| --- | --- | --- |
| `GitWorkspaceScreen.kt` is 100,982 bytes | largest file | Recomposition scope, merge conflicts, rebrand cost |
| 6 screens > 28 KB | file sizes | Hard to review, high cognitive load |
| `NexoraGitTheme` casts context to `Activity` | `Theme.kt:73-81` | Latent `ClassCastException` (BUG-022) |
| Dynamic color supported | `Theme.kt` | Good |
| AMOLED scheme | `Theme.kt` | Good, but see BUG-002 |
| Only `SYSTEM`/`ENGLISH`/`SPANISH` | `AppPreferences.kt` | Narrow localisation |
| `Typography()` re-instantiated | `Type.kt:9-24` | Minor |
| No screenshots in repo | `find` | Documentation gap |

### 5.1 The theme defect in context

`Theme.kt` correctly selects Compose color schemes from `AppThemeMode`. The defect is that
`MainActivity` is an `AppCompatActivity` and the **platform** theme is decided by Android resource
qualifiers (`values-night`), not by the Compose setting. This mismatch is the root of BUG-001 and
BUG-002.

Two coherent fixes exist (not applied):

1. Make `Theme.NexoraGit` AppCompat-compatible in **both** `values/` and `values-night/`
   (e.g. `Theme.AppCompat.DayNight.NoActionBar` everywhere), and call
   `AppCompatDelegate.setDefaultNightMode` from the theme setting.
2. Drop `AppCompatActivity` for `ComponentActivity` and a Compose `MaterialTheme` window, removing
   the AppCompat theme requirement entirely. Locale APIs would then need `LocaleManager` (API 33+)
   or the AndroidX locale library.

---

## 6. Accessibility and localisation

- `contentDescription` usage exists in icon composables (spot-checked).
- `supportsRtl="true"` in the manifest.
- Locale config lists `en` and `es-MX`.
- `AppCompatDelegate.setApplicationLocales` is called without the metadata service (BUG-013), so
  per-app language may not persist on API 26–32.
- No automated accessibility tests. The `performance.yml` workflow runs macrobenchmarks, not
  accessibility checks.

Verdict: baseline accessibility is present; it is untested.

---

## 7. UI/navigation findings summary

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-001 | CRITICAL | Confirmed | Night theme kills the Activity before any UI renders |
| BUG-002 | CRITICAL | Confirmed | Theme setting never changes the platform night mode |
| BUG-012 | P2 | Suspected | `requireNotNull(activeAccount)` in authenticated host |
| BUG-021 | P2 | Confirmed | Full-text syntax recomputation per keystroke |
| BUG-022 | P2 | Confirmed | Theme casts context to `Activity` |
| BUG-024 | P3 | Confirmed | Untyped string navigation routes |
| BUG-029 | P3 | Suspected | Custom Tab launch not guarded |
| — | P2 | Confirmed | LSP is declared but unimplemented |
| — | P2 | Confirmed | No GitHub deep links |
| — | P3 | Confirmed | `FoundationScreen` is dead code |

---

## 8. UI verdict

The UI is **broad and visually complete** for a v1 GitHub client, with a genuinely usable editor and
a safe custom Markdown renderer. It is **not maintainable at scale**:

- the Git workspace screen is a 101 KB monolith;
- theme handling is split between Compose and AppCompat with a fatal mismatch;
- LSP is declared but absent;
- navigation is stringly-typed;
- there are no UI tests running on a device in CI.

The crash is entirely explained by the theme mismatch, not by Compose content. Any fork must fix the
theme before touching UI features, otherwise the first launch in dark mode fails regardless of what
the screens contain.
