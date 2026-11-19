# 02 — Repository Identity & Maturity

**Verification date:** 2026-11-19
**Method:** GitHub REST API + fresh `git clone` + `git log` (no reliance on README claims)

---

## 1. Verified identity

### 1.1 Nexora Git

| Field | Verified value | Evidence |
| --- | --- | --- |
| Canonical URL | `https://github.com/Gh0stDeveloper/Nexora-Git` | API `html_url` |
| Clone URL | `https://github.com/Gh0stDeveloper/Nexora-Git.git` | API `clone_url` |
| Owner | `Gh0stDeveloper` | API `full_name` |
| Default branch | `main` | API `default_branch` |
| HEAD commit | `f4befb6c1954d956f0a67b14f4ab1642aa185833` | `git log -1` |
| HEAD date | 2026-10-09T03:18:46-06:00 | `git log -1 --format=%aI` |
| Repository pushed_at | 2026-11-16T07:56:33Z | API |
| Created | 2026-10-04T07:38:24Z | API |
| License (detected) | Apache-2.0 | API + `LICENSE` |
| Stars / forks | 4 / 0 | API |
| Open issues + PRs | 18 | API `open_issues_count` |
| Repository size | 2,284 KiB (packed) | API |
| Commits on `main` | 409 | `git rev-list --count HEAD` |
| Contributors | 1 (`Ghost Developer`) | API contributors (incl. anonymous) |
| Releases | **0** | API releases |
| Tags | **0** | API tags |
| Archived / disabled | No / No | API |

