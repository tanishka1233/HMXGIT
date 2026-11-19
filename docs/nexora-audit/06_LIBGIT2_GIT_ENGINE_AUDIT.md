# 06 — libgit2 Git Engine Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** `core/git` (11 Kotlin files), `core/work` (7 Kotlin files), and the full native Git engine
> (`git_core.cpp` 2,979 LOC, `advanced_git.cpp` 1,588 LOC, `lfs_filter.cpp` 363 LOC).
> **Method:** static tracing of every `GitEngine` method → JNI export → libgit2 call. No Git operation
> was executed on a device or in a local build.

---

## 1. Engine identity

| Property | Value | Evidence |
| --- | --- | --- |
| Library | libgit2 **1.9.7** | `native/git/CMakeLists.txt:12` (`49e408b3208bc3093757a1c2db938d3590f3f412`) |
| TLS | Mbed TLS (pinned `068ff08…`) | `CMakeLists.txt:13`, `USE_HTTPS mbedTLS` line 123 |
| SSH | **disabled** (`USE_SSH OFF`) | `CMakeLists.txt:120` |
| Compression | bundled zlib | `USE_BUNDLED_ZLIB ON` |
| Transport | HTTPS only | `is_github_https_url` gate in `git_core.cpp` |
| Kotlin facade | `Libgit2GitEngine` | `core/git/Libgit2GitEngine.kt` |
| Native facade | `NativeGitBridge` (50 `external fun`) | `core/git/NativeGitBridge.kt`, `native/git/src/git_jni.cpp` |
| Dispatch | `withContext(Dispatchers.IO)` for every call | `Libgit2GitEngine.kt` private `native { }` helper |
| Concurrency control | **none** | exhaustive grep for `Mutex`/`withLock`/`FileLock` in `core/git` and `core/work` returned nothing |

The engine is real, not a shell-out and not a stub. The operation surface below is implemented
against libgit2 and covered by JNI parity (report 05).

---

## 2. Operation matrix

`GitEngine` exposes **43 operations**. Each is mapped to its native entry point and libgit2 primitive.

