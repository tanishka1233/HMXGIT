# 08 — Code Editor & Developer Tools Audit

**Scope:** Does each candidate contain a real editor, what class is it, and what would HMX Git have
to build?

**Classification scale used in this report:**

| Level | Name | Definition |
| --- | --- | --- |
| L1 | File viewer | Displays text; no editing |
| L2 | Text editor | Edits + saves; no syntax intelligence |
| L3 | Syntax-highlighted editor | Lexer/highlighter, search, undo |
| L4 | Tree-sitter-based editor | Structural parsing: highlights, symbols, diagnostics |
| L5 | Full language-intelligence editor | L4 + LSP (completion, hover, diagnostics, formatting) |

---

## 1. Verdict table

| Candidate | Level | Editor exists | Syntax highlighting | Tree-sitter | LSP | Tabs | Undo/redo | Search/replace | Terminal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Nexora Git** | **L4 (partial)** | Yes (Compose) | **Yes (native)** | **Yes (7 grammars)** | **Interface only (0 impls)** | **No** | **Yes** | **Yes** | No |
| **Gitling** | **L1** | **No** | Viewer-only (CodeMirror in WebView) | No | No | No | No | Viewer search | No |
| **PuppyGit** | **L2** | Yes (basic) | Sora-derived (author recommends external editors) | No (attempted, commented out) | No | No | Basic | Basic | No |

> **Finding E-01** — Only **Nexora Git** contains a genuine developer-grade editor. It is the only
> candidate that reaches L4. **VERIFIED.** Confidence: High.

---

## 2. Nexora Git editor

### 2.1 Architecture

| Component | File | LOC / size | Role |
| --- | --- | --- | --- |
| Screen | `feature/editor/MobileEditorScreen.kt` | 38,926 B | Compose UI, toolbar, dialogs, preview |
| ViewModel | `feature/editor/MobileEditorViewModel.kt` | 38,337 B | buffer state, undo, search, save, diff |
| Text operations | `core/editor/EditorTextOperations.kt` | 250 LOC | insert/delete/indent/line helpers |
| History | `core/editor/EditorHistory.kt` | 41 LOC | undo/redo stack |
| Models | `core/editor/EditorModels.kt` | 27 LOC | `EditorRevision`, `EditorSearchMatch`, `EditorDiffPreview`, `EditorIndentStyle` |
| File store | `core/editor/EditorFileStore.kt` | 115 LOC | load/save with `AtomicFile`, 512 KiB cap |
| Formatter | `core/editor/EditorFormatter.kt` | 191 LOC | formatting/indent logic |
| Syntax bridge | `core/editor/NativeSyntaxBridge.kt` | 20 LOC | JNI entry to Tree-sitter |
| Syntax engine | `core/editor/TreeSitterSyntaxEngine.kt` | 64 LOC | Kotlin wrapper |
| Syntax parser | `core/editor/SyntaxJsonParser.kt` | 97 LOC | parses native JSON spans |
| Syntax models | `core/editor/SyntaxModels.kt` | 58 LOC | span/symbol/diagnostic types |
| LSP package | `core/editor/lsp/*` | 183 LOC | interface + codec, **no implementation** |

### 2.2 Feature matrix (Nexora)

| Feature | Status | Evidence |
| --- | --- | --- |
| Text buffer | **Yes** | `EditorRevision(text, selectionStart, selectionEnd)` |
| Line numbers | **Yes** (rendered in screen) | `MobileEditorScreen.kt` gutter |
| Undo / redo | **Yes**, custom `EditorHistory` | `MobileEditorViewModel.undo()/redo()`; `EditorCoreTest` |
| Search | **Yes** (`EditorSearchMatch`) | VM search functions |
| Replace current / replace all | **Yes** | `replaceCurrent()`, `replaceAll()` at VM:232,254 |
| Syntax highlighting | **Yes**, native Tree-sitter | `syntax_engine.cpp` + `TreeSitterSyntaxEngine` |
| Languages | Kotlin, Java, JSON, Python, JavaScript, TypeScript, TSX (7) | pinned grammar SHAs in `CMakeLists.txt` |
| Symbol extraction | **Yes** | native `Symbol` structs → `SyntaxModels` |
| Diagnostics | **Yes** (structural, not semantic) | native `Diagnostic` structs |
| Formatting | **Yes** (indent/format helpers) | `EditorFormatter.kt` + `EditorFormatterTest` |
| Diff preview before save | **Yes** (`EditorDiffPreview(patch, additions, deletions)`) | VM ~:356 |
| Markdown preview | renderer present | `feature/files/CodeBrowserRenderers.kt` |
| Indent styles | Tabs / 2 spaces / 4 spaces | `EditorIndentStyle` |
| Autosave | **No** | no debounce/`snapshotFlow` in VM |
| Crash recovery | **No** | `AtomicFile` used only on explicit save |
| Multiple tabs | **No** | no tab/open-file list model |
| File size | **512 KiB hard cap** | `EditorFileStore.MAX_EDITOR_BYTES = 512L * 1024L` (`:113`) |
| Large-file streaming | **No** | whole file loaded into a String |
| Encoding detection | **Not found** | — |
| Line endings control | **Not found** | — |
| LSP | **No implementation** | `grep ": LanguageServerClient"` → 0 |
| Terminal | **No** | — |
| Tree-sitter | **Yes** | real |
| File explorer | Yes (separate `feature/files/CodeBrowserScreen`) | — |

