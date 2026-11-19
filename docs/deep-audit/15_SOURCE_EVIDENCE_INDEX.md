# 15 — Source Evidence Index

**Purpose:** every significant claim in this audit set traces to a concrete location. Line numbers
refer to the audited commit. `file:line` is exact for single-line findings; ranges are marked.

**Audited commits**

| Repository | URL | Commit SHA | Date |
| --- | --- | --- | --- |
| Nexora Git | `https://github.com/Gh0stDeveloper/Nexora-Git` | `f4befb6c1954d956f0a67b14f4ab1642aa185833` | 2026-10-09 |
| Gitling | `https://github.com/maneeshacooray/Gitling` | `390591428d17e0ddf324ad1dd9ec5f20f45909bc` | 2026-11-17 |
| PuppyGit | `https://github.com/catpuppyapp/PuppyGit` | `00657bd0ec6513b15ecc43e24676d0966464d97e` | 2026-11-17 |

**Evidence classes:** VERIFIED · PARTIALLY VERIFIED · DOCUMENTED ONLY · NOT FOUND · FAILED · UNKNOWN.

---

## 1. Nexora Git — identity and build

| Path | Symbol / location | Line(s) | Finding supported | Class |
| --- | --- | --- | --- | --- |
| `settings.gradle.kts` | `include(":app")`, `include(":baselineprofile")` | end of file | Only 2 Gradle modules despite "feature modules" claim | VERIFIED |
| `gradle/libs.versions.toml` | `agp = "9.4.0"`, `kotlin = "2.2.10"` | versions block | Build toolchain | VERIFIED |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl=…gradle-9.6.0-bin.zip`, `distributionSha256Sum` | all | Gradle 9.6 + SHA pinning | VERIFIED |
| `app/build.gradle.kts` | `minSdk = 26` | 46 | Android floor | VERIFIED |
| `app/build.gradle.kts` | `targetSdk = 36` | 47 | Target | VERIFIED |
| `app/build.gradle.kts` | `isMinifyEnabled = true`, `isShrinkResources = true` | 105–106 | R8 enabled | VERIFIED |
| `app/build.gradle.kts` | `abiFilters += setOf("arm64-v8a","armeabi-v7a","x86_64")` | 58–61 | ABI set | VERIFIED |
| `app/build.gradle.kts` | `ndkVersion = "27.2.12479018"` | 134 | NDK pin | VERIFIED |
| `app/build.gradle.kts` | `applicationIdSuffix = ".ci"` | 108 | Ephemeral CI signing separation | VERIFIED |
| `native/git/CMakeLists.txt` | `NEXORA_LIBGIT2_REF "49e408b…"` | 11 | libgit2 pin | VERIFIED |
| `native/git/CMakeLists.txt` | `NEXORA_MBEDTLS_REF "068ff08…"` | 12 | Mbed TLS pin | VERIFIED |
| `native/git/CMakeLists.txt` | `NEXORA_TREE_SITTER_REF "752c612…"` | 13 | Tree-sitter pin | VERIFIED |
| `native/git/CMakeLists.txt` | `set(USE_SSH OFF …)` | 120 | SSH disabled | VERIFIED |
| `native/git/CMakeLists.txt` | `set(USE_HTTPS "mbedTLS" …)` | 123 | TLS backend | VERIFIED |
| `AndroidManifest.xml` | `android:allowBackup="false"` | 8 | Backup disabled | VERIFIED |
| `AndroidManifest.xml` | `android:networkSecurityConfig="@xml/network_security_config"` | 14 | NSC present | VERIFIED |
| `AndroidManifest.xml` | `android:usesCleartextTraffic="false"` | 18 | No cleartext | VERIFIED |
| `AndroidManifest.xml` | `autoVerify="true"`, `path="/oauth/android/callback"` | 30, 37 | App Links callback | VERIFIED |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | all domains excluded | all | Backup hardening | VERIFIED |
| `res/xml/network_security_config.xml` | `cleartextTrafficPermitted="false"`, system CAs; user CAs only `debug-overrides` | all | Transport hardening | VERIFIED |

---

## 2. Nexora Git — Git engine (Kotlin)

| Path | Symbol | Line(s) | Finding | Class |
| --- | --- | --- | --- | --- |
| `core/git/GitEngine.kt` | `interface GitEngine`, 48 `suspend fun` | 3–end | Engine abstraction | VERIFIED |
| `core/git/GitEngine.kt` | `suspend fun clone` | 10 | Clone API | VERIFIED |
| `core/git/GitEngine.kt` | `suspend fun push` | 114 | Push API | VERIFIED |
| `core/git/GitEngine.kt` | `suspend fun resolveConflict` | 134 | Conflict API | VERIFIED |
| `core/git/GitEngine.kt` | `suspend fun rebase` | 147 | Rebase API | VERIFIED |
| `core/git/Libgit2GitEngine.kt` | `class Libgit2GitEngine : GitEngine` | 8–13 | Single implementation | VERIFIED |
| `core/git/NativeGitBridge.kt` | 50 `external fun` (e.g. `nativeClone` 40, `nativePush` 158, `nativePushForceWithLease` 166, `nativeRebase` 205, `nativeBlame` 311) | — | JNI symmetry | VERIFIED |
| `core/git/NativeGitBridge.kt` | `ensureInitialized()` `synchronized` | 15–27 | One-time native init | VERIFIED |
| `core/git/GitRemoteSecurityPolicy.kt` | `allowsGitHubOAuthCredentials` | 8–end | `https://github.com` only | VERIFIED |
| `core/git/GitCredentialProvider.kt` | `GitHubGitCredentialProvider.credentialsFor` | 10–end | Credential gate | VERIFIED |
| `core/git/GitLfsManager.kt` | `inspect`, pointer parsing | 1–end | LFS pointer handling | VERIFIED |
| `core/git/GitLfsTransport.kt` | LFS batch/transfer | 526 LOC | LFS transport | PARTIALLY VERIFIED |
| `core/git/GitJsonParser.kt` | JSON → models | 290 LOC | Native contract parsing | VERIFIED |
| `core/git/GitNativeException.kt` | `GitNativeException` | all | Typed JNI error | VERIFIED |