| # | Kotlin method | JNI symbol | libgit2 primitive(s) | Notes |
| ---: | --- | --- | --- | --- |
| 1 | `version()` | `nativeVersion` | `git_libgit2_version` | Returns `X.Y.Z` |
| 2 | `init()` | `nativeInitRepository` | `git_repository_init` | Managed workspace |
| 3 | `clone()` | `nativeClone` | `git_clone` + credential callback | No progress, no cancel |
| 4 | `remoteUrl()` | `nativeRemoteUrl` | `git_remote_lookup`, `git_remote_url` | |
| 5 | `remotes()` | `nativeRemotes` | `git_remote_list` + per-remote lookup | Manual JSON assembly |
| 6 | `addRemote()` | `nativeAddRemote` | `git_remote_create` | |
| 7 | `renameRemote()` | `nativeRenameRemote` | `git_remote_rename` | `git_strarray` leak on failure (BUG-033) |
| 8 | `removeRemote()` | `nativeRemoveRemote` | `git_remote_delete` | |
| 9 | `status()` | `nativeStatus` | `git_status_list_new` | Full working-tree scan, no pathspec |
| 10 | `stage()` | `nativeStage` | `git_index_add_all` | |
| 11 | `unstage()` | `nativeUnstage` | `git_reset_default` | |
| 12 | `commit()` | `nativeCommit` | `git_index_write_tree`, `git_commit_create` | Author from Kotlin; committer defaults to author |
| 13 | `branches()` | `nativeBranches` | `git_branch_iterator` | Local + remote? See §5 |
| 14 | `setUpstream()` | `nativeSetUpstream` | `git_branch_set_upstream` | |
| 15 | `divergence()` | `nativeDivergence` | `git_graph_ahead_behind` | |
| 16 | `repositoryState()` | `nativeRepositoryState` | `git_repository_state` | Maps merge/rebase/cherry-pick/revert |
| 17 | `createBranch()` | `nativeCreateBranch` | `git_branch_create` | |
| 18 | `checkout()` | `nativeCheckout` | `git_checkout_tree` + `git_repository_set_head` | Default checkout strategy |
| 19 | `fetch()` | `nativeFetch` | `git_remote_fetch` | No prune control, no depth |
| 20 | `pull()` | `nativePull` | fetch + `MERGE`/`FF_ONLY`/`REBASE` | Strategy from Kotlin enum |
| 21 | `continueMerge()` | `nativeContinueMerge` | `git_merge` completion | |
| 22 | `continueRebase()` | `nativeContinueRebase` | `git_rebase_next`/`commit` | |
| 23 | `abortRebase()` | `nativeAbortRebase` | `git_rebase_abort` | |
| 24 | `push()` | `nativePush` | `git_remote_push` | Optional force-with-lease |
| 25 | `diff()` | `nativeDiff` | `git_diff_*_to_buffer` | ALL/STAGED/UNSTAGED |
| 26 | `merge()` | `nativeMerge` | `git_merge`, `git_merge_analysis` | Conflict-aware |
| 27 | `conflicts()` | `nativeConflicts` | `git_index_conflict_iterator` | |
| 28 | `resolveConflict()` | `nativeResolveConflict` | `git_index_add`/`git_index_conflict_remove` | OURS/THEIRS only |
| 29 | `history()` | `nativeHistory` | `git_revwalk` | Default limit 50 |
| 30 | `rebase()` | `nativeRebase` | `git_rebase_init` + loop | |
| 31 | `cherryPick()` | `nativeCherryPick` | `git_cherrypick` | |
| 32 | `continueCherryPick()` | `nativeContinueCherryPick` | sequencer continuation | |
| 33 | `abortCherryPick()` | `nativeAbortCherryPick` | `git_cherrypick_abort` | |
| 34 | `stashes()` | `nativeStashes` | `git_stash_foreach` | |
| 35 | `saveStash()` | `nativeSaveStash` | `git_stash_save` | Include-untracked flag |
| 36 | `applyStash()` | `nativeApplyStash` | `git_stash_apply` | pop variant |
| 37 | `dropStash()` | `nativeDropStash` | `git_stash_drop` | |
| 38 | `reset()` | `nativeReset` | `git_reset` | SOFT/MIXED/HARD |
| 39 | `revert()` | `nativeRevert` | `git_revert` | |
| 40 | `continueRevert()` | `nativeContinueRevert` | sequencer continuation | |
| 41 | `abortRevert()` | `nativeAbortRevert` | `git_revert_abort` | |
| 42 | `tags()` / `createTag()` / `deleteTag()` | `nativeTags`/`nativeCreateTag`/`nativeDeleteTag` | `git_tag_*` | Annotated + lightweight |
| 43 | `submodules()` / `syncSubmodule()` / `updateSubmodule()` | `nativeSubmodules`/`nativeSyncSubmodule`/`nativeUpdateSubmodule` | `git_submodule_*` | Initialize supported |
| — | `blame()` | `nativeBlame` | `git_blame_file` | Hunk-level |

**Coverage verdict:** the operation set is unusually complete for a v1 Android client. It covers
history rewriting (reset/revert/cherry-pick/rebase), stash, tags, submodules, blame and conflict
resolution — operations most mobile Git clients omit.

---

## 3. Credentials and transport isolation

### 3.1 Kotlin side

`GitHubGitCredentialProvider` (`core/git/GitCredentialProvider.kt:13-40`):

```kotlin
override suspend fun credentialsFor(remoteUrl: String): GitTransportCredentials? {
    if (!securityPolicy.allowsGitHubOAuthCredentials(remoteUrl)) return null
    return when (val result = authSessionRepository.getActiveAccessToken()) {
        is AuthResult.Success -> GitTransportCredentials(
            username = "x-access-token",
            password = result.value,
        )
        else -> null
    }
}
```

`GitRemoteSecurityPolicy` (`core/git/GitRemoteSecurityPolicy.kt:10-21`) permits credentials only when
the URL is `https://github.com/...` (scheme + host exact, no userinfo, no port confusion).

### 3.2 Native side

`git_core.cpp` `is_github_https_url()` and `credentials_callback` (lines ~120-190) independently
re-check the URL before supplying `GIT_CREDENTIAL_USERPASS_PLAINTEXT`. If the remote is not GitHub
HTTPS, the callback returns `GIT_PASSTHROUGH` and libgit2 fails the operation instead of leaking the
token.

**Verdict:** credential isolation is **defense-in-depth and correct**. This is one of the strongest
security properties of the codebase.

Residual issue: tokens are passed as immutable Java `String` and copied into native `std::string`
without zeroization (**BUG-040**), and the credential callback receives the token for the whole
duration of a clone/push.

### 3.3 LFS transport

