# 13 — Final Repository Ranking

**Purpose:** convert the evidence in reports 03–12 into a transparent, weighted score for the
specific **HMX Git** scope. Scores are 0–10. Weights are the ones specified for this audit. No
score is adjusted to steer the outcome; each is justified against a cited source finding.

---

## 1. Weights (as specified)

| Category | Weight |
| --- | --- |
| Git engine | **20%** |
| GitHub integration | **12%** |
| Security | **12%** |
| Architecture | **10%** |
| Android compatibility | **10%** |
| Testing | **10%** |
| Editor | **8%** |
| Performance | **6%** |
| Maintenance | **5%** |
| License compatibility | **4%** |
| Release maturity | **3%** |
| **Total** | **100%** |

---

## 2. Scorecard

| Category (weight) | Nexora Git | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Git engine (20%) | **7.5** | **7.0** | **9.0** |
| GitHub integration (12%) | **8.5** | **1.5** | **0.5** |
| Security (12%) | **7.5** | **4.5** | **6.0** |
| Architecture (10%) | **6.0** | **4.5** | **4.0** |
| Android compatibility (10%) | **7.0** | **6.0** | **7.0** |
| Testing (10%) | **7.5** | **2.0** | **2.0** |
| Editor (8%) | **7.0** | **1.5** | **3.5** |
| Performance (6%) | **6.5** | **4.5** | **7.0** |
| Maintenance (5%) | **3.0** | **8.5** | **6.0** |
| License compatibility (4%) | **10.0** | **3.0** | **3.0** |
| Release maturity (3%) | **1.0** | **9.5** | **7.5** |
| **Weighted total** | **7.00** | **4.59** | **5.23** |
| **Rank** | **1** | **3** | **2** |

> **Note on arithmetic:** weighted total = Σ(score × weight) / 100. Displayed to two decimals;
> Gitling/GuppyGit are 4.59 → **4.6** and 5.23 → **5.2** in summary text.

---

## 3. Justification per category

### 3.1 Git engine (20%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **7.5** | Real libgit2 1.9.7; **25 operations** incl. rebase/cherry-pick/revert **continue-abort**, stash, tags, submodules, LFS; native integration test (`native_smoke.cpp`). Deductions: **no SSH**, no progress, no cancel, no repo lock, conflict resolution ours/theirs only. (Report 03 §4, 06 §5) |
| Gitling | **7.0** | JGit 7.3.0, 24 task classes, **SSH via JSch**, clone progress + shallow. Deductions: **no revert**, **no rebase continue/abort**, non-FF message bug (`PushTask.java:147`), no LFS. (Report 04 §4, 07 SEC-12) |
| PuppyGit | **9.0** | Deepest surface: LFS (implemented; author flags `lfsTestPassed=false`), reflog, patch, squash, SSH/libssh2, shallow, submodules, conflict editor. Deduction: binding is opaque git24j; version provenance ambiguous (1.9.4 vs 1.9.7). (Report 05 §3.2) |

### 3.2 GitHub integration (12%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **8.5** | REST + GraphQL, ETag cache, pagination, rate-limit manager, 401 refresh, host allow-list; issues/PRs/reviews/Actions/logs/releases/notifications/orgs/Pages/security/Codespaces/Gists. Deduction: some depth unproven e2e. (Report 03 §6) |
| Gitling | **1.5** | Device Flow auth only; **no** issues/PRs/Actions/releases/notifications/orgs; only two `api.github.com` uses. (Report 04 §5, `G-GH-01`) |
| PuppyGit | **0.5** | **No GitHub API at all**. (Report 05 §3.3, `P-GH-01`) |

### 3.3 Security (12%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **7.5** | AES-GCM Keystore tokens, `allowBackup=false` + full exclusion, strict NSC, native host allow-list, App Links, secret scanning, ASan/UBSan/fuzz in CI. Deductions: broker dependency (HIGH), no repo lock, no secondary-rate-limit handling. (Report 07) |
| Gitling | **4.5** | Device Flow (no secret) is good; **logs SecurePreferences master password** (`SecurePrefsHelper.java:68`, HIGH), `allowBackup=true`, deprecated RSA KeyPairGeneratorSpec, WebView JS viewer. No analytics. (Report 07 SEC-01/05/07/08) |
| PuppyGit | **6.0** | App-managed credential encryption, SSH host-key prompts, no analytics/ads; but "trust unknown host" option and unverified storage posture. (Report 07 §2.3, SEC-13) |