---

## 3. Nexora Git — native engine (C++)

| Path | Symbol | Line(s) | Finding | Class |
| --- | --- | --- | --- | --- |
| `native/git/src/git_core.cpp` | `is_github_https_url` | 127 | Host allow-list | VERIFIED |
| `native/git/src/git_core.cpp` | `credentials_callback` | 139 | `GIT_PASSTHROUGH` for non-GitHub | VERIFIED |
| `native/git/src/git_core.cpp` | `repository_has_conflicts` | 225 | Conflict detection | VERIFIED |
| `native/git/src/git_core.cpp` | `conflicts_json` | 233 | Conflict payload | VERIFIED |
| `native/git/src/git_core.cpp` | `fast_forward_only` | 632 | FF-only pull | VERIFIED |
| `native/git/src/git_core.cpp` | `advance_rebase` | 668 | Rebase continue (ours-only commit path) | VERIFIED |
| `native/git/src/git_core.cpp` | `init_repository` | 835 | Init | VERIFIED |
| `native/git/src/git_core.cpp` | `clone_repository`, `git_clone` | 856, 872 | Clone (no progress cb) | VERIFIED |
| `native/git/src/git_core.cpp` | `remote_url` | 886 | Remote lookup | VERIFIED |
| `native/git/src/git_core.cpp` | `status` | 1031 | Status | VERIFIED |
| `native/git/src/git_core.cpp` | `commit` | 1195 | Commit | VERIFIED |
| `native/git/src/git_core.cpp` | `pull` / `pull_with_strategy` | 1605 / 1620 | Pull | VERIFIED |
| `native/git/src/git_core.cpp` | `git_remote_fetch` | 1593 | Fetch (no progress cb) | VERIFIED |
| `native/git/src/git_core.cpp` | `push` / `git_remote_push` | 2132 / 2177 | Push | VERIFIED |
| `native/git/src/git_core.cpp` | `push_force_with_lease` / `git_remote_push` | 2197 / 2328 | Force-with-lease | VERIFIED |
| `native/git/src/git_core.cpp` | `diff` | 2348 | Diff | VERIFIED |
| `native/git/src/git_core.cpp` | `merge` | 2455 | Merge | VERIFIED |
| `native/git/src/git_core.cpp` | `resolve_conflict` | 2516 | Ours/theirs only | VERIFIED |
| `native/git/src/git_core.cpp` | `blame` / `git_blame_file` | 2885 / 2901 | Blame | VERIFIED |
| `native/git/src/advanced_git.cpp` | `rebase_onto` | 681 | Explicit rebase | VERIFIED |
| `native/git/src/advanced_git.cpp` | `cherry_pick` / `abort_cherry_pick` | 766 / 903 | Cherry-pick + abort | VERIFIED |
| `native/git/src/advanced_git.cpp` | `stashes` / `save_stash` / `drop_stash` | 929 / 961 / 1029 | Stash ops | VERIFIED |
| `native/git/src/advanced_git.cpp` | `reset_to` | 1041 | Reset (incl. hard) | VERIFIED |
| `native/git/src/advanced_git.cpp` | `revert_commit` | 1107 | Revert (+continue/abort 1193/1241) | VERIFIED |
| `native/git/src/advanced_git.cpp` | `tags` / `create_tag` / `delete_tag` | 1267 / 1376 / 1447 | Tags | VERIFIED |
| `native/git/src/advanced_git.cpp` | `submodules` / `update_submodule` | 1467 / 1544 | Submodules | VERIFIED |
| `native/git/src/git_jni.cpp` | 50 `JNIEXPORT` (e.g. `nativeClone` 195, `nativePush` 531, `nativeRebase` 672) | — | JNI parity with Kotlin | VERIFIED |
| `native/git/src/git_jni.cpp` | `throw_native` | 65–135 | Typed exception mapping | VERIFIED |
| `native/git/src/git_jni.cpp` | `string_call` / `void_call` try/catch | 137–155 | Exception safety | VERIFIED |
| `native/git/src/syntax_engine.cpp` | `kMaxVisitedNodes/Spans/Symbols/Diagnostics` | 28–31 | Bounded parsing | VERIFIED |
| `native/git/src/syntax_engine.cpp` | grammar entry points (7) | 14–22 | Tree-sitter grammars | VERIFIED |
| `native/git/src/lfs_filter.cpp` | clean/smudge | 1–end | Native LFS filter | VERIFIED |
| `native/git/tests/native_smoke.cpp` | `basic_local_workflow`, `remote_workflow` | 176, 422 | Real engine integration test | VERIFIED |

