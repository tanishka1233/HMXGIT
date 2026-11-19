# 16 — Audit Limitations & Unverified Claims

This report exists so that no reader mistakes static evidence for proven behaviour. It lists what
was **not** done, what could **not** be done, and which statements in this audit set are
**inferences** rather than observations.

---

## 1. Environment limitations

| Constraint | Effect |
| --- | --- |
| **No Android SDK** (only a stray `build-tools/debian` directory) | Could not compile, lint, or package any app |
| **No NDK / CMake Android toolchain** | Could not build `native/git`, libgit2, Mbed TLS, Tree-sitter for any ABI |
| **No Gradle distribution matching the wrappers** | Could not run `./gradlew` for either project |
| **JDK 17 only** | Gitling requires **JDK 21** (`build.yml`, toolchain) |
| **No emulator / physical device** | No runtime, UX, memory, CPU, battery, or thermal observation |
| **No Android account / GitHub token** | Could not exercise OAuth, refresh, revocation, or live API calls |
| **No SSH server** | Could not verify SSH transport behaviour in Gitling/PuppyGit |
| **`dl.google.com` SDK endpoint unreachable for SDK install** | Even a partial SDK bootstrap was impossible |
| **PuppyGit cloned shallow (depth 200)** | Commit counts and full history analysis not performed |

> **Consequence:** **No build or test in this audit was executed.** All references to CI
> success/failure come from the hosting platform's public check-run API for specific commits, which
> is third-party evidence — strong, but not reproduced locally.

---

## 2. What WAS done (for contrast)

| Activity | Scope |
| --- | --- |
| Repository identity verification | GitHub REST API for all three repos (owner, URL, branch, license, stars, releases, contributors) |
| Full clone | Nexora (full), Gitling (full) |
| Shallow clone | PuppyGit (depth 200) |
| Source walk | Gradle files, version catalogs, manifests, CMake, Kotlin/Java/C++ sources, Room, workflows, tests, docs, licenses |
| Exact symbol tracing | 25 Git operations, GitHub endpoints, auth, editor, storage |
| CI conclusion retrieval | `check-runs` API for `b36c868`, `3d0f933`, `5ac3f73`, `f4befb6`, Gitling `3905914` |
| APK inspection | Gitling `v1.0.66` downloaded and inspected with `unzip -l` |
| License verification | Upstream `LICENSE`/`COPYING` texts fetched and read |
| Dependency pin verification | libgit2 `49e408b` → v1.9.7 via commit API |

---

## 3. Claims classified as inference (not observation)

| Claim | Basis | Real status |
| --- | --- | --- |
| "JGit uses more memory than libgit2 on Android" | Architecture reasoning + Gitling's `largeHeap` | **Estimate**; no measurement |
| "A 1 GB clone needs 200–400 MB on libgit2" | Reasoning from mmap/pack behaviour | **Estimate** |
| "Nexora release APK would be 10–25 MB" | Build config reasoning | **Estimate** |
| "Cold start is faster with baseline profiles" | Known Android behaviour + presence of profiles | **Estimate** (not profiled) |
| "Concurrent ops can corrupt the index" | libgit2 thread-safety contract + absence of a lock | **Strong inference**, not reproduced |
| "Conflict resolution loses one side" | `resolve_conflict` accepts only `ours`/`theirs` | **Verified code path**, UX consequence inferred |
| "Gitling viewer has no WebView hardening" | `setJavaScriptEnabled` not found by grep | **Possible false negative** (may be set elsewhere/dynamically) |
| "Nexora editor reparses on every change" | No debounce found | **Uncertain**; could be manual or throttled elsewhere |
| "Nexora has no storage quota policy" | No quota code found | **Absence of evidence** |

---

## 4. Documentation claims not verified

| Claim (source) | Status | Note |
| --- | --- | --- |
| Nexora "feature-modular architecture" (`docs/ARCHITECTURE.md`) | **Contradicted** | Only `:app` + `:baselineprofile` modules |
| Nexora LSP support (`core/editor/lsp/*`) | **DOCUMENTED ONLY** | Interface + codec; zero implementers |
| Nexora "production ready" | **Contradicted by maintainer** | Issue #57 "release NO-GO" |
| Nexora accessibility audit (`docs/ACCESSIBILITY_AUDIT.md`) | **DOCUMENTED ONLY** | Document exists; compliance not tested |
| Gitling "full branch/merge commit graph" | **PARTIALLY VERIFIED** | Code present; not visually tested |
| Gitling "self-hosted Forgejo/Gitea" | **PARTIALLY VERIFIED** | Per-host credentials present; no live server test |
| PuppyGit "Git LFS" | **PARTIALLY VERIFIED** | Implemented; author flag `lfsTestPassed=false` |
| PuppyGit "libgit2 1.9.4" (README) | **Contradicted** | Build script downloads 1.9.7 |
| Sora Editor "Tree-sitter/LSP" (upstream docs) | **Not audited** | Component not adopted; license review only |