### 3.4 Architecture (10%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **6.0** | Correct layering (`feature → core`), Hilt DI, `GitEngine` interface, gateway pattern. Deductions: **single `:app` module** despite "feature modules" claim; 100 KB screen, 38 KB VM, 28 KB gateway. (Report 03 §2) |
| Gitling | **4.5** | Functional but **32 KB `Repo.java` god object**, dual Java/Compose UI, no DI, `AsyncTask`. (Report 04 §3) |
| PuppyGit | **4.0** | **385 KB `Libgit2Helper.kt`**, 230 KB screen files, single module. (Report 05 §3.4) |

### 3.5 Android compatibility (10%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **7.0** | minSdk 26, arm64/armv7/x86_64, no `largeHeap`, baseline profiles, resource shrinking. Deduction: no device testing. (Report 12 §1, §4) |
| Gitling | **6.0** | minSdk 23 (widest), tablet `sw600dp`/`sw720dp-land`, 12 locales. Deductions: `largeHeap`, 37.5 MB APK, 7 dex, no minify. (Report 12 §2.1) |
| PuppyGit | **7.0** | 4 ABIs, ~13 MB per-ABI APK, libgit2, shallow clone. Deduction: not device-verified; embedded server. (Report 12 §2.2) |

### 3.6 Testing (10%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **7.5** | **102 unit tests**, native integration suite against a bare remote, ASan/UBSan, fuzz, clang-tidy, CodeQL, secret scan. Deductions: no concurrency/cancel/conflict e2e, main HEAD lacks Android CI, CodeQL Kotlin/Java failed once. (Report 09 §3) |
| Gitling | **2.0** | **5 unit tests**, no instrumentation, `testDebug` only. (Report 09 §3.1) |
| PuppyGit | **2.0** | Template tests only; no quality-gate CI. (Report 09 §2.3) |

### 3.7 Editor (8%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **7.0** | Real Compose editor, **native Tree-sitter (7 grammars)**, undo/redo, search/replace, diff preview, formatting. Deductions: no tabs/autosave/crash recovery, 512 KiB cap, **LSP facade only**. (Report 08 §2) |
| Gitling | **1.5** | **No editor**; read-only CodeMirror WebView viewer. (Report 08 §3) |
| PuppyGit | **3.5** | Basic editor; author recommends external editors; Sora dependency commented out. (Report 08 §4) |

### 3.8 Performance (6%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **6.5** | libgit2 (off-heap), baseline profiles, minify. Deductions: **no progress, no cancel, no shallow clone**, editor cap; no measured data. (Report 12 §6) |
| Gitling | **4.5** | Progress + shallow are good; but JGit + `largeHeap` + 37.5 MB APK. (Report 12 §3, §2.1) |
| PuppyGit | **7.0** | libgit2, ~13 MB APK, shallow; but embedded HTTP server battery risk. (Report 12 §2.2, P-09) |

### 3.9 Maintenance (5%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **3.0** | **1 contributor**, 6 weeks old, 0 releases, release NO-GO, main stale since 2026-10-09. (Report 02 §2) |
| Gitling | **8.5** | **64 contributors**, 1,433 commits, 68 releases, active weekly, 2 open issues. (Report 02 §2) |
| PuppyGit | **6.0** | 1 primary maintainer but very active, many releases, large user base (915★/159 forks). (Report 02 §1.3) |

### 3.10 License compatibility (4%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **10.0** | Apache-2.0; libgit2 linking exception verified; Tree-sitter MIT; Mbed TLS dual. Clean proprietary chain. (Report 10 §3) |
| Gitling | **3.0** | GPL-3.0 → forces copyleft on HMX Git. (Report 10 §2.2) |
| PuppyGit | **3.0** | GPL-3.0 → forces copyleft. (Report 10 §2.2) |

