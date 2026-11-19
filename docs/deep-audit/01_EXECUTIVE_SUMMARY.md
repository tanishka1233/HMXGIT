# 01 — Executive Summary

**Audit date:** 2026-11-19
**Scope:** read-only source audit of existing open-source Android Git/GitHub clients
**Candidates:** Nexora Git, Gitling; one discovery shortlist candidate (PuppyGit)

---

## 1. Objective

Identify the strongest existing open-source foundation for **HMX Git**, an advanced native
Android Git + GitHub client, and produce an evidence-based adoption recommendation. This audit
does **not** implement, fork, modify or rebrand any application.

---

## 2. What was examined

| Repository | HEAD | License | What it actually is |
| --- | --- | --- | --- |
| **Nexora Git** | `f4befb6` (main) | Apache-2.0 | 208 Kotlin files (46,729 LOC) + 7,058 LOC C++ libgit2 JNI + Go auth broker + web site. Six weeks old, one contributor, no release. |
| **Gitling** | `3905914` (master, v1.0.66) | GPL-3.0 | 85 Kotlin + 75 Java files (13,835 + 7,182 LOC) built on a 2013 SGit → MGit lineage. JGit 7.3.0 with a build-time bytecode patch. Published on Play and F-Droid. |
| **PuppyGit** | `00657bd` (main) | GPL-3.0 | 545 Kotlin files, 113k LOC, libgit2 via a vendored git24j JNI fork, libssh2/OpenSSL, 68 releases. No GitHub API layer. |

---

## 3. Key discoveries

### 3.1 Nexora Git is not "documentation only" — the engine is real

The native layer implements genuine libgit2 operations, not GitHub REST file uploads:

- `native/git/src/git_core.cpp`: clone, fetch, push, pull, merge, rebase, conflicts, history, blame.
- `native/git/src/advanced_git.cpp`: reset, cherry-pick, revert, stash, tags, submodules.
- `native/git/src/git_jni.cpp`: 963 lines, 50 `JNIEXPORT` entry points matching
  `NativeGitBridge.kt`'s 50 `external fun` declarations one-for-one.
- libgit2 **v1.9.7** is pinned by commit (`49e408b…`, verified via GitHub API: commit message
  "v1.9.7: update version numbers"), with Mbed TLS pinned and `USE_SSH OFF`.

CI evidence: on commit `b36c868`, checks **"Build, lint and unit test"**, **"Release build and
production gate"** and four CodeQL jobs all concluded `success`; on `3d0f933`, **"ASan and UBSan
native tests"**, **"Build and test libgit2 core"**, **"Fuzz secure native runtime decoder"** and
**"Pinned native build without network"** all succeeded.
(Source: GitHub check-runs API, queried 2026-11-19.)

**But** `main` itself carries no Android CI run for its HEAD `f4befb6`; only CodeQL ran on that
commit. The project's own release gate issue #57 says **"release NO-GO"**.

### 3.2 Gitling is a mature product with a narrow GitHub surface

- 1,433 commits, 64 contributors, 68 releases, Play Store + F-Droid distribution.
- Real JGit usage across 24 task classes (clone, fetch, pull, push, merge, rebase, cherry-pick,
  reset, status, stage/unstage, blame, graph, submodules, checkout, tags via JGit).
- SSH is genuinely implemented (`SgitTransportCallback`, `SshCredentialsProvider`,
  private-key import/export/generation, keyboard-interactive fix).
- **No GitHub API client for issues/PRs/Actions/releases** — the only `api.github.com` uses are
  the device-flow token exchange and the app's own update checker.
- **No text editor.** The README says so explicitly; the file screen is a read-only CodeMirror
  WebView.

### 3.3 Licensing is the sharpest differentiator

| Repository | License | Commercial/closed-source reuse |
| --- | --- | --- |
| Nexora Git | Apache-2.0 | Yes, with NOTICE/attribution |
| Gitling | GPL-3.0 | Only if HMX Git is distributed under GPL-3.0 |
| PuppyGit | GPL-3.0 | Only if HMX Git is distributed under GPL-3.0 |

Nexora bundles libgit2 (GPL-2.0 **with linking exception**, verified from upstream `COPYING`),
which explicitly permits linking into differently licensed programs. Tree-sitter is MIT; Mbed TLS
is Apache-2.0 OR GPL-2.0; JGit is EDL-1.0 (BSD-style).

### 3.4 The "editor" gap decides the product scope

- Nexora: real Compose editor (`MobileEditorScreen.kt`, 38,926 bytes), custom `EditorHistory`
  undo/redo, search/replace, Tree-sitter highlighting/symbols/diagnostics from native code.
  No multiple tabs, no autosave, 512 KiB file cap, no LSP provider implementation.
- Gitling: no editor (file viewer only).
- PuppyGit: basic editor + Sora-derived highlighting; author recommends external editors.

### 3.5 Credential isolation is stronger in Nexora, but narrower

Nexora's native credential callback **refuses to send credentials to any host except
`https://github.com`** (`git_core.cpp`, `is_github_https_url`). Its Kotlin
`GitRemoteSecurityPolicy` enforces the same rule. This is excellent for token isolation and
fatal for GitLab/self-hosted support.