### 2.3 Tree-sitter implementation (verified)

`native/git/src/syntax_engine.cpp`:

- Declares 7 `tree_sitter_*()` grammar entry points.
- Hard limits: `kMaxVisitedNodes = 12000`, `kMaxSpans = 5000`, `kMaxSymbols = 1000`,
  `kMaxDiagnostics = 200` — bounded work per parse (good for mobile).
- Uses a UTF-16 ↔ byte offset map (`Utf16Map`) so Compose offsets align with native byte ranges.
- Emits JSON (`Span`, `Symbol`, `Diagnostic`) parsed by `SyntaxJsonParser.kt`.

> **Finding E-02 (positive)** — The Tree-sitter integration is **real, bounded and mobile-aware**
> (node/span caps, UTF-16 mapping). This is the single hardest editor capability to build from
> scratch and Nexora already has it. **VERIFIED.** Confidence: High.

> **Finding E-03 (MEDIUM)** — Parsing is bounded but there is no incremental re-parse on every
> keystroke visible in the VM (no `snapshotFlow`/debounce). Either the screen reparses on change
> (potential jank on large files) or reparse is manual. **PARTIALLY VERIFIED.** Confidence: Medium.

### 2.4 LSP facade (important)

`core/editor/lsp/LanguageServerClient.kt` declares:

```kotlin
interface LanguageServerClient {
    val descriptor: LanguageServerDescriptor
    val state: StateFlow<LanguageServerState>
    suspend fun openDocument(...)
    suspend fun changeDocument(...)
    suspend fun formatting(...): List<LspTextEdit>
    suspend fun closeDocument(...)
    suspend fun stop()
}
```

`LspJsonRpcCodec.kt` correctly builds/parses JSON-RPC 2.0. `OptionalLanguageServerRegistry` is an
empty `@Singleton`. **No class implements `LanguageServerClient`.**

> **Finding E-04 (DOCUMENTED ONLY)** — Language Server Protocol support **does not exist**; only an
> interface and a codec do. The presence of `lsp` packages and a unit-tested codec can create the
> impression of support. HMX Git must treat LSP as **unimplemented work**. Risk: **MEDIUM**
> (expectation gap). Confidence: High.

---

## 3. Gitling editor

**There is no editor.** `README.md:55` states:

> "Gitling doesn't include an internal text editor -- to edit files, you'll need an editor app
> installed that supports opening files via Android's File Provider mechanism."

Implementation confirms it:

- `me/sheimi/sgit/activities/ViewFileActivity.kt` hosts a Compose `ViewFileScreen` plus a
  **WebView** that loads `file:///android_asset/editor.html` (CodeMirror + highlight.js).
- `onEditClick` calls `FsUtils.openFile(this, file)` — launching an **external** app via
  `ACTION_VIEW`/FileProvider.
- Language selection is applied by injecting JS:
  `CodeGuesser.wrapUrlScript("setLang('$lang')")` where
  `URL_SCRIPT_WRAPPER = "javascript:(function(){%s;})()"`.
- `copyAll()` uses `copy_all()` inside the same JS wrapper.

| Feature | Status |
| --- | --- |
| Edit + save | **No** (external app) |
| Syntax highlighting | Viewer only (CodeMirror/highlight.js) |
| Line numbers | Viewer-dependent |
| Tabs | File / Commits / Blame **tabs**, not editor tabs |
| Undo/redo | No |
| Search | Viewer search |
| Tree-sitter / LSP / terminal | No |

> **Finding E-05 (MEDIUM)** — The viewer injects JavaScript via a `javascript:` URL into a WebView
> that loads `file:///android_asset/`. The string interpolation of a language id is low-risk
> (controlled values), but the pattern is fragile and any future user-controlled value would be a
> script-injection vector. No WebView hardening settings were found in `app/src/main`.
> **PARTIALLY VERIFIED.** Confidence: Medium.

> **Finding E-06** — Gitling's editor level is **L1 (file viewer)**. For HMX Git, Gitling
> contributes **zero** editor capability. **VERIFIED.**