---

## 4. Nexora Git — auth, GitHub, editor, storage

| Path | Symbol | Line(s) | Finding | Class |
| --- | --- | --- | --- | --- |
| `core/auth/KeystoreCipher.kt` | `class KeystoreCipher`, `TRANSFORMATION = "AES/GCM/NoPadding"` | 15, 77 | AES-GCM Keystore crypto | VERIFIED |
| `core/auth/KeystoreCipher.kt` | `KEY_ALIAS = "nexora_git_auth_master_v1"` | 76 | Key alias | VERIFIED |
| `core/auth/SecureAuthStorage.kt` | encrypted prefs read/write | 1–end | Encrypted token storage | VERIFIED |
| `core/auth/AuthConfig.kt` | `isValidAuthEndpointPair`, `isValidAppCallbackUri` | 25–end | Strict HTTPS endpoint policy | VERIFIED |
| `core/auth/PkceGenerator.kt` | S256 PKCE | all | PKCE | VERIFIED |
| `core/auth/PendingAuthorizationValidator.kt` | state validation | all | State binding | VERIFIED |
| `core/auth/OAuthCallbackParser.kt` | callback parsing | all | Callback handling | VERIFIED |
| `core/auth/AuthSessionRepository.kt` | active account id, refresh | — | Multi-account + refresh | PARTIALLY VERIFIED |
| `auth-broker/main.go` | routes `/health`, `/oauth/callback`, `/v1/oauth/exchange`, `/refresh`, `/revoke` | 119–125 | Broker endpoints | VERIFIED |
| `auth-broker/main.go` | `securityHeaders` | 366 | Broker headers | VERIFIED |
| `core/platform/GitHubRestClient.kt` | `class GitHubRestClient` | 18 | REST client | VERIFIED |
| `core/platform/GitHubRestClient.kt` | `response.code == 401 && retryAuthentication` | 110 | Single refresh+retry | VERIFIED |
| `core/platform/GitHubRestClient.kt` | `etag = response.header("ETag")` | 169 | ETag capture | VERIFIED |
| `core/platform/GitHubRestClient.kt` | `builder.header("If-None-Match", etag)` | 240 | Conditional GET | VERIFIED |
| `core/platform/GitHubUrlResolver.kt` | `requireOfficialApiHost` | 33–end | `api.github.com` only | VERIFIED |
| `core/platform/GitHubRestPaginator.kt` | `nextPage` | all | Pagination | VERIFIED |
| `core/platform/GitHubGraphQlClient.kt` | GraphQL client (285 LOC) | all | GraphQL | VERIFIED |
| `core/platform/GitHubRateLimitManager.kt` | header → StateFlow | all | Rate limits | VERIFIED |
| `core/advanced/GitHubAdvancedGitHubGateway.kt` | `/pages`, security alerts, gists, codespaces | 202–497 | Advanced endpoints | VERIFIED |
| `core/editor/EditorFileStore.kt` | `MAX_EDITOR_BYTES = 512L * 1024L` | 113 | 512 KiB editor cap | VERIFIED |
| `core/editor/EditorHistory.kt` | undo/redo stack | all | Undo/redo | VERIFIED |
| `core/editor/EditorTextOperations.kt` | 250 LOC text ops | all | Buffer ops | VERIFIED |
| `core/editor/lsp/LanguageServerClient.kt` | `interface LanguageServerClient` | 5 | No implementation anywhere | DOCUMENTED ONLY |
| `core/editor/lsp/LspJsonRpcCodec.kt` | JSON-RPC codec | all | Codec only | VERIFIED |
| `feature/editor/MobileEditorViewModel.kt` | `undo` 127, `redo` 133, `replaceCurrent` 232, `replaceAll` 254, `save` 274 | — | Editor features | VERIFIED |
| `feature/git/GitWorkspaceViewModel.kt` | 8 × `viewModelScope.launch` (134, 365, 396, 777, 850, 1028, 1130, …) | — | Concurrent op entry points (no lock) | VERIFIED |
| `core/work/DurableGitWorker.kt` | `CoroutineWorker.doWork` | 1–end | Durable fetch/pull | VERIFIED |
| `core/storage/WorkspaceRegistry.kt` | workspace registry | all | SAF/direct workspace | VERIFIED |
| `core/storage/ProjectRiskDetector.kt` | private-key regex | 132 | Repo risk detection | VERIFIED |
| `core/files/CodeBrowserFileSystem.kt` | `MAX_TEXT_BYTES = 2L*1024L*1024L` | 321 | 2 MiB browser truncation | VERIFIED |

