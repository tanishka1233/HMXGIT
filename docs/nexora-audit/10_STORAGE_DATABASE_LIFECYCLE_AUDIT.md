# 10 — Storage, Database and Lifecycle Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** Room v4, DataStore, SharedPreferences, SAF workspaces, WorkManager, process lifecycle.
> **Method:** static schema reading, migration review, flow tracing. No database was opened.

---

## 1. Persistence map

| Store | Technology | Data | Backup |
| --- | --- | --- | --- |
| `nexora-git.db` | Room **v4** | workspaces, auth accounts, cached GitHub repositories | excluded |
| `nexora_settings` | Preferences DataStore | onboarding, active account, theme, language, editor indents, push settings | excluded |
| `nexora_git_secure_auth` | SharedPreferences + Keystore AES-GCM | tokens, pending OAuth authorization | excluded |
| Workspace directories | `filesDir` / SAF tree | cloned or imported repositories | excluded |
| `cache/code-share` | File cache | shared code snippets via FileProvider | n/a |
| Memory caches | `GitHubResponseCache`, `GitHubGraphQlCache` | API responses | n/a |

Backup is fully disabled in both `backup_rules.xml` and `data_extraction_rules.xml`, consistent
with `allowBackup=false`. **Correct for a token-bearing app.**

---

## 2. Room database

### 2.1 Schema

`NexoraDatabase` (`core/database/NexoraDatabase.kt`):

- **version 4**
- `exportSchema = true`
- Three entities: `WorkspaceEntity`, `AuthAccountEntity`, `GitHubRepositoryEntity`
- Three DAOs

| Entity | Primary key | Notes |
| --- | --- | --- |
| `workspaces` | `id: String` (UUID) | Strategy (MANAGED/DIRECT), sync state, scan counters, warnings |
| `auth_accounts` | `accountId: Long` | Token expiry timestamps only; tokens are **not** in Room |
| `github_repositories` | `(accountId, repositoryId)` plus unique `(accountId, fullName)` | Per-account repository cache |

Storing tokens outside Room is correct: Room files are unencrypted.

### 2.2 Migrations

Three explicit migrations:

| Migration | Operation |
| --- | --- |
| `1 → 2` | Create `auth_accounts` |
| `2 → 3` | Add 13 workspace columns with defaults |
| `3 → 4` | Create `github_repositories` + unique index |

The migrations are hand-written and match the entities (column names and types checked). `2 → 3`
uses `NOT NULL DEFAULT` for every new non-null column, which is required by SQLite. Good.

### 2.3 Findings

**BUG-006 (HIGH, Confirmed) — no destructive fallback and no committed schema.**

`DatabaseModule.kt:20-30`:

```kotlin
Room.databaseBuilder(context, NexoraDatabase::class.java, "nexora-git.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
```

There is no `.fallbackToDestructiveMigration()` and no `.fallbackToDestructiveMigrationOnDowngrade()`.
Consequences:

1. **Downgrade** (install an older build over a newer one, e.g. switching from a beta to an older
   release) throws `IllegalStateException: Database has been downgraded` on first access.
2. **Unknown forward version** (installing a newer build first, then an older one) fails the same way.
3. A migration bug or a partially applied migration leaves the app permanently unable to open the
   database, with no recovery path short of clearing app data.
4. `exportSchema = true` but **`app/schemas` is not committed** (`find app -path '*schemas*'` is
   empty). The exported schema that Room generates in the build directory is not in source control,
   so migration tests and schema diffs cannot be run against the repository.

This is a real launch-crash class: if the Room open triggers during `AuthViewModel` construction
(which collects `authAccountDao.observeAll()` immediately), the exception surfaces on the main
thread and kills the process. See **BUG-011** in report 04 for the missing collector exception
boundary that turns this into a crash rather than an error screen.

**Severity rationale:** HIGH, because factory-reset/clear-data is the only recovery and the failure
is a startup crash on upgrade.

### 2.4 DAO review

- `AuthAccountDao.observeAll()` returns `Flow<List<AuthAccountEntity>>`, mapped to summaries in the
  repository.
- All DAO queries use bound parameters; no string concatenation. **No SQL injection.**
- No `@Transaction` on the multi-write auth sequence; that sequence spans Room *and* prefs, so a
  Room transaction alone would not make it atomic (BUG-017).

---

## 3. DataStore

`SettingsRepository` uses a single `preferencesDataStore(name = "nexora_settings")` delegate
(`SettingsRepository.kt:16`). Keys: onboarding, active account, editor indent, theme mode, dynamic
color, app language, confirm force push, Wi-Fi-only transfers.

### 3.1 Findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-011 | P2 | Confirmed | Startup collectors do not catch DataStore `CorruptionException`/`IOException` |
| BUG-002 | P0 | Confirmed | `themeMode` is read but never applied to the platform night mode |
| — | INFO | Confirmed | `resetProductPreferences()` removes its six keys inside a single atomic `edit {}`; no partial-failure defect |

DataStore corruption is rare but real: an interrupted write, a full disk or a killed process can
produce a corrupt preferences file. Without a `.catch { emit(emptyPreferences()) }` in the repository
flow or a `ReplaceFileCorruptionHandler`, the app crashes on first read. `SettingsRepository` was not
found to install either. This is folded into BUG-011.

**Positive:** the theme/language flows use `map` with `runCatching` around enum parsing, so an
unknown stored string falls back to a default instead of throwing. Verify in
`SettingsRepository.kt:56-66`.

---

## 4. Workspaces and SAF

`core/storage` (12 files) implements two workspace strategies:

| Strategy | Behaviour |
| --- | --- |
| `MANAGED` | Clone into app-controlled storage; full Git operations |
| `DIRECT` | Import a user-selected SAF tree; read-only or mirrored depending on permission |