---

## 4. PuppyGit editor

- A basic Compose text editor exists (`fileeditor/texteditor/*`, `TextEditorState.kt` 124 KB,
  `EditorInnerPage.kt` 114 KB).
- Highlighting is **Sora-derived** but the Sora dependency is **commented out**
  (`app/build.gradle.kts:176-181`), so the shipped highlighting is a lighter in-house implementation.
- README explicitly recommends external editors ("Squircle CE", "Markor") and says the author
  "has no plan to implement a complex editor".
- No Tree-sitter, no LSP, no tabs, no terminal.

> **Finding E-07** — PuppyGit is **L2** at best; its own author disclaims it as a serious editor.
> **VERIFIED (README + commented dependencies).** Confidence: High.

---

## 5. File management and developer tools

| Capability | Nexora | Gitling | PuppyGit |
| --- | --- | --- | --- |
| File explorer | Yes (`CodeBrowserScreen`, 28 KB) | Yes (explorer + documents provider) | Yes (`FilesInnerPage`, 191 KB) |
| File create/delete/rename | Present in browser | Yes | Yes |
| Binary/image preview | Yes (`CodeBrowserRenderers` decode/sample) | Limited | Yes (Coil) |
| Markdown preview | Yes | Via WebView | Yes (Markwon) |
| Git diff in editor | Yes (`EditorDiffPreview`) | Diff screen | Yes (`DiffScreen`, 186 KB) |
| Integrated terminal | **No** | **No** (Git console only) | **No** (HTTP automation instead) |
| Git command console | Partial (UI actions) | **Yes** (`GitCommandEngine`) | Some via UI |
| Search across project | Yes (`core/search`, `ProjectSearchEngineTest`) | Yes (repo/history/file search) | Yes |
| External editor hand-off | No | **Yes** (core design) | **Yes** ("Open As") |

> **Finding E-08** — No candidate has an integrated terminal. HMX Git must build one (or embed
> Termux-style PTY) if "integrated terminal where appropriate" is a hard requirement.

---

## 6. Work required for HMX Git

### 6.1 Starting from Nexora (recommended)

| Task | Effort | Notes |
| --- | --- | --- |
| Add multiple editor tabs | **Medium** | Introduce `OpenDocument` list + tab state in VM/nav; current route is `code/{workspaceId}` single-file |
| Autosave + crash recovery | **Medium** | Debounced `snapshotFlow` → `AtomicFile`; journal unsaved buffers |
| Incremental Tree-sitter reparse | **Medium** | Add `ts_tree_edit` + debounce; caps already exist |
| Raise file cap / streaming | **Medium–High** | 512 KiB is restrictive for real code; needs chunked buffer |
| Encoding + line-ending detection | **Low–Medium** | BOM/UTF-8/UTF-16 detection; CRLF/LF toggle |
| LSP client implementation | **High** | Need process spawn (or embedded server), transport, document sync, capabilities; only codec exists |
| Integrated terminal | **High** | PTY + JNI or Termux library; sandboxing and Play-policy review |
| Snippet/autocomplete | **Medium** | Tree-sitter queries or LSP |

### 6.2 Starting from Gitling

| Task | Effort |
| --- | --- |
| Build an editor from scratch | **Very High** |
| Add Tree-sitter | High |
| Add LSP | High |
| Add tabs/undo/search | High |

### 6.3 Starting from PuppyGit

| Task | Effort |
| --- | --- |
| Harden/replace editor | **High** |
| Add Tree-sitter | High |
| Add LSP | High |

> **Finding E-09** — Nexora is **1–2 orders of magnitude closer** to a usable developer editor than
> the alternatives: it has the hard parts (Tree-sitter, native syntax, diff preview, undo, search/
> replace, formatting) and lacks the medium-effort parts (tabs, autosave, streaming, LSP).

---

## 7. Editor verdict

| Question | Answer |
| --- | --- |
| Does Gitling contain an editor? | **No** (file viewer, L1) |
| Does PuppyGit contain an editor? | Basic (L2), author-disclaimed |
| Does Nexora contain an editor? | **Yes** (L4 partial: Tree-sitter, no LSP) |
| Is any of them an IDE? | **No.** None has LSP, terminal, or tabs of a production LSP editor |
| What is the single best editor asset in the audit? | Nexora's `native/git/src/syntax_engine.cpp` + `core/editor/*` |
| What is the biggest editor risk? | LSP is a **facade**; an HMX Git "LSP" claim would be false until implemented |

**Recommendation:** adopt Nexora's editor core, then spend a dedicated work package on tabs,
autosave/recovery, streaming buffers and (optionally) a real LSP client. Do **not** describe
language intelligence as shipped until `LanguageServerClient` has an implementation and an
end-to-end test.
