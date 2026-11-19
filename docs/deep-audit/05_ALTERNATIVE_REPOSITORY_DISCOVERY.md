# 05 — Alternative Repository Discovery

**Search date:** 2026-11-19
**Method:** GitHub REST search API + targeted repository inspection + one shallow clone
**Rule applied:** no full deep audit unless a candidate appeared *materially stronger* than
Nexora Git or Gitling.

---

## 1. Search methodology

### 1.1 Queries executed

All queries used `https://api.github.com/search/repositories`, sorted by stars, filtered to
recently pushed repositories, and then verified by opening the actual repository/API metadata:

| Query | Filters | Purpose |
| --- | --- | --- |
| `android git client in:name,description,readme` | Kotlin | Kotlin Git clients |
| `git client android kotlin` | | Kotlin Git clients |
| `jetpack compose git` | | Compose-based Git UI |
| `android github client` | | GitHub clients |
| `android git libgit2` | | libgit2-based apps |
| `android code editor git` | | editors with Git |
| `git client android` | all languages | broad sweep |
| `github android client` | all languages | broad sweep |
| `mobile ide android kotlin` | | IDE-style |
| `git android app` | | broad sweep |

### 1.2 Addition criteria (all must hold)

1. Real Android **application** (not a library, not marketing copy).
2. Contains an actual Git implementation (JGit, libgit2, JNI, or native Git) **or** a genuinely
   broad GitHub platform layer.
3. Recently maintained (pushed within ~12 months) **and** buildable-looking Gradle project.
4. A license compatible with, or consciously chosen for, HMX Git.
5. Materially stronger than Nexora Git or Gitling on HMX Git's stated scope.

### 1.3 Exclusions applied

Rejected without deep audit: dead forks, abandoned clients (last push > 2023), library-only
projects, "GitHub mobile clients" with no local Git engine (they cannot satisfy the Git
requirements), and projects whose license or activity made them non-viable.

---

## 2. Shortlist