---

## 5. Nexora Git — CI evidence (API-derived, not files)

| Object | Reference | Finding | Class |
| --- | --- | --- | --- |
| check-runs `b36c868` | `Build, lint and unit test` = success | Android build+test green | VERIFIED |
| check-runs `b36c868` | `Release build and production gate` = success | Release build green | VERIFIED |
| check-runs `3d0f933` | `ASan and UBSan native tests` = success | Native sanitizers | VERIFIED |
| check-runs `3d0f933` | `Build and test libgit2 core` = success | Native build/test | VERIFIED |
| check-runs `3d0f933` | `Fuzz secure native runtime decoder` = success | Fuzzing | VERIFIED |
| check-runs `3d0f933` | `Analyze Kotlin/Java` = **failure** | CodeQL Kotlin/Java failed once | VERIFIED |
| check-runs `f4befb6` (main HEAD) | only CodeQL jobs | No Android CI on HEAD | VERIFIED |
| releases API | `length = 0`, tags = 0 | Never released | VERIFIED |
| issue #57 | title contains "release NO-GO" | Maintainer declares NO-GO | VERIFIED |
| contributors API | 1 (incl. anon) | Bus factor 1 | VERIFIED |
| `git rev-list --count HEAD` | 409 | Commit count | VERIFIED |

---

## 6. Gitling