Supporting components:

- `SafPermissionManager` — persists and re-checks `takePersistableUriPermission`.
- `SafProjectScanner`, `DirectProjectScanner` — bounded directory walks.
- `SafWorkspaceSyncEngine` — mirrors changes to/from the SAF tree.
- `WorkspaceManifestWriter` — writes a manifest into the workspace.
- `ProjectRiskDetector` — flags secrets/large files (unit-tested).
- `GitIgnoreMatcher` — unit-tested.
- `WorkspaceStrategyResolver` — decides MANAGED vs DIRECT.

### 4.1 Findings

- **Permission loss** is handled by re-checking persisted grants, but there is no periodic
  verification. A revoked SAF grant surfaces as an IO error at operation time.
- **Mirroring is not proven for every Git call.** If a `DIRECT` workspace is read via
  `CodeBrowserFileSystem` from the SAF tree but Git operations run against a mirror, the two views
  can diverge. The audit could not confirm the mirror is always used (report 18, Unverified).
- **No storage quota checks.** A clone into a nearly-full device is not pre-flighted; libgit2 fails
  with an IO error mid-clone, leaving a partial directory. `RepositoryCloneWorker` does not appear
  to clean up on failure (not verified line-by-line).
- **Workspace deletion** removes the Room row; whether it deletes files recursively was not proven.
  A destructive-delete confirmation exists in the UI (spot-checked), but the engine path is not
  centralised.

### 4.2 Registered finding

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-037 | P3 | Confirmed | No cleanup of partial clone directories after an aborted or failed clone |

---

## 5. WorkManager and durable operations

`core/work`:

- `DurableGitOperations` — `enqueueFetch`, `enqueuePull`, `enqueuePush` with unique work names.
- `DurableGitWorker` — `CoroutineWorker` that resolves dependencies through a Hilt
  `EntryPointAccessors` entry point (because Hilt does not inject Workers without
  `@HiltWorker`/`HiltWorkerFactory`). This works but bypasses the standard Hilt Worker integration.
- `RepositoryCloneWorker` — clone in the background.

### 5.1 Findings

**BUG-004 (HIGH, Confirmed).** WorkManager unique-work names serialize *durable* jobs with the same
name, but a foreground `fetch`/`commit` from `GitWorkspaceViewModel` uses the same singleton engine
and is not excluded. There is no per-repository mutex.

**Worker retry semantics.** `DurableGitWorker.doWork()` returns `Result.failure(...)` for invalid
input and `Result.retry()`/`Result.failure()` for Git errors (spot-checked). A transient network
error should be `retry` with backoff, not `failure`; a permanent auth error should be `failure`, not
`retry`. The mapping was not exhaustively verified.

**No foreground notification** is visible in the worker code. WorkManager's default
`CoroutineWorker` runs without a foreground service; a long clone can be killed by the system when
the app is backgrounded. For a mobile Git client, clone/fetch should use a foreground worker with a
progress notification. Registered as a **capability gap (P1-adjacent)**, tracked with BUG-005.

---

## 6. Process death and lifecycle

| Scenario | Behaviour |
| --- | --- |
| Activity recreated (rotation, locale change) | Compose state restored via Navigation/`rememberSaveable`; ViewModels survive config changes |
| Process death in background | Navigation back stack restored by `NavHost` saved state; open editor text is **not** persisted (no draft autosave) |
| OAuth callback after process death | `AuthCallbackBus` is a singleton in a new process; `onCreate` dispatches the intent, so the callback is processed |
| OAuth callback while Activity alive | **No `onNewIntent` override** → callback may be dropped (BUG-025) |
| Locale change | `setApplicationLocales` can recreate the Activity; with the night theme defect this is another path into BUG-001 |
| Low-memory kill during native Git call | Operation aborted; libgit2 may leave `.git/index.lock` behind, blocking the next operation until manually removed |

**Editor draft loss** is a real UX gap: `MobileEditorViewModel` holds unsaved text only in memory.
A process death loses edits with no recovery. P2 capability note.

**Leftover index.lock** after a crash is a classic libgit2 problem. The app does not detect or offer
to clear a stale lock. P2 capability note for HMX Git.

---

## 7. Storage findings summary

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-006 | HIGH | Confirmed | Room v4: no destructive fallback, no committed schema |
| BUG-011 | P2 | Confirmed | Startup storage collectors have no exception boundary |
| BUG-015 | P2 | Confirmed | Async token persistence |
| BUG-017 | P2 | Confirmed | Non-atomic auth state |
| BUG-037 | P3 | Confirmed | No clone cleanup/quota pre-flight |
| — | P2 | Confirmed | Editor drafts not persisted across process death |
| — | P2 | Confirmed | No stale `index.lock` recovery |
| — | P2 | Confirmed | No foreground notification for long background clones |

---

## 8. Storage verdict

The storage design is **sensible**:

- tokens isolated from Room and encrypted with Keystore;
- per-account repository cache keyed correctly;
- explicit migrations for every version bump;
- backups fully disabled;
- clear separation between managed and SAF-backed workspaces.

It is **not resilient**:

- no destructive fallback or committed schema (**BUG-006**) — an upgrade/downgrade or migration bug
  is a startup crash with no recovery;
- startup collectors convert storage errors into process death (**BUG-011**);
- auth state spans three stores without transactions (**BUG-015/017**);
- long background clones have no foreground service or progress.

For HMX Git, the minimum bar is: commit `app/schemas`, add `fallbackToDestructiveMigration` for
non-critical tables (or a version-gated recovery screen), add `.catch` to every startup flow, and add
a foreground clone worker.