---

## 5. Things that could change the conclusions

| If this were true… | …the effect |
| --- | --- |
| Nexora's Phase-0 build fails or the app crashes on launch | Recommendation switches to **Option E** |
| Nexora's `main` cannot be made green | Adoption cost rises; still likely ahead of GPL alternatives |
| A live GitHub App OAuth flow is broken | Auth must be replaced with Device Flow (already a fallback) |
| libgit2 SSH (libssh2) integration proves unstable | Engine choice may revert to JGit despite its Android issues |
| PuppyGit were relicensed to Apache/MIT | Ranking could change substantially (it would gain ~8 points of license weight share) |
| Gitling had a hidden GitHub API layer | Its score would rise, but source grep is exhaustive |
| A better 2026 candidate exists outside the searched terms | Discovery could miss it (see §6) |

---

## 6. Discovery coverage limits

- GitHub search is **relevance/star biased**; very new or unstarred repositories may not surface.
- Codeberg, GitLab and F-Droid-only projects were **not** searched systematically (F-Droid presence
  was checked for Gitling/PuppyGit via metadata only).
- Non-English project descriptions may be under-represented despite broad queries.
- The shortlist was screened by **metadata + source spot-checks**, not by cloning all 60 candidates.
- Only **one** alternative (PuppyGit) received a source audit; the others were rejected on clear
  categorical grounds (no Git engine, abandoned, wrong license, library-only).

> **Confidence in "no materially stronger candidate":** **High** for the categories searched
> (Android Git clients, GitHub clients, editors, KMP clients); **Medium** for the long tail of
> unlisted/obscure projects.

---

## 7. Security-audit limits

| Limitation | Impact |
| --- | --- |
| No dynamic analysis (no Frida, no emulator) | Runtime token handling, memory scraping, and WebView behaviour unverified |
| No network interception | TLS, pinning, MITM resilience unverified |
| No dependency CVE database scan executed | Only the presence of `dependency-review` CI was noted |
| No secret scanning of Git history beyond source grep | Historical secrets could exist in deleted branches/tags |
| No review of the Go broker deployment | `docs/AUTH_DEPLOYMENT.md` not validated against a live host |
| No review of prebuilt native binaries' provenance | PuppyGit `.so` files not reproducible here |

No real secrets are disclosed in this audit. Where logging defects are cited, only the statement and
location are identified, never the secret value.

---

## 8. Performance-audit limits

- **Zero** on-device measurements.
- All RAM/CPU/startup/battery numbers are **estimates** and labelled as such.
- APK sizes are measured only for Gitling (downloaded) and PuppyGit (release metadata); Nexora has
  no artifact.
- No large-repository clone was attempted (would require device + network + time).
- No low-storage or process-death test was run.

---

## 9. Legal-audit limits

- This is **not legal advice**.
- License texts were read for the main dependencies, but **transitive** dependency licenses were
  inventoried from knowledge of their standard terms, not audited file-by-file across every artifact.
- **Trademark** use, Play Store policy, and export-control questions were not reviewed by counsel.
- The libgit2 linking exception was read from `COPYING`; interpretation for a specific distribution
  model should be confirmed legally.

---

## 10. Summary of unverified claims in the audit set

| Report | Unverified element | Why |
| --- | --- | --- |
| 03 | Nexora runtime behaviour, device UI | No build/device |
| 04 | Gitling SSH/Forgejo live behaviour, viewer security settings | No server/device |
| 05 | PuppyGit full history, release provenance | Shallow clone |
| 06 | All memory/perf comparisons | Estimates |
| 07 | Broker deployment security, WebView settings | External/unbuilt |
| 08 | Editor performance, LSP (absent) | No device |
| 09 | Any local build/test | No toolchain |
| 10 | Transitive license completeness | Metadata + standard terms |
| 11 | "PARTIALLY VERIFIED" rows | No e2e |
| 12 | All RAM/CPU/startup figures | Estimates |
| 13 | Numeric sub-scores | Judgment bands |
| 14 | Phase durations | Planning estimates |
| 17 | Adoption gate outcomes | Future work |

---

## 11. What the next auditor/engineer must do first

1. Provision **JDK 17, Android SDK 37, NDK 27.2.12479018, CMake ≥ 3.22.1**.
2. Run the Phase-0 commands in [`09_BUILD_CICD_AND_TESTING_AUDIT.md`](09_BUILD_CICD_AND_TESTING_AUDIT.md) §6.
3. Install the resulting APK on **API 26** and **API 33** devices.
4. Clone a real repository over HTTPS and SSH; interrupt it; kill the app; re-open.
5. Measure memory for clone/log/status on a **3–4 GB** device.
6. Confirm the GitHub OAuth (or Device Flow) path against a real account.
7. Only then treat any "VERIFIED" engine claim in this audit as "verified **and** reproduced".

Until those steps are done, the recommendation in report 17 remains **conditional**.