| Path | Symbol / location | Line(s) | Finding | Class |
| --- | --- | --- | --- | --- |
| `README.md` | "doesn't include an internal text editor" | 55 | No editor | VERIFIED |
| `app/build.gradle.kts` | ASM JGit bytecode-patch comment | 1–60 | JGit Java-17 workaround | VERIFIED |
| `app/build.gradle.kts` | `compileSdk = 37` / `minSdk = 23` / `targetSdk = 37` | 133 / 148 / 149 | SDK levels | VERIFIED |
| `app/build.gradle.kts` | `multiDexEnabled = true` | 154 | Multidex | VERIFIED |
| `app/build.gradle.kts` | `isMinifyEnabled = false` | 178 | No R8 | VERIFIED |
| `app/build.gradle.kts` | `isCoreLibraryDesugaringEnabled = true`, Java 17 | 187–190 | Desugaring | VERIFIED |
| `AndroidManifest.xml` | `android:allowBackup="true" android:largeHeap="true"` | 14 | Backup on + large heap | VERIFIED |
| `me/sheimi/android/utils/SecurePrefsHelper.java` | `Timber.w("pref password %s", prefsPassword)` | 68 | **Master password logged** | VERIFIED |
| `me/sheimi/sgit/repo/tasks/repo/PushTask.java` | `case REJECTED_NONFASTFORWARD:` (no `break`) | 147 | Non-FF message fall-through | VERIFIED |
| `me/sheimi/sgit/database/models/Repo.java` | `private String mPassword;` | 68 | Plaintext password field | VERIFIED |
| `me/sheimi/sgit/database/models/Repo.java` | `private Git mGit;`, `static SparseArray<RepoOpTask>` | 80, 88 | God object | VERIFIED |
| `me/sheimi/sgit/repo/tasks/repo/CloneTask.java` | `SHALLOW_DEPTH = 1`, `setProgressMonitor(new RepoCloneMonitor())` | 29, 114 | Shallow + progress | VERIFIED |
| `me/sheimi/sgit/repo/tasks/repo/RebaseTask.java` | `rebase().setUpstream().call()` only | 31 | No continue/abort | VERIFIED |
| `me/sheimi/sgit/repo/tasks/repo/` | 24 task classes | dir | Engine task layer | VERIFIED |
| `com/manichord/mgit/repodetail/GitCommandEngine.kt` | `execute`, `"stash"` | 26, 39 | Git console | VERIFIED |
| `com/manichord/mgit/auth/GitHubAuthManager.kt` | `CLIENT_ID = "Ov23limX5SJfOQfVzNXZ"` | 39 | Public device-flow client id | VERIFIED |
| `com/manichord/mgit/auth/GitHubAuthManager.kt` | `DEVICE_CODE_URL`, `TOKEN_URL`, `GRANT_TYPE` | 40–43 | Device Flow | VERIFIED |
| `me/sheimi/sgit/AndroidJschCredentialsProvider.java` | prompt-string → key filename parsing | `get()` | Fragile credential lookup | VERIFIED |
| `me/sheimi/sgit/ssh/PrivateKeyUtils.java` | `getPrivateKeyFolder`, public-key derivation | all | SSH key handling | VERIFIED |
| `me/sheimi/sgit/ssh/PrivateKeyGenerate.kt` | `generateKey` | 142 | Key generation | VERIFIED |
| `me/sheimi/sgit/importrepo/RepoImporter.kt` | `tempCanonical`, `canonicalPath.startsWith(tempCanonical)` | 163, 187 | Zip-Slip guard | VERIFIED |
| `me/sheimi/sgit/activities/ViewFileActivity.kt` | WebView loads `file:///android_asset/editor.html` | ~178 | CodeMirror viewer | VERIFIED |
| `me/sheimi/android/utils/CodeGuesser.java` | `URL_SCRIPT_WRAPPER = "javascript:(function(){%s;})()"` | 122 | JS injection pattern | VERIFIED |
| `.github/workflows/build.yml` | `branches: [main]` (default is `master`) | 7–8 | CI branch mismatch | VERIFIED |
| `.github/workflows/release.yml` | tag trigger + keystore decode + publish | all | Working release pipeline | VERIFIED |
| `app/src/test/**` | 5 `@Test` methods | 2 files | Minimal tests | VERIFIED |
| releases API | 68 releases; latest `v1.0.66` | — | Release maturity | VERIFIED |
| contributors API | 64 | — | Contributor base | VERIFIED |

