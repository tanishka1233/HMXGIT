# 11 — HMX Git Feature Comparison

**Legend (evidence classification):**
- **VERIFIED** — implemented, traced in source
- **PARTIALLY VERIFIED** — code exists, end-to-end behaviour unproven
- **DOCUMENTED ONLY** — claimed but not implemented
- **NOT FOUND** — absent after search
- **FAILED** — present but broken
- **UNKNOWN** — insufficient evidence

**Candidates:** Nexora Git (`f4befb6`), Gitling (`3905914`, v1.0.66), PuppyGit (`00657bd`).

---

## 1. Git operations

| # | Feature | Nexora | Gitling | PuppyGit | Source evidence | Testing evidence | Work required (from best base) | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Clone | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `git_core.cpp:856` `git_clone`; G: `CloneTask.java`; P: `Libgit2Helper.kt` | N: `native_smoke.cpp` remote_workflow; G: none; P: none | Add progress + cancel (N) | MEDIUM |
| 2 | Fetch | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `git_remote_fetch:1593`; G: `FetchTask.java` | N: native | progress (N) | MEDIUM |
| 3 | Pull | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `pull:1605`, `pull_with_strategy:1620`; G: `PullTask.java` | N: native | — | MEDIUM |
| 4 | Push | **VERIFIED** | **PARTIALLY VERIFIED** | **VERIFIED** | N: `push:2132`, ff/force-w-lease; G: `PushTask.java` (non-FF bug); P: libgit2 | N: native | G: fix non-FF; N: progress | MEDIUM |
| 5 | Commit | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `commit:1195`; G: `CommitChangesTask.java` | N: native | — | LOW |
| 6 | Stage | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `git_index_add_all`; G: `AddToStageTask.java` | N: native | — | LOW |
| 7 | Unstage | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `git_reset_default`; G: `RemoveFromStageTask`, `UnstageAllTask` | N: native | — | LOW |
| 8 | Branch create | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `advanced_git.cpp`; G: `NewBranchAction.java` | N: native | — | LOW |
| 9 | Branch delete | **VERIFIED** | **PARTIALLY VERIFIED** | **VERIFIED** | N: `git_branch_delete`; G: JGit in UI | N: native | confirm UX | LOW |
| 10 | Checkout | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `GIT_CHECKOUT_SAFE`; G: `CheckoutTask/CheckoutFileTask` | N: native | — | MEDIUM |
| 11 | Merge | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `merge:2455`, `merge_annotated:451`; G: `MergeTask.java` | N: native | — | HIGH |
| 12 | Rebase | **VERIFIED** | **PARTIALLY VERIFIED** | **VERIFIED** | N: `git_rebase_init:1779`, `advance_rebase:668`, continue/abort; G: `RebaseTask.java` (no continue/abort) | N: native | G: add recovery | HIGH |
| 13 | Cherry-pick | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `advanced_git.cpp:766` + continue/abort; G: `CherryPickAction/Task` | N: native | — | MEDIUM |
| 14 | Revert | **VERIFIED** | **NOT FOUND** (no task; JGit capability unused) | **VERIFIED** | N: `advanced_git.cpp:1107` + continue/abort | N: native | G: build | MEDIUM |
| 15 | Reset | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `reset_to:1041`; G: `ResetCommitTask.java` | N: native | confirmation UX | HIGH |
| 16 | Stash | **VERIFIED** | **PARTIALLY VERIFIED** (console only) | **VERIFIED** | N: `stashes:929`, `save_stash:961`, apply/drop; G: `GitCommandEngine` stash | N: native | G: dedicated UI | MEDIUM |
| 17 | Tags | **VERIFIED** | **PARTIALLY VERIFIED** (JGit API in chooser) | **VERIFIED** | N: `tags:1267`, create/delete; G: `TagCommand` | N: native | — | LOW |
| 18 | Remotes | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: add/rename/remove; G: `AddRemoteAction/RemoveRemoteAction` | N: native | — | LOW |
| 19 | Commit history | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: revwalk; G: `GetCommitGraphTask`; P: lib | N: native | — | LOW |
| 20 | Commit graph | **PARTIALLY VERIFIED** | **VERIFIED** | **VERIFIED** | N: revwalk + UI; G: **lazygit-style lane graph** (UI) | G: none | N: polish; G: reusable UI | MEDIUM |
| 21 | Diff | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `diff:2348`; G: `CommitDiffTask`; P: `DiffScreen` | N: native | — | LOW |
| 22 | Blame | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `blame:2885` `git_blame_file:2901`; G: `GetBlameTask` | N: native | — | LOW |
| 23 | Merge conflict detection | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `repository_has_conflicts:225`; G: JGit status | N: native | — | LOW |
| 24 | Conflict resolution | **PARTIALLY VERIFIED** (ours/theirs only) | **PARTIALLY VERIFIED** (file ours/theirs) | **VERIFIED** (hunks) | N: `resolve_conflict:2516`; G: checkout file; P: hunk resolver | N: untested path | **Build 3-way/hunk resolver** | **HIGH** |
| 25 | Native Git (no API-upload fake) | **VERIFIED** | **VERIFIED** | **VERIFIED** | all use real engines | N: native suite | — | LOW |