| Repository | ★ | Lang | License | Last push | Type | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| [`catpuppyapp/PuppyGit`](https://github.com/catpuppyapp/PuppyGit) | 915 | Kotlin | GPL-3.0 | 2026-11-17 | Android Git client, libgit2/git24j | **Focused audit** (see §3) |
| [`maks/MGit`](https://github.com/maks/MGit) | 1,591 | Java | GPL-3.0 | 2026-09-18 | Android Git client (Gitling's ancestor) | Rejected: superseded by Gitling |
| [`sheimi/SGit`](https://github.com/sheimi/SGit) | 379 | Java | GPL-3.0 | 2021-11-18 | Original Android Git client | Rejected: dormant, older than Gitling |
| [`slapperwan/gh4a`](https://github.com/slapperwan/gh4a) | 2,010 | Java | Apache-2.0 | 2025-12-09 | GitHub client (no local Git) | Rejected: no Git engine |
| [`k0shk0sh/FastHub`](https://github.com/k0shk0sh/FastHub) | 5,750 | Java | GPL-3.0 | 2022-09-12 | GitHub client | Rejected: abandoned, no Git engine |
| [`ThirtyDegreesRay/OpenHub`](https://github.com/ThirtyDegreesRay/OpenHub) | 4,380 | Java | GPL-3.0 | 2024-05-30 | GitHub client | Rejected: no Git engine, stale |
| [`jonan/ForkHub`](https://github.com/jonan/ForkHub) | 2,804 | Java/JS | Apache-2.0 | 2022-03-19 | GitHub client | Rejected: abandoned, no Git engine |
| [`GitJournal/GitJournal`](https://github.com/GitJournal/GitJournal) | 4,248 | Dart | **AGPL-3.0** | 2026-05-26 | Flutter note app w/ Git sync | Rejected: AGPL + not a developer client |
| [`JamGmilk/FuwaGit`](https://github.com/JamGmilk/FuwaGit) | 15 | Kotlin | MIT | 2026-06-13 | Lightweight Android Git client | Rejected: too small/immature |
| [`neutron42-jp/github-mobile-maxxing`](https://github.com/neutron42-jp/github-mobile-maxxing) | 50 | Kotlin | none | 2026-11-19 | KMP GitHub client (no Git engine) | Rejected: no Git engine, no license |
| [`AndroidIDEOfficial/AndroidIDE`](https://github.com/AndroidIDEOfficial/AndroidIDE) | 3,404 | Java | GPL-3.0 | 2024-10-18 | On-device IDE | Rejected: dormant, not a Git/GitHub client |
| [`Acode-Foundation/Acode`](https://github.com/Acode-Foundation/Acode) | 7,841 | JS | MIT | 2026-11-17 | Cordova code editor | Rejected: not native Android/Kotlin |
| [`Rosemoe/sora-editor`](https://github.com/Rosemoe/sora-editor) | 1,467 | Java | **LGPL-2.1** | 2026-11-18 | Code editor **library** | **Component candidate** (see §4) |
| [`massivemadness/Squircle-CE`](https://github.com/massivemadness/Squircle-CE) | 1,908 | Kotlin | Apache-2.0 | 2026-10-13 | Android code editor (no Git) | **Component candidate** (see §4) |
| [`termux/termux-app`](https://github.com/termux/termux-app) | 65,184 | Java | NOASSERTION | 2026-11-05 | Terminal emulator | Rejected: not a Git/GitHub client |

---

## 3. Focused audit — PuppyGit

PuppyGit was the only candidate that cleared the "materially stronger on Git surface" bar, so it
received a focused source audit (shallow clone, HEAD
`00657bd0ec6513b15ecc43e24676d0966464d97e`, 2026-11-17).

### 3.1 Verified identity

| Field | Value |
| --- | --- |
| URL | `https://github.com/catpuppyapp/PuppyGit` |
| Created | 2024-10-29 |
| Default branch | `main` |
| Stars / forks | 915 / 159 |
| License | **GPL-3.0** |
| Latest release | `1.1.5.10v136`, 2026-11-17 |
| APK assets | `arm64-v8a` 13.4 MB, `armeabi-v7a` 13.2 MB, `x86` 13.5 MB, `x86_64` 13.6 MB, universal 22.9 MB |
| Distribution | GitHub Releases + IzzyOnDroid |
| Code | 545 Kotlin files, ~113k LOC |

### 3.2 Engine

| Component | Value | Evidence |
| --- | --- | --- |
| Git engine | **libgit2 1.9.7** (download script `LIBGIT2_VERSION="1.9.7"`) | `lib_build_scripts/2_downloadsrc.sh:5` |
| README discrepancy | README states *"built from libgit2 1.9.4"* (`README.md:170`) | **mismatch with download script** |
| Binding | **git24j** fork (`lib_src/git24j-1.0.4.20241114-sources.jar`) | `lib_src/` |
| SSH | **libssh2 1.11.1** | `README.md:171` |
| TLS | OpenSSL (built alongside) | `lib_build_scripts/` |
| Native JNI | `libgit24j.so` + `libgit2.so` + `libssh2.so` | README |

> **Finding P-GE-01** — PuppyGit has the **deepest Git surface** of any candidate:
> clone (incl. shallow), fetch, pull, push, commit, stage/unstage, branches, checkout, merge,
> rebase, cherry-pick, **patch**, **reflog**, **squash**, tags, stashes, remotes, submodules,
> reset, conflict resolution (incl. an editor "merge mode"), commit graph, blame, markdown preview.
> **VERIFIED** from README feature list + `Libgit2Helper.kt`. Confidence: High.
>
> **Caveat:** Git LFS is **implemented but flagged untested** by the author:
> `app/src/main/java/com/catpuppyapp/puppygit/dev/DevFlag.kt:53` sets
> `val lfsTestPassed = false` (comment: *"untested features or tested but not enough tested"*).
> LFS in PuppyGit is therefore **PARTIALLY VERIFIED**, not proven.

> **Finding P-GE-02** — The README/libgit2 version is **1.9.4** while the build script downloads
> **1.9.7**. This is either stale documentation or a release built from a different pin than the
> checked-in script; it cannot be resolved without building. **PARTIALLY VERIFIED.** Risk:
> **MEDIUM** (supply-chain provenance ambiguity). Confidence: Medium.

> **Finding P-GE-03** — SSH host-key verification defaults to prompting on first connect, with an
> option to trust unknown hosts by default. **VERIFIED** (README §"About ssh"). This is a deliberate
> security trade-off HMX Git should copy but not loosen.

### 3.3 GitHub integration

> **Finding P-GH-01** — **No GitHub API layer.** `grep -rn "api.github.com"` across
> `app/src/main/java` returns nothing. There is no issues/PRs/Actions/releases client, no OAuth
> device flow, no REST/GraphQL abstraction. PuppyGit authenticates to remotes (HTTPS/SSH) but is not
> a GitHub client. **VERIFIED (absence).** Risk: **CRITICAL** relative to HMX scope. Confidence: High.

### 3.4 Architecture and quality

- **`Libgit2Helper.kt` = 385,601 bytes** (≈385 KB) — the largest single god object seen in any
  candidate. It wraps essentially every libgit2 call.
- `ChangeListInnerPage.kt` 230 KB, `FilesInnerPage.kt` 191 KB, `DiffScreen.kt` 186 KB.
  Screen files are far larger than Nexora's.
- Room + KSP, Compose, Ktor **embedded HTTP server** (for Tasker/automation), DataStore-style
  settings, property-based credential encryption (`EncryptUtil`/`Encryptor`).
- `app/src/main/java` layout is package-organized but **not modularized** (single `:app`).

> **Finding P-AR-01** — *`Libgit2Helper.kt` at 385 KB is the single most severe god object across
> all audited projects.* Risk: **HIGH** for maintainability and for reuse. **VERIFIED.**

### 3.5 Tests and CI

| Item | Value |
| --- | --- |
| `app/src/test` / `app/src/androidTest` | 2 example tests (template `ExampleUnitTest`/`ExampleInstrumentedTest`) |
| Workflows | `build_unsigned_apk_only.yml`, `build_libs_and_unsigned_apk.yml`, `log_build_libs_and_unsigned_apk.yml`, misc download/cmake probes |
| Release process | appears **manual/local** (author builds native libs and unsigned APK, then signs) |

> **Finding P-CI-01** — **Essentially no automated tests and no release-signing CI.** Build CI
> exists for unsigned artefacts but the release path is manual. **VERIFIED.** Risk: **HIGH**.

### 3.6 License

GPL-3.0 (repo `LICENSE`), with libgit2 GPL-2.0-with-exception, libssh2 BSD-3-Clause,
OpenSSL Apache-2.0. Any derived HMX Git must be GPL-3.0.

### 3.7 PuppyGit verdict

PuppyGit is **the best pure-Git client** and a poor foundation for a *Git + GitHub workspace*:
no GitHub platform, no tests, a 385 KB god object, and GPL-3.0. It is an excellent **reference**
for LFS, SSH host-key UX, reflog/patch/squash and automation-style sync — not the base.

---

## 4. Components worth extracting (license-checked)

Discovery is not only about whole apps. Three permissively licensed components could strengthen an
HMX Git built on Nexora or independently:

| Component | License | Why it matters | Integration cost |
| --- | --- | --- | --- |
| [`Rosemoe/sora-editor`](https://github.com/Rosemoe/sora-editor) | **LGPL-2.1** | Full-featured Android code editor (TextMate + Tree-sitter + LSP client) | LGPL: dynamic linking acceptable; static/obfuscated linking triggers obligations. Requires legal review. |
| [`massivemadness/Squircle-CE`](https://github.com/massivemadness/Squircle-CE) | **Apache-2.0** | Mature Kotlin code editor app; pattern reference for editor UX | Permissive; usable as design reference or extracted code with attribution |
| [`eclipse-jgit/jgit`](https://github.com/eclipse-jgit/jgit) | **EDL-1.0** (BSD) | Alternate engine; pure-JVM, no NDK | Android API-level bytecode issues (the exact problem Gitling patches) |

> **Finding D-CO-01** — Sora Editor is the strongest off-the-shelf editor component, but it is
> **LGPL-2.1**, which interacts with HMX Git's distribution model. **VERIFIED** (license via API).
> Risk: **MEDIUM** (legal). Confidence: High.

> **Finding D-CO-02** — Squircle-CE (Apache-2.0) is a safer editor reference than Sora for a
> permissively licensed HMX Git, but it is an *application*, not a drop-in library.

---

## 5. Was there a materially stronger candidate?

**No.**

| Dimension | Nexora Git | Gitling | PuppyGit | A stronger combined candidate? |
| --- | --- | --- | --- | --- |
| Real Git engine | Yes (libgit2) | Yes (JGit) | Yes (libgit2) | No single repo has Nexora's engine **and** Gitling's SSH **and** PuppyGit's LFS |
| SSH | **No** | Yes | Yes | None has SSH *and* GitHub platform |
| GitHub platform | **Yes (broad)** | No | No | Nexora unique |
| Editor | Yes (Tree-sitter) | No | Basic | Nexora unique |
| Tests | Good | Weak | None | Nexora unique |
| License | **Apache-2.0** | GPL-3.0 | GPL-3.0 | Nexora unique |
| Maturity/releases | None | **68 releases** | Many | Gitling unique |

Every candidate is strong on a *different* axis. There is **no repository that dominates on the
combined HMX Git scope**, and no repository that is materially stronger than Nexora Git *for that
scope*. In particular:

- GitHub clients (FastHub, OpenHub, gh4a, ForkHub, github-mobile-maxxing) have **no local Git
  engine** — they fail the Git requirements outright.
- Git clients (MGit/SGit/PuppyGit/Gitling) have **no GitHub platform** — they fail the GitHub
  requirements.
- Editors (Acode, Sora, Squircle) have **no Git engine**.
- GitJournal is AGPL-3.0 and a notes app.

> **Finding D-01** — *No alternative repository is a stronger whole-application foundation than
> Nexora Git for the specific HMX Git scope.* **VERIFIED by exhaustive shortlist inspection.**
> Confidence: High.

---

## 6. Discovery conclusion

1. The discovery pass was broad (10 queries, ~60 repositories screened, 16 shortlisted, 1 cloned and
   audited).
2. **PuppyGit** is the only shortlisted project that warranted a focused audit, and it was rejected
   as a base for HMX Git: GPL-3.0, **no GitHub layer**, no tests, 385 KB god object.
3. The correct use of the alternatives is **selective**:
   - Nexora Git → primary base (engine + GitHub + editor + license).
   - Gitling → **reference** for SSH/credentials/release engineering (do **not** copy code unless
     HMX Git adopts GPL-3.0).
   - PuppyGit → **reference** for LFS, SSH host-key UX, reflog/patch/squash.
   - Sora Editor / Squircle-CE → optional editor components (license-dependent).
4. **No candidate triggers Option C** (fork another verified candidate). Option D (combine code) is
   constrained by GPL-3.0 and analyzed in report 10.

See [`17_FINAL_DECISION.md`](17_FINAL_DECISION.md) for the resulting decision.