---

## 7. PuppyGit

| Path | Symbol / location | Line(s) | Finding | Class |
| --- | --- | --- | --- | --- |
| `lib_build_scripts/2_downloadsrc.sh` | `LIBGIT2_VERSION="1.9.7"` | 5 | libgit2 version (script) | VERIFIED |
| `README.md` | "built from libgit2 1.9.4" | 170 | Version **mismatch** with script | VERIFIED |
| `README.md` | libssh2 1.11.1 | 171 | SSH via libssh2 | VERIFIED |
| `README.md` | git24j fork | 172 | Binding provenance | VERIFIED |
| `lib_src/git24j-1.0.4.20241114-sources.jar` | git24j binding | — | JNI wrapper | VERIFIED |
| `app/src/main/java/.../utils/Libgit2Helper.kt` | file size 385,601 B | — | God object | VERIFIED |
| `app/src/main/java/.../dev/DevFlag.kt` | `val lfsTestPassed = false` | 53 | LFS untested flag | VERIFIED |
| `app/build.gradle.kts` | comment `id("com.google.devtools.ksp")`, Sora deps commented out | 176–181 | Editor library not enabled | VERIFIED |
| `app/src/main/java` | 545 Kotlin files (~113k LOC) | — | Codebase size | VERIFIED |
| `app/src/test`, `app/src/androidTest` | template examples only | — | No real tests | VERIFIED |
| `.github/workflows/` | build/probe scripts, no test gate | — | Weak CI | VERIFIED |
| releases API | `1.1.5.10v136`, 5 APK assets | — | Release cadence | VERIFIED |
| `app/src/main/java` | `grep "api.github.com"` → none | — | No GitHub API | VERIFIED (absence) |

---

## 8. Upstream dependency verification (API / license fetch)

| Object | Reference | Finding | Class |
| --- | --- | --- | --- |
| `libgit2/libgit2` @ `49e408b` | commit message "v1.9.7: update version numbers" | Nexora pin = v1.9.7 | VERIFIED |
| `libgit2` `COPYING` | "LINKING EXCEPTION … unlimited permission to link" | Permits linking into non-GPL apps | VERIFIED |
| `Mbed-TLS/mbedtls` @ `068ff08` | commit 2026-07-01 | Mbed TLS pin | VERIFIED |
| `Mbed-TLS` license | Apache-2.0 OR GPL-2.0 (dual) | Choose Apache-2.0 | VERIFIED |
| `tree-sitter/tree-sitter` @ `752c612` | commit 2026-10-05; license MIT | Tree-sitter pin + license | VERIFIED |
| `eclipse-jgit/jgit` `LICENSE` | Eclipse Distribution License 1.0 | JGit license | VERIFIED |
| `mwiede/jsch` `LICENSE.txt` | "switched over to a BSD-style license" | JSch BSD-3 | VERIFIED |
| `Rosemoe/sora-editor` | API `license.spdx_id = LGPL-2.1` | Editor component license | VERIFIED |
| `massivemadness/Squircle-CE` | API `license.spdx_id = Apache-2.0` | Editor reference license | VERIFIED |

---

## 9. Evidence that is explicitly NOT available

| Claim | Why unavailable | Class |
| --- | --- | --- |
| Local build succeeded | No SDK/NDK/Gradle/JDK-21 in audit env | UNKNOWN |
| APK installs / app runs | No device/emulator | UNKNOWN |
| Clone/push over SSH end-to-end | No SSH host + no build | UNKNOWN |
| OAuth flow with real GitHub | No account/credentials | UNKNOWN |
| Memory/CPU/battery figures | No device | UNKNOWN |
| Nexora APK size | No published artifact | UNKNOWN |
| PuppyGit full source audit | Shallow clone, time-boxed | PARTIALLY VERIFIED |
| Broker deployment security | Requires a live deployment | DOCUMENTED ONLY |

See [`16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md`](16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md).