`GitLfsManager` inspects `.gitattributes` and pointer files, and `lfs_filter.cpp` registers a native
`lfs` clean/smudge filter. The Kotlin layer also implements an authenticated basic LFS batch API for
GitHub HTTPS remotes (`GitLfsTransport.kt`, covered by `GitLfsTransportTest.kt`).

Limitations:

- LFS transfer reuses the same GitHub token; there is no separate LFS credential scope.
- No progress reporting for large LFS objects.
- No resumable transfer.

---

## 4. Force-with-lease, conflicts and destructive operations

### 4.1 Force-with-lease

`Libgit2GitEngine.push()` (`Libgit2GitEngine.kt:293-325`):

```kotlin
if (request.forceWithLease) {
    require(request.expectedRemoteOid.isNotBlank()) {
        "Force-with-lease requires the expected remote OID."
    }
    bridge.nativePushForceWithLease(..., expectedRemoteOid = ...)
} else {
    bridge.nativePush(...)
}
```

This is correct and safer than a bare force: the caller must supply the remote OID it believes is
current. The UI policy additionally asks for confirmation (`confirmForcePush` default `true`,
`core/settings/AppPreferences.kt:22`).

Gap: there is no re-validation that `expectedRemoteOid` matches the ref that was actually fetched;
a stale UI snapshot can still force-push if the user confirms. That is expected force-with-lease
semantics, not a bug.

### 4.2 Conflict resolution

`GitConflictResolution` supports only **OURS** and **THEIRS** (`GitModels.kt:94-99`). There is no
"open in editor and mark resolved" resolution at the engine level; the editor's marker detection
(`GitWorkflowPolicy.hasConflictMarkers`) is a UI-side heuristic.

Impact: a user resolving a genuine textual conflict in the editor cannot tell the index the file is
resolved through the engine API; they must use the OURS/THEIRS buttons. This is a **functional
limitation (P2)** for an "advanced" Git client, not a crash. It is captured under BUG-006-adjacent
scope rather than a separate ID to keep the register bounded; report 13 notes it as a capability
gap.

### 4.3 Destructive operations

`reset(HARD)`, `checkout` with default strategy, `dropStash`, `deleteTag`, `abortRebase`,
`abortCherryPick`, `abortRevert` are all exposed. There is no engine-level confirmation; the UI is
responsible. Because `GitWorkflowPolicy` lives in `feature/git` (not `core/git`), any future caller
that bypasses the ViewModel can perform a hard reset without policy checks.

**This is the architectural gap described in report 02 §6**: policy is not enforced at the engine
boundary.

---

## 5. Behavioral gaps found

### 5.1 No per-repository serialization — BUG-004 (HIGH, Confirmed)

- `core/git` and `core/work` contain **no** `Mutex`, `withLock`, `FileLock` or `ReentrantLock`.
  Verified by exhaustive grep.
- `Libgit2GitEngine` is a `@Singleton`. Both the UI (`GitWorkspaceViewModel`) and WorkManager
  (`DurableGitWorker`, `RepositoryCloneWorker`) obtain the same instance.
- `DurableGitOperations` uses unique WorkManager work names, which serializes **its own** enqueued
  jobs, but it does not exclude a foreground `fetch`/`push`/`commit` running at the same time.
- libgit2 is not thread-safe for concurrent writes to the same repository. Concurrent index writes
  can produce `index.lock` contention, lost updates or a corrupt index.

Trigger: open a workspace, start a fetch, background a pull via WorkManager, then commit. Probability
is low on a single-user device but non-zero, and the failure mode is data loss.

### 5.2 No progress and no cancellation — BUG-005 (HIGH, Confirmed)

`remote_callbacks()` (`git_core.cpp:180-185`):

```cpp
git_remote_callbacks remote_callbacks(const Credentials* credentials) {
    git_remote_callbacks callbacks = GIT_REMOTE_CALLBACKS_INIT;
    callbacks.credentials = credentials_callback;
    callbacks.payload = const_cast<Credentials*>(credentials);
    return callbacks;
}
```

Only the credential callback is installed. There is no:

- `transfer_progress` / `push_transfer_progress` → no bytes/objects UI;
- `sideband_progress` → no server messages;
- `certificate_check` → no custom TLS handling;
- cancellation flag read inside a callback → **cancelling the Kotlin coroutine does not abort the
  native call**.

Consequence: a large clone on a slow connection cannot be cancelled from the UI; the user must kill
the process. The UI shows an indeterminate spinner. This is a P1 usability/data-integrity issue for
a mobile Git client.