---

## 2. GitHub integration

| # | Feature | Nexora | Gitling | PuppyGit | Source evidence | Testing | Work required | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 26 | Authentication | **VERIFIED** (GitHub App OAuth+PKCE) | **VERIFIED** (Device Flow) | **NOT FOUND** (remote creds only) | N: `core/auth/*`; G: `GitHubAuthManager.kt` | N: auth unit tests; G: none | N: broker or switch to device flow | HIGH |
| 27 | Multiple accounts | **VERIFIED** | **VERIFIED** | **NOT FOUND** | N: `AuthAccountEntity`; G: `AccountManager` | N: partial | — | LOW |
| 28 | Repository management | **VERIFIED** | **PARTIALLY VERIFIED** (local only) | **NOT FOUND** | N: `GitHubRepositoryGateway` | N: parser tests | — | LOW |
| 29 | File browsing (remote) | **PARTIALLY VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: repository/file gateway | N: parser tests | verify e2e | MEDIUM |
| 30 | File upload/download | **PARTIALLY VERIFIED** (contents API) | **NOT FOUND** | **NOT FOUND** | N: gateway | none | verify | MEDIUM |
| 31 | Issues | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/issues` + parser test | N: `IssueJsonParserTest` | — | LOW |
| 32 | Pull requests | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/pulls` + `PullRequestDetailScreen` | N: `PullRequestJsonParserTest` | — | LOW |
| 33 | Reviews | **PARTIALLY VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: pulls gateway | none | verify submit e2e | MEDIUM |
| 34 | Comments | **PARTIALLY VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: issues/pulls gateways | none | verify | MEDIUM |
| 35 | Releases (browse/manage) | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/releases` + `ReleaseDetailScreen` | N: `ReleasesJsonParserTest` | — | LOW |
| 36 | Release assets | **PARTIALLY VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: releases gateway | none | verify upload/download | MEDIUM |
| 37 | GitHub Actions | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/actions` + `/actions/runs` | N: `GitHubActionsJsonParserTest` | — | LOW |
| 38 | Workflow logs | **VERIFIED** (request builders) | **NOT FOUND** | **NOT FOUND** | N: `GitHubActionsLogGateway` (`/actions/jobs/{id}/logs`) | none | verify streaming/redirect | MEDIUM |
| 39 | Notifications | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/social` | N: `SocialJsonParserTest` | — | LOW |
| 40 | Organizations | **PARTIALLY VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: explore/social gateways | none | verify | MEDIUM |
| 41 | GraphQL | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `GitHubGraphQlClient.kt` (10 ops) | N: `GitHubGraphQlErrorClassifierTest` | — | LOW |
| 42 | REST pagination/ETag/rate-limit | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: `core/platform/*` | N: link-header, rate-limit tests | — | LOW |

> **Aggregate:** Nexora is the **only** candidate with a GitHub platform; Gitling and PuppyGit have
> **no** GitHub features beyond (Gitling) sign-in.

---

## 3. Developer tools

| # | Feature | Nexora | Gitling | PuppyGit | Source evidence | Testing | Work required | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 43 | Built-in code editor | **VERIFIED** | **NOT FOUND** (external app) | **PARTIALLY VERIFIED** (basic) | N: `MobileEditorScreen`; G: `README.md:55` | N: `EditorCoreTest` | N: tabs/autosave | MEDIUM |
| 44 | Syntax highlighting | **VERIFIED** (Tree-sitter) | **PARTIALLY VERIFIED** (viewer WebView) | **PARTIALLY VERIFIED** | N: `syntax_engine.cpp` | N: `TreeSitterGrammarSelectionTest` | — | LOW |
| 45 | Multiple editor tabs | **NOT FOUND** | **NOT FOUND** | **NOT FOUND** | — | — | **Build** | MEDIUM |
| 46 | Search & replace | **VERIFIED** | **PARTIALLY VERIFIED** (viewer search) | **PARTIALLY VERIFIED** | N: VM `replaceCurrent/replaceAll` | N: editor test | — | LOW |
| 47 | Undo/redo | **VERIFIED** | **NOT FOUND** | **PARTIALLY VERIFIED** | N: `EditorHistory.kt` | N: `EditorCoreTest` | — | LOW |
| 48 | File explorer | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `CodeBrowserScreen`; G: explorer; P: `FilesInnerPage` | — | — | LOW |
| 49 | Markdown preview | **PARTIALLY VERIFIED** | **PARTIALLY VERIFIED** (WebView) | **VERIFIED** (Markwon) | N: renderers; P: Markwon deps | — | verify (N) | LOW |
| 50 | Tree-sitter | **VERIFIED** | **NOT FOUND** | **NOT FOUND** | N: native + 7 grammars | N: partial | — | LOW |
| 51 | LSP | **DOCUMENTED ONLY** | **NOT FOUND** | **NOT FOUND** | N: interface only, 0 impls | N: codec test only | **Build** | **HIGH** |
| 52 | Integrated terminal | **NOT FOUND** | **NOT FOUND** (Git console only) | **NOT FOUND** | — | — | **Build** | HIGH |
| 53 | Git command console | **NOT FOUND** (UI actions) | **VERIFIED** (`GitCommandEngine`) | **PARTIALLY VERIFIED** | G: `GitCommandEngine.kt` | none | optional | LOW |

> **Note:** A "Git command console" is **not** an integrated terminal. No candidate provides a PTY
> shell. HMX Git's "terminal where appropriate" requirement is **unmet by all**.

---

## 4. Advanced Git

| # | Feature | Nexora | Gitling | PuppyGit | Source evidence | Testing | Work required | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 54 | SSH | **NOT FOUND** (`USE_SSH OFF`) | **VERIFIED** (JSch) | **VERIFIED** (libssh2) | N: CMake; G: `ssh/*`; P: libssh2 | G: `SshCredentialsProviderTest` | **Add libssh2 to N** | **HIGH** |
| 55 | HTTPS (generic hosts) | **PARTIALLY VERIFIED** (GitHub only) | **VERIFIED** (per-host) | **VERIFIED** | N: host allow-list; G: per-host creds | — | **Generalise N policy** | HIGH |
| 56 | Git LFS | **VERIFIED** (custom transport + filter) | **NOT FOUND** | **PARTIALLY VERIFIED** (impl present, author flag `lfsTestPassed=false`) | N: `GitLfsManager`, `GitLfsTransport`, `lfs_filter.cpp`; P: `LfsUtil.kt`, `DevFlag.kt:53` | N: `GitLfsManagerTest`, `GitLfsTransportTest` | — | MEDIUM |
| 57 | Submodules | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: `submodules:1467`, init/sync/update; G: `SubmoduleUpdateTask` | N: native | — | LOW |
| 58 | Multiple remotes | **VERIFIED** | **VERIFIED** | **VERIFIED** | N: remotes API; G: add/remove | N: native | — | LOW |
| 59 | Offline Git operations | **VERIFIED** | **VERIFIED** | **VERIFIED** | local engine | N: native | — | LOW |
| 60 | Large repository support | **PARTIALLY VERIFIED** | **PARTIALLY VERIFIED** | **VERIFIED** (shallow/LFS) | N: libgit2; G: JGit + largeHeap; P: shallow | none on device | **benchmark** | MEDIUM |
| 61 | Credential isolation | **VERIFIED** (strong) | **PARTIALLY VERIFIED** (logs bug) | **PARTIALLY VERIFIED** | N: host allow-list + AES-GCM; G: logs master password | N: policy test | fix G logging | HIGH |
| 62 | RefLog / patch / squash | **NOT FOUND** | **NOT FOUND** | **VERIFIED** | P: libgit2 helper | none | optional | LOW |

---

## 5. UI / UX requirements (derived)

| Feature | Nexora | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Dashboard/home | **VERIFIED** | PARTIAL (repo list) | VERIFIED |
| Repository explorer | VERIFIED | VERIFIED | VERIFIED |
| Git changes screen | VERIFIED | VERIFIED | VERIFIED |
| Commit screen | VERIFIED | VERIFIED | VERIFIED |
| Branch management | VERIFIED | VERIFIED | VERIFIED |
| Issues screen | VERIFIED | NOT FOUND | NOT FOUND |
| Pull requests screen | VERIFIED | NOT FOUND | NOT FOUND |
| Actions screen | VERIFIED | NOT FOUND | NOT FOUND |
| Settings | VERIFIED | VERIFIED | VERIFIED |
| Loading/error/empty states | PARTIALLY VERIFIED (`AppResult`) | PARTIALLY VERIFIED | PARTIALLY VERIFIED |
| Accessibility | DOCUMENTED ONLY (`docs/ACCESSIBILITY_AUDIT.md`) | UNKNOWN | UNKNOWN |
| Tablet support | PARTIALLY VERIFIED | VERIFIED (`sw600dp`, `sw720dp-land`) | PARTIALLY VERIFIED |
| Dark/light mode | VERIFIED (`values-night`) | VERIFIED (`values-night`) | VERIFIED |
| Localization | 1 extra locale (`es-rMX`) | 12 locales | multiple (translators) |

---

## 6. Completeness summary

Counting the 62 substantive requirements above (25 Git + 17 GitHub + 11 developer + 10 advanced + UI):

| Candidate | VERIFIED | PARTIALLY VERIFIED | DOCUMENTED ONLY | NOT FOUND |
| --- | --- | --- | --- | --- |
| **Nexora Git** | **~40** | ~14 | 1 (LSP) | ~7 (SSH, generic HTTPS, tabs, terminal, refLog/patch/squash, …) |
| **Gitling** | **~22** | ~14 | 0 | ~26 (all GitHub platform, editor, Tree-sitter, LSP, terminal, LFS, revert, …) |
| **PuppyGit** | **~30** | ~10 | 0 | ~22 (all GitHub platform, Tree-sitter, LSP, terminal, tabs, …) |

> **Finding F-01** — Nexora covers the **largest share of the HMX Git scope** and is the only
> candidate that covers **both** the Git and GitHub halves. Its gaps are concentrated and known
> (transport, reliability, editor polish). **VERIFIED by matrix.** Confidence: High.

> **Finding F-02** — **No candidate** satisfies: SSH + generic HTTPS + GitHub platform + editor +
> LSP + terminal simultaneously. HMX Git must **build** SSH/generic-credential support, editor tabs/
> autosave, LSP and a terminal regardless of base.

---

## 7. Requirement gaps that are identical across all candidates

| Requirement | Status across all | Why |
| --- | --- | --- |
| Integrated terminal | **NOT FOUND** | none ships a PTY |
| Multiple editor tabs | **NOT FOUND** | none models open documents |
| LSP with a real server | **NOT FOUND** (Nexora interface only) | none spawns/manages a language server |
| Offline crash recovery for all Git ops | **NOT FOUND** | best case is WorkManager fetch/pull (Nexora) |
| Cancellation of transfers | **NOT FOUND** in Nexora; partial elsewhere | none wires `git_remote_stop`/JGit cancel to UI |
| Concurrent-operation safety | **NOT FOUND** | no repo-scoped lock anywhere |
| Device-verified 3–4 GB performance | **NOT FOUND** | no benchmarks published |

These are **HMX Git's own engineering obligations**, independent of the chosen base.