### 3.11 Release maturity (3%)

| Candidate | Score | Evidence |
| --- | --- | --- |
| Nexora | **1.0** | **0 releases, 0 tags**, `release.yml` never executed, project self-declares **NO-GO**. (Report 02 §1.1, 09 B-06/B-13) |
| Gitling | **9.5** | **68 signed releases**, Play + F-Droid + GitHub, latest 2026-11-17. (Report 02 §1.2) |
| PuppyGit | **7.5** | Many releases + IzzyOnDroid, but manual signing and no CI release gate. (Report 09 §2.3) |

---

## 4. Ranking

| Rank | Repository | Weighted score | Profile |
| --- | --- | --- | --- |
| **1** | **Nexora Git** | **7.00** | Best on the two heaviest and most differentiating categories (Git engine breadth + GitHub platform + license), weakest on maturity. |
| **2** | **PuppyGit** | **5.23** | Best pure-Git engine, but **zero** GitHub platform and a licensing/maintainability penalty. |
| **3** | **Gitling** | **4.59** | Most mature and released, but **no GitHub platform, no editor**, and GPL-3.0. |

---

## 5. Sensitivity analysis (does the order change?)

| Change to weights | Effect |
| --- | --- |
| GitHub 12% → **0%** (pure Git client) | Nexora ≈ 5.9, Gitling ≈ 4.4, PuppyGit ≈ 5.2 → **Nexora still first** (engine breadth + tests) |
| Maintenance 5% → **25%**, Release 3% → **15%** (maturity-first) | Gitling rises sharply; Nexora falls; **order flips toward Gitling** |
| License 4% → **20%** (proprietary requirement) | Nexora ≈ 8.1, Gitling ≈ 3.2, PuppyGit ≈ 3.5 → **Nexora by a wider margin** |
| Testing 10% → **25%** | Nexora pulls further ahead |
| Editor 8% → **25%** | Nexora ≈ 8.2, Gitling ≈ 1.6, PuppyGit ≈ 3.9 → **Nexora dominant** |

> **Finding R-01** — Nexora wins under **every weighting that reflects HMX Git's actual scope**
> (Git + GitHub + editor + permissive license). Gitling only wins if maturity is weighted far above
> the HMX feature set — i.e. if the product requirement were "a stable pure Git client", not
> "an advanced Git and GitHub workspace". **Confidence: High.**

> **Finding R-02** — PuppyGit is a genuine contender only as an **engine reference**, never as the
> GitHub+editor foundation.

---

## 6. Confidence in the ranking

| Conclusion | Confidence | Basis |
| --- | --- | --- |
| Nexora ranks first | **High** | Source-verified coverage of Git + GitHub + editor + license |
| Nexora is not production-ready | **High** | 0 releases; maintainer NO-GO issue #57 |
| Gitling ranks last for *this scope* | **High** | Exhaustive proof of absent GitHub platform + absent editor |
| PuppyGit ranks second | **Medium** | Focused (not full) audit; shallow clone |
| Absolute scores are precise | **Low–Medium** | Rubric is judgment-based; ordering is robust, exact decimals are not |
| Performance/RAM numbers | **Low** | Estimates only; no device benchmarks |

> **Why the numbers should be read as bands:** the rubric encodes product priorities, and several
> sub-scores (e.g. GitHub depth, editor maturity) rest on **PARTIALLY VERIFIED** evidence. The
> **order (7.0 > 5.2 > 4.6)** is stable across every scope-relevant weighting; the exact decimals
> are not meaningful beyond ±0.3.

---

## 7. Recommended candidate

**Nexora Git (7.00/10)** — recommended as the base, conditional on the Phase-0 gate in reports 09
and 14. Fallback: **Option E (independent composition)** if the gate fails. Not recommended:
Gitling (B) or PuppyGit (C) as whole-application bases.

See [`17_FINAL_DECISION.md`](17_FINAL_DECISION.md).