### 5.3 No timeouts

libgit2 network operations inherit the OS TCP timeouts. There is no `git_remote_connect` option, no
HTTP timeout configuration, and no watchdog. A hung server or a captive portal can leave a fetch
blocked for minutes.

### 5.4 No SSH — BUG-008 (HIGH, Confirmed)

`USE_SSH OFF` (`native/git/CMakeLists.txt:120`) and no libssh2 in the build. `ssh://` and
`git@github.com:` remotes are impossible. For HMX Git's stated "advanced Git client" scope this is a
capability gap, not a defect in the GitHub-only flows.

### 5.5 No shallow/partial clone, no prune, no depth

`nativeClone` performs a full clone. `nativeFetch` has no prune and no depth. On a mobile data
connection a large repository is a multi-hundred-MB download with no way to limit history.

### 5.6 Branch listing scope

`nativeBranches` iterates branches; the Kotlin model `GitBranch` carries a `isRemote` flag. The
audit did not find an explicit `GIT_BRANCH_REMOTE` filter in the native loop, so remote branches are
listed. This is acceptable, but the UI must not confuse `origin/main` with local `main` when building
a push refspec — `GitWorkflowPolicy.pushRefspec` always uses `refs/heads/<source>:refs/heads/<target>`,
which is correct.

### 5.7 Checkout safety

`nativeCheckout` uses default `git_checkout_options`. The default strategy is `SAFE`, so uncommitted
changes that would be overwritten cause an error rather than being discarded. No explicit
`FORCE` path was found. **Positive.**

### 5.8 Submodules

`nativeSubmodules`, `nativeSyncSubmodule`, `nativeUpdateSubmodule` are implemented. `updateSubmodule`
supports `initialize`. There is no recursive update and no credential propagation to submodule
remotes; a private submodule will fail to initialize because the credential callback is bound to the
parent operation's URL only. This is a real functional gap for monorepos with submodules (P2
capability note).

---

## 6. Repositories and workspaces

`WorkspaceRegistry` (`core/storage/WorkspaceRegistry.kt`) persists workspaces in Room and maps them
to directories on disk. `WorkspacePathPolicy` enforces canonical containment and blocks `.git`.

Notable:

- **Direct SAF projects** and **managed clones** are both supported (`WorkspaceStrategyResolver`).
- A managed workspace is a real directory under app storage; a direct project uses a persisted SAF
  tree URI. Git operations on a SAF-backed tree require a local mirror; the audit found a
  `DirectProjectScanner` and sync engine, which implies mirroring, but did not prove the mirror is
  used for every Git call. This is **Unverified** and flagged in report 18.
- No repository-level lock file is created by the app; libgit2 creates `.git/index.lock` per
  operation, which is the only mutual exclusion.

---

## 7. Git engine findings summary

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-004 | HIGH | Confirmed | No per-repository lock; UI + WorkManager can write concurrently |
| BUG-005 | HIGH | Confirmed | No progress, no cancellation, no timeout for native transfers |
| BUG-008 | HIGH | Confirmed | No SSH transport (`USE_SSH OFF`) |
| BUG-033 | LOW | Confirmed | `git_strarray` leak in failed `rename_remote` |
| BUG-039 | MEDIUM | Confirmed | Modified UTF-8 at the Git JNI boundary |
| BUG-040 | MEDIUM | Confirmed | Native credential buffers not zeroized |
| — | P2 capability | Confirmed | Conflict resolution limited to OURS/THEIRS |
| — | P2 capability | Confirmed | No shallow/partial clone, no prune |
| — | P2 capability | Unverified | Submodule credential propagation |
| — | P2 capability | Unverified | SAF-backed Git call path |

---

## 8. Git engine verdict

The engine is **the most credible part of Nexora Git**:

- 43 operations, real libgit2, correct credential isolation, force-with-lease, conflict handling,
  stash/rebase/cherry-pick/revert/tags/submodules/blame.
- JNI parity is exact; C++ exception boundaries are correct; handle cleanup is disciplined in the
  common paths.

It is **not production-grade for an "advanced" client** because:

1. there is no concurrency control on a shared singleton engine;
2. long transfers cannot be cancelled or observed;
3. SSH, shallow clone and submodule auth are missing;
4. policy is in the UI layer, so the engine can be misused.

None of these causes the immediate startup crash. They are the P1 engine backlog for HMX Git
(`16_FIX_PRIORITY_AND_RECOMMENDATIONS.md`).