Gitling resolves credentials **per remote host**, including self-hosted Forgejo/Gitea and SSH.
PuppyGit adds SSH host-key verification prompts.

### 3.6 The strongest alternative is not stronger overall

PuppyGit surpasses both on pure Git surface (reflog, patch, squash, LFS, auto-sync, Tasker HTTP)
and on release cadence, but has **zero GitHub platform integration**, a 4/10 architecture, and
GPL-3.0. It is a source of components, not a base for HMX Git's stated scope.

---

## 4. Strongest candidate

**Nexora Git** — on weighted evidence (see 13), it scores **7.0/10** versus Gitling **4.6/10**
and PuppyGit **5.2/10**.

It is the only candidate that can host the full HMX Git scope without a license change:
local Git engine, GitHub platform, editor, syntax intelligence, permissive license.

It is **not** production-ready today: no release, no device QA, no SSH, one maintainer.

---

## 5. Major risks (ranked)

| ID | Risk | Level | Where |
| --- | --- | --- | --- |
| R-01 | No published or device-tested build; project declares release NO-GO | **CRITICAL** | Nexora #57 |
| R-02 | Single contributor, 6-week-old codebase; abandonment risk | **HIGH** | Nexora history |
| R-03 | No SSH transport; GitHub-HTTPS-only credentials | **HIGH** | Nexora CMake + policy |
| R-04 | No per-repository operation lock; concurrent Git ops can corrupt index | **HIGH** | Nexora (absence of lock) |
| R-05 | No transfer progress / true cancellation for clone/fetch/push | **HIGH** | Nexora native |
| R-06 | Self-hosted auth broker is a deployment and availability dependency | **HIGH** | Nexora `auth-broker/` |
| R-07 | GPL-3.0 copyleft blocks proprietary HMX distribution if Gitling/PuppyGit code is used | **HIGH** | Gitling, PuppyGit |
| R-08 | Gitling logs the SecurePreferences password (`Timber.w`) and uses deprecated crypto | **HIGH** | `SecurePrefsHelper.java:68` |
| R-09 | Gitling non-fast-forward push message falls through and is overwritten | **MEDIUM** | `PushTask.java:147` |
| R-10 | Large APK / 7-dex build in Gitling (37.5 MB, no minify) | **MEDIUM** | APK inspection |
| R-11 | Nexora's documented "feature modules" do not exist in Gradle | **MEDIUM** | `settings.gradle.kts` |
| R-12 | 100 KB single screen file / 38 KB ViewModel in Nexora | **MEDIUM** | `GitWorkspaceScreen.kt` |

---

## 6. Recommendation

### Primary: **Option A — Fork Nexora Git** (Apache-2.0)

Reuse: `core/git` + `native/git`, the GitHub platform layer, auth/Keystore stack, editor,
Room/DataStore, WorkManager durability, CI and security posture.

Replace/add before any public release:

1. SSH transport (libssh2 or JGit-style JSch) and generic HTTPS/PAT credentials.
2. Repository-scoped operation queue (single-writer lock).
3. libgit2 transfer-progress callbacks + cancellable JNI operations.
4. Editor tabs, autosave and crash recovery; remove or implement the LSP facade.
5. Auth strategy: GitHub Device Flow (no server) or a clearly operated broker.
6. Real-device acceptance on API 26/29/33/36 and 3–4 GB RAM hardware.

### Fallback: **Option E — Build independently on selected libraries**

If the Phase-0 gate fails (cannot reproduce the native build, or the device smoke test fails),
build HMX Git on: libgit2 (or JGit) + Compose + OkHttp + Room, reusing Apache-2.0/MIT components
and *designs* (not GPL code) from the audited projects.

### Not recommended

- **B (Fork Gitling):** would force GPL-3.0 and still require building the entire GitHub layer and
  an editor — i.e. most of HMX Git's differentiating work.
- **C (Fork PuppyGit):** GPL-3.0, no GitHub layer, and the largest architectural cleanup burden.
- **D (Combine code):** permitted only under GPL-3.0; combining GPL-3.0 code with Nexora's
  Apache-2.0 code changes HMX Git's license to GPL-3.0.

---

## 7. What must be audited again

1. A clean, reproducible local build of the chosen base (NDK 27.2, CMake 3.22.1, SDK 37).
2. Real clone/push/pull against GitHub, GitLab and a self-hosted server over HTTPS and SSH.
3. Interrupted-operation behaviour (process death, airplane mode, low storage).
4. Token lifecycle against a real GitHub App (refresh, revocation, multi-account switching).
5. Large-repository memory profile on a 3–4 GB device.
6. License review by counsel before distribution.

---

## 8. Confidence

| Conclusion | Confidence | Basis |
| --- | --- | --- |
| Nexora has a real libgit2 engine | **High** | 7k LOC C++ + JNI + native tests |
| Nexora has broad GitHub API coverage | **High** | 20-file platform layer + gateways |
| Nexora is not production-ready | **High** | Maintainer's own NO-GO issue + no releases |
| Gitling lacks GitHub platform features | **High** | Source grep: only 2 `api.github.com` uses |
| Gitling lacks an editor | **High** | README + WebView viewer source |
| Group ranking (A > C > B) | **Medium** | Weighted rubric; B's maturity is underweighted by design |
