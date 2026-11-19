# HMX Git — Deep Source-Code Audit & Repository Discovery

**Audit date:** 2026-11-19
**Audit type:** read-only source audit (no forks, no modification, no implementation)
**Audit workspace:** `/tmp/opencode/hmx-audit` (isolated; upstream repositories untouched)

---

## 1. Purpose

This documentation set determines which existing open-source project, if any, is the
strongest foundation for **HMX Git** — a native Android Git + GitHub workspace with:

- a real local Git engine (not GitHub-API file upload),
- GitHub REST/GraphQL coverage,
- an on-device code editor with syntax intelligence,
- advanced Git operations (rebase, cherry-pick, revert, stash, LFS, submodules, SSH),
- production-grade security, tests, licensing and maintainability.

No application was forked, rebranded or modified during this audit.

---

## 2. Candidates

| ID | Repository | Type | License | Audited |
| --- | --- | --- | --- | --- |
| A | [`Gh0stDeveloper/Nexora-Git`](https://github.com/Gh0stDeveloper/Nexora-Git) | Kotlin/Compose + libgit2/JNI + GitHub API | Apache-2.0 | Full |
| B | [`maneeshacooray/Gitling`](https://github.com/maneeshacooray/Gitling) | Kotlin/Compose + Java + JGit | GPL-3.0 | Full |
| C | [`catpuppyapp/PuppyGit`](https://github.com/catpuppyapp/PuppyGit) | Kotlin/Compose + libgit2 via git24j/JNI | GPL-3.0 | Focused (discovery shortlist) |

Discovery also evaluated MGit, SGit, GitJournal, FastHub, OpenHub, gh4a, ForkHub, FuwaGit,
github-mobile-maxxing, AndroidIDE, Acode, Termux, Sora Editor and Squircle-CE. See
[`05_ALTERNATIVE_REPOSITORY_DISCOVERY.md`](05_ALTERNATIVE_REPOSITORY_DISCOVERY.md).

---

## 3. Verified source revisions

| Repository | Default branch | Audited HEAD | Commit date | Commits in history |
| --- | --- | --- | --- | --- |
| Nexora Git | `main` | `f4befb6c1954d956f0a67b14f4ab1642aa185833` | 2026-10-09T03:18:46-06:00 | 409 |
| Gitling | `master` | `390591428d17e0ddf324ad1dd9ec5f20f45909bc` | 2026-11-17T07:41:59+05:30 | 1,433 |
| PuppyGit | `main` | `00657bd0ec6513b15ecc43e24676d0966464d97e` | 2026-11-17T07:25:32+08:00 | not counted (shallow clone) |

Note: Nexora Git's repository `pushed_at` is **2026-11-16** (dependency-update commits), but its
`main` HEAD is **2026-10-09**. Both facts are stated where relevant.

---

## 4. Final recommendation (summary)

> **Fork Nexora Git (Option A), subject to a mandatory Phase-0 verification gate; fall back to
> independent composition (Option E) if that gate fails.**

Nexora Git is the only candidate that combines, in permissively licensed source:

- a genuine libgit2 engine behind a Kotlin `GitEngine` interface (25 operations),
- a broad GitHub REST + GraphQL platform layer (issues, PRs, reviews, Actions, releases,
  notifications, organizations, Pages, security feeds, Gists, Codespaces),
- a real Compose editor with a native Tree-sitter engine,
- Android Keystore-backed token encryption and a documented OAuth/PKCE design,
- passing CI evidence for build, lint, unit tests, native tests and a release gate.

Its weaknesses are equally real: **no SSH**, **GitHub-only credentials**, **no transfer
progress/cancellation**, **no repository-level operation lock**, **no LSP implementation**,
**single-module packaging**, **one contributor**, **no published release**, and an
**auth broker that the user must host**.

Gitling is mature and battle-tested but is GPL-3.0, has no editor, and has essentially **no
GitHub platform features** beyond sign-in. PuppyGit has the deepest Git surface but **no
GitHub API layer at all** and is also GPL-3.0.

Full reasoning: [`17_FINAL_DECISION.md`](17_FINAL_DECISION.md) and
[`13_FINAL_REPOSITORY_RANKING.md`](13_FINAL_REPOSITORY_RANKING.md).

---

## 5. Report index

| # | Report | Contents |
| --- | --- | --- |
| 01 | [Executive Summary](01_EXECUTIVE_SUMMARY.md) | Objective, candidates, key discoveries, risks, recommendation |
| 02 | [Repository Identity & Maturity](02_REPOSITORY_IDENTITY_AND_MATURITY.md) | Verified URLs, branches, releases, activity |
| 03 | [Nexora Git Deep Audit](03_NEXORA_GIT_DEEP_AUDIT.md) | Full source-level audit |
| 04 | [Gitling Deep Audit](04_GITLING_DEEP_AUDIT.md) | Full source-level audit |
| 05 | [Alternative Repository Discovery](05_ALTERNATIVE_REPOSITORY_DISCOVERY.md) | Search method, shortlist, PuppyGit focused audit |
| 06 | [Git Engine Comparison](06_GIT_ENGINE_COMPARISON.md) | JGit vs libgit2 vs git24j, memory, coverage |
| 07 | [Security & Authentication Audit](07_SECURITY_AND_AUTHENTICATION_AUDIT.md) | Threat model, findings, risk levels |
| 08 | [Code Editor & Developer Tools](08_CODE_EDITOR_AND_DEVELOPER_TOOLS_AUDIT.md) | Editor, Tree-sitter, LSP, terminal |
| 09 | [Build, CI/CD & Testing](09_BUILD_CICD_AND_TESTING_AUDIT.md) | Gradle, workflows, tests, blockers |
| 10 | [License & Dependency Audit](10_LICENSE_AND_DEPENDENCY_AUDIT.md) | Licenses, obligations, combination scenarios |
| 11 | [HMX Git Feature Comparison](11_HMX_GIT_FEATURE_COMPARISON.md) | Requirement-by-requirement matrix |
| 12 | [Performance & Android Compatibility](12_PERFORMANCE_AND_ANDROID_COMPATIBILITY.md) | RAM, ABI, APK, large repos |
| 13 | [Final Repository Ranking](13_FINAL_REPOSITORY_RANKING.md) | Weighted scorecard and confidence |
| 14 | [Adoption & Enhancement Roadmap](14_HMX_GIT_ADOPTION_AND_ENHANCEMENT_ROADMAP.md) | Reuse, replace, build, phases |
| 15 | [Source Evidence Index](15_SOURCE_EVIDENCE_INDEX.md) | Path, symbol, line, classification |
| 16 | [Audit Limitations](16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md) | What was not verified |
| 17 | [Final Decision](17_FINAL_DECISION.md) | One-page decision record |

---

## 6. Verification status at a glance

| Evidence type | Nexora Git | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Source cloned and inspected | Yes | Yes | Yes (shallow) |
| CI runs observed on hosting platform | Yes (build/lint/test/release gate success) | Yes (PR build+unit tests success) | Workflows exist; not executed here |
| Public APK inspected | No (no release) | Yes (37,473,461 bytes) | Yes (release asset sizes only) |
| Local build executed in this audit | **No** — no Android SDK/NDK/Gradle in environment | **No** — same | **No** — same |
| Runtime/device behaviour | Not tested | Not tested | Not tested |

The absence of a local build is disclosed in every report where it matters. No build or test
result in this documentation is fabricated.

---

## 7. Method summary

1. Verified repository identity through the GitHub REST API and canonical clone URLs.
2. Cloned full history (Nexora, Gitling) into an isolated directory; no upstream writes.
3. Walked Gradle configuration, version catalogs, manifests, native CMake, Kotlin/Java/C++ sources,
   workflows, tests, docs and licenses.
4. Traced each HMX requirement to a concrete symbol, then classified the evidence
   (VERIFIED / PARTIALLY VERIFIED / DOCUMENTED ONLY / NOT FOUND / FAILED / UNKNOWN).
5. Checked CI conclusions per commit rather than trusting badges.
6. Downloaded and inspected the published Gitling APK (dex/lib composition).
7. Searched GitHub broadly for alternatives, then source-audited the only credible one (PuppyGit).

---

## 8. Rating scale used

- **Risk:** CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL
- **Evidence:** VERIFIED / PARTIALLY VERIFIED / DOCUMENTED ONLY / NOT FOUND / FAILED / UNKNOWN
- **Confidence:** High / Medium / Low

All scores in [`13_FINAL_REPOSITORY_RANKING.md`](13_FINAL_REPOSITORY_RANKING.md) are derived from
the evidence in reports 03–12, not from popularity.