**Status:** pre-release. The maintainer's own gate issue
[#57](https://github.com/Gh0stDeveloper/Nexora-Git/issues/57) is titled
*"S.18 — Signed release candidate, production VPS and real-device acceptance (release NO-GO)"*
and states: *"Do not call the project production ready based solely on successful source CI."*
It lists unchecked items for VPS/domain, OAuth client, signing key, real-device QA, screenshots
and store distribution.

**Branch topology:** 59 remote branches, mostly `phase-*` development branches plus Dependabot
branches. The phase names (`phase-a-android-foundation` … `phase-s/p2-launch-polish`) map to the
documented delivery plan.

**Anomaly worth noting:** `pushed_at` (2026-11-16) is later than the `main` HEAD (2026-10-09)
because Dependabot force-pushes branches. Dependency-update runs exist for 2026-11-16, but no
feature commit landed on `main` after 2026-10-09.

---

### 1.2 Gitling

| Field | Verified value | Evidence |
| --- | --- | --- |
| Canonical URL | `https://github.com/maneeshacooray/Gitling` | API `html_url` |
| Clone URL | `https://github.com/maneeshacooray/Gitling.git` | API `clone_url` |
| Owner | `maneeshacooray` | API `full_name` |
| Default branch | `master` | API `default_branch` |
| HEAD commit | `390591428d17e0ddf324ad1dd9ec5f20f45909bc` | `git log -1` |
| HEAD date | 2026-11-17T07:41:59+05:30 | `git log -1 --format=%aI` |
| HEAD subject | `Merge branch 'develop' into master: v1.0.66` | `git log -1` |
| Repository created | 2026-03-07T12:54:29Z | API |
| Pushed | 2026-11-17T02:12:08Z | API |
| License (detected) | GPL-3.0 | API + `LICENSE` + `COPYING` |
| Stars / forks | 130 / 8 | API |
| Open issues | 2 (#118 password-based servers, #56 SSL connection issues) | API |
| Repository size | 23,338 KiB (packed) | API |
| Commits | 1,433 | `git rev-list --count HEAD` |
| Contributors | 64 (incl. SGit/MGit lineage) | API |
| Releases | **68** | API releases |
| Latest release | `v1.0.66`, 2026-11-17, APK `app-release.apk` = 37,473,461 bytes | API |
| Distribution | Google Play + F-Droid + GitHub Releases | README badges, F-Droid package id |

**History and provenance (verified):**

- The first commit is `ec194e3` dated **2013-08-17** (`[init repo] first commit`).
- The README states it is *"a fork of MGit, itself a continuation of SGit"*, and the root Gradle
  project is still named `MGit`. Package roots are `me.sheimi.sgit` (SGit) and
  `com.manichord.mgit` (MGit).
- `git shortlog` shows the original authors: sheimi (87 commits), Reason Zhang (100),
  Maksim Lin (375), plus Maneesha Cooray (581 in this repository).
- Inherited upstream tags remain, e.g. `v1.7.0` dated **2023-01-03**
  (`Merge pull request #668 from maks/bump-version-for-FDroid`). Gitling's own versioning
  restarts at `v1.0.x`.

**Maturity:** production app with continuous releases. Release cadence over the last 60 days:
v1.0.61 (2026-09-23), v1.0.62 (2026-09-30), v1.0.63 (2026-10-19), v1.0.64 (2026-10-19),
v1.0.65 (2026-10-20), v1.0.66 (2026-11-17).

---

### 1.3 PuppyGit (discovery shortlist)

| Field | Verified value |
| --- | --- |
| URL | `https://github.com/catpuppyapp/PuppyGit` |
| Default branch | `main` |
| HEAD | `00657bd0ec6513b15ecc43e24676d0966464d97e` (2026-11-17) |
| Created | 2024-10-29 |
| License | GPL-3.0 |
| Stars / forks | 915 / 159 |
| Latest release | `1.1.5.10v136`, 2026-11-17, 5 APK assets (13.2–22.9 MB) |
| Distribution | GitHub Releases + IzzyOnDroid |

---

## 2. Activity analysis

### 2.1 Commit concentration

| Repository | Commits | Distinct authors (top-level `shortlog`) | Bus factor |
| --- | --- | --- | --- |
| Nexora Git | 409 | 1 | **1** |
| Gitling | 1,433 | 64 | Low (Maneesha Cooray 581, Maksim Lin 375, Reason Zhang 100, sheimi 87) |
| PuppyGit | not counted (shallow clone) | 1 primary maintainer + translators | 1 |

Nexora's 409 commits were produced in **5 days** (2026-10-04 → 2026-10-09) with phase-level
squash merges; commit timestamps show automated, sequential phase delivery. This is consistent
with a rapid, possibly assisted, greenfield build rather than years of field hardening — a
maturity risk, not a code-quality verdict.

### 2.2 Release evidence

| Repository | Releases | Signed APK verified in this audit | Store presence |
| --- | --- | --- | --- |
| Nexora Git | 0 | No | None |
| Gitling | 68 | Downloaded and inspected (see §4) | Play + F-Droid |
| PuppyGit | many (≥ 5 recent) | Asset sizes checked | IzzyOnDroid |

---

## 3. Issue and maintenance signals

### Nexora Git (18 open items)

Most are Dependabot PRs. The substantive open items are release gates:

- #57 S.18 signed RC / VPS / device acceptance — **NO-GO**
- #50 S.17 F-Droid fully-offline qualification
- #49 Play Console assets and Data safety
- #47 official screenshots "after VPS and app readiness"
- #46 production homepage
- #44 launch metadata

This is an unusually honest project-management posture: the maintainer explicitly refuses to
present mockups or unverified builds.

### Gitling (2 open issues)

- #118 "Difficulties with password based git servers" (2026-10-29)
- #56 "SSL connection issues" (2026-08-21)

Only two open issues against 130 stars and 68 releases is a strong maintenance signal, though it
may also indicate issue triage/filtering; no evidence of the latter was found.

---

## 4. Published artifact inspection (Gitling)

The v1.0.66 APK was downloaded from the official release and inspected with `unzip -l`:

| Artifact | Size | Observation |
| --- | --- | --- |
| `classes.dex` | 30,049,160 B | Very large main dex — minification disabled |
| `classes4.dex` | 13,010,772 B | multiDex in use |
| `classes5.dex` | 8,509,448 B | |
| `classes6.dex` | 9,678,516 B | |
| `classes7.dex` | 7,149,920 B | 7 dex files total |
| `lib/arm64-v8a/libconscrypt_jni.so` | 2,754,312 B | Conscrypt per ABI |
| `lib/armeabi-v7a/libconscrypt_jni.so` | 1,776,996 B | |
| `lib/x86/libconscrypt_jni.so` | 2,781,268 B | x86 included |
| `lib/x86_64/libconscrypt_jni.so` | 2,983,040 B | x86_64 included |
| `lib/*/libandroidx.graphics.path.so` | ~7–10 KB each | AndroidX path |
| **Total APK** | **37,473,461 B (35.7 MiB)** | No R8 shrinking |

This is a measured fact, not an estimate, and is used in report 12.

---

## 5. Maturity verdict

| Dimension | Nexora Git | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Age of codebase | 6 weeks | 13 years of lineage | 2 years |
| Public releases | 0 | 68 | many |
| Real-device evidence | None (maintainer says pending) | Store distribution implies real use | Store/IZZY distribution |
| Contributor base | 1 | 64 | 1 primary |
| Documentation | Extensive, sometimes ahead of code | Practical README/CHANGELOG | Extensive but informal |
| Sustainability | **High risk** | **Proven** | Medium |

Nexora is a *promising architecture*, not a *proven product*. Gitling is a *proven product* with a
narrow feature envelope. This distinction drives the final recommendation's mandatory gate.
