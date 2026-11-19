# 10 — License & Dependency Audit

**Disclaimer:** This is a technical licensing review, **not legal advice**. Final decisions must be
reviewed by qualified counsel, especially for app-store distribution and trademark use.

**Audit date:** 2026-11-19

---

## 1. Main repository licenses

| Repository | License (verified) | Evidence |
| --- | --- | --- |
| **Nexora Git** | **Apache License 2.0** | `LICENSE` first line; API `license.spdx_id = Apache-2.0` |
| **Gitling** | **GNU GPL v3.0** | `COPYING` first line; `LICENSE`; API `GPL-3.0` |
| **PuppyGit** | **GNU GPL v3.0** | `LICENSE`; API `GPL-3.0` |

---

## 2. What each license permits

### 2.1 Apache-2.0 (Nexora Git)

| Right / obligation | Permitted? | Notes |
| --- | --- | --- |
| Fork | **Yes** | |
| Modify | **Yes** | Must state changes in modified files |
| Rebrand | **Yes** | Cannot use upstream trademarks; must rename if trademark asserted |
| Distribute APKs | **Yes** | |
| Distribute source | **Yes** | |
| Commercial / closed-source | **Yes** | |
| Sublicense | Yes | |
| Patent grant | **Yes** (explicit) | Terminates on patent litigation |
| NOTICE requirements | **Yes** | Must retain `NOTICE` and attribution |
| Mark modified files | **Yes** | §4(b) |
| Warranty | None | |

### 2.2 GPL-3.0 (Gitling, PuppyGit)

| Right / obligation | Permitted? | Notes |
| --- | --- | --- |
| Fork / modify | Yes | |
| Distribute APKs | Yes, **only if** complete corresponding source is offered under GPL-3.0 | |
| Closed-source distribution | **No** | Copyleft propagates to the combined work |
| Commercial distribution | Yes, **if** GPL-3.0 obligations are met (source, notices, no extra restrictions) | Selling is allowed; closing is not |
| Rebrand | Yes (with source) | |
| Anti-tivoization | Applies | Installation information required for "User Products" |
| Patent grant | Yes | |
| Combined with Apache-2.0 code | **Yes, result is GPL-3.0** | Apache-2.0 is GPL-3.0-compatible (one-way) |
| Combined with GPL-2.0-only code | **No** | GPL-2.0-only is incompatible with GPL-3.0 |

> **Key legal fact:** You cannot take Gitling or PuppyGit source into HMX Git and keep HMX Git
> proprietary. Doing so makes the **entire distributed work** GPL-3.0.

---

## 3. Third-party dependency licenses

### 3.1 Native libraries

| Library | Version (Nexora) | License | Verified source | Notes for HMX Git |
| --- | --- | --- | --- | --- |
| **libgit2** | 1.9.7 (`49e408b…`) | **GPL-2.0 with linking exception** | upstream `COPYING` text: *"unlimited permission to link the compiled version of this library into combinations with other programs, and to distribute those combinations without any restriction coming from the use of this file"* | **Linking into Apache/proprietary HMX Git is permitted.** Modifying libgit2 itself still triggers GPL-2.0 for the modified library. |
| **Mbed TLS** | pinned `068ff08…` | **Apache-2.0 OR GPL-2.0** (dual) | project license page | Choose Apache-2.0 → permissive |
| **Tree-sitter** | pinned `752c612…` | **MIT** | API `license.spdx_id = MIT` | Permissive |
| Tree-sitter grammars (Kotlin/Java/JSON/Python/JS/TS/TSX) | pinned SHAs | **MIT** (typical) | grammar repos | Permissive; verify each grammar's LICENSE |
| **libssh2** | 1.11.1 (PuppyGit) | **BSD-3-Clause** | README + libssh2 `COPYING` | Permissive — safe to add to HMX Git |
| **OpenSSL** | (PuppyGit) | **Apache-2.0** | OpenSSL 3 license | Permissive |
| **JGit** | 7.3.0 (Gitling) | **Eclipse Distribution License 1.0 (EDL-1.0, BSD-3-clause style)** | upstream `LICENSE` | Permissive; combinable with GPL-3.0 or Apache-2.0 |
| **JSch (mwiede)** | 0.2.22 (Gitling) | **BSD-3-Clause** | `LICENSE.txt`: *"JSch 0.0.* was released under the GNU LGPL license. Later, we have switched over to a BSD-style license."* | Permissive; **the bundled 0.0.x-LGPL lineage does not apply to mwiede 0.2.22** |

> **Finding L-01 (critical clarification)** — **libgit2 is NOT plain GPL-2.0.** Its `COPYING` grants
> an explicit **linking exception**. Both Nexora (Apache-2.0) and PuppyGit (GPL-3.0) ship libgit2
> legally; HMX Git can link libgit2 from a proprietary app provided it does not modify libgit2 and
> distributes the license/notice. **VERIFIED.** Confidence: High.

> **Finding L-02** — Mbed TLS dual-licensed Apache-2.0/GPL-2.0; select **Apache-2.0**. **VERIFIED**
> (project licensing). Confidence: High.

### 3.2 JVM / Android dependencies (Nexora)

| Dependency | License |
| --- | --- |
| AndroidX (core, activity, lifecycle, navigation, compose BOM, room, work, datastore, documentfile, browser, benchmark, profileinstaller) | Apache-2.0 |
| Jetpack Compose (UI, Material 3, icons) | Apache-2.0 |
| Hilt / Dagger | Apache-2.0 |
| OkHttp / Retrofit | Apache-2.0 |
| org.json | JSON License (public domain-ish, permissive) |
| JUnit 4 | Eclipse Public License 1.0 |
| AndroidX Test / Espresso | Apache-2.0 |

### 3.3 JVM dependencies (Gitling, additional)

| Dependency | License |
| --- | --- |
| Conscrypt (`libconscrypt_jni.so`) | Apache-2.0 |
| CodeMirror (bundled JS) | MIT |
| highlight.js (bundled JS) | BSD-3-Clause |
| jQuery (bundled JS) | MIT |
| desugar_jdk_libs | Apache-2.0 |
| org.ow2.asm (build-time only) | BSD-3-Clause |
| Timber | Apache-2.0 |
| Stetho (debug only) | BSD-3-Clause |

### 3.4 Optional editor components

| Component | License | Implication |
| --- | --- | --- |
| **Sora Editor** (`Rosemoe/sora-editor`) | **LGPL-2.1** | Dynamic linking is permitted; **static linking or aggressive R8/obfuscation can trigger LGPL obligations** (provide means to relink / share library modifications). Requires legal review if HMX Git is proprietary. |
| **Squircle-CE** | **Apache-2.0** | Permissive; usable with attribution |

> **Finding L-03 (MEDIUM)** — Sora Editor's **LGPL-2.1** is the only dependency that conflicts with a
> fully proprietary HMX Git distribution model unless handled carefully (keep it as a separate,
> replaceable library module; do not statically merge it). **VERIFIED** (license via API). Confidence:
> High.

---

## 4. Attribution and NOTICE requirements

### 4.1 Nexora

`NOTICE` contains:

```
Nexora Git
Copyright 2026 Ghost Developer
Nexora Git is an independent open-source project and is not affiliated with,
endorsed by, or sponsored by GitHub, Inc.
"GitHub" and related marks belong to their respective owners.
```

HMX Git, if forked from Nexora:

- Must retain the `NOTICE` file and add its own attribution.
- Must state which files were modified (Apache-2.0 §4(b)).
- Must not imply endorsement by "Ghost Developer" or GitHub.
- Must distribute the Apache-2.0 license text.

### 4.2 Gitling / PuppyGit

GPL-3.0 requires:

- Full corresponding source for any distributed binary.
- Retain copyright notices and license text.
- Provide "Installation Information" for User Products (anti-tivoization).
- No additional restrictions on downstream recipients.

---

## 5. Combination scenarios (explicit)

| Scenario | Resulting license of HMX Git | Verdict |
| --- | --- | --- |
| **A. Fork Nexora (Apache-2.0) + link libgit2 (exception) + Mbed TLS (Apache) + Tree-sitter (MIT)** | **Apache-2.0 (or proprietary)** | **Clean.** Recommended. |
| B. Fork Nexora + copy Gitling GPL-3.0 code | **GPL-3.0** | Legal but forces copyleft |
| C. Fork Gitling (GPL-3.0) + add proprietary code | **GPL-3.0** | Cannot close source |
| D. Fork PuppyGit (GPL-3.0) | **GPL-3.0** | Forces copyleft |
| E. Independent composition: libgit2 + JGit + Compose + OkHttp + Sora (LGPL) | Apache-2.0 **if** Sora is dynamically linked with a relink path; otherwise legal review | Feasible |
| F. Nexora + Sora Editor (LGPL-2.1) | Apache-2.0 **provided** Sora remains a separate replaceable library and its modifications (if any) are published under LGPL-2.1 | Feasible with care |

> **Finding L-04 (HIGH)** — **Option D (combine Nexora + Gitling/PuppyGit source) is only legal if
> HMX Git adopts GPL-3.0.** A product decision, not a technical one. **VERIFIED** (license
> compatibility rules). Confidence: High.

> **Finding L-05** — Apache-2.0 → GPL-3.0 is a one-way compatible combination (Apache code may enter
> a GPL-3.0 work; GPL-3.0 code may not enter an Apache-only work). **VERIFIED.** Confidence: High.

---

## 6. Trademark and branding

| Concern | Note |
| --- | --- |
| "GitHub" name/logo | Owned by GitHub, Inc.; use only descriptively ("works with GitHub"); do not imply affiliation |
| "Git" mark | Generally descriptive; avoid a confusingly similar app name |
| Upstream app names/logos ("Nexora Git", "Gitling", "PuppyGit") | Must be replaced on fork/rebrand |
| App icon/assets | Nexora's icon (`ic_nexora_git`) is project-owned under Apache-2.0; still replace for branding |
| GitHub OAuth app name | Register HMX Git's own OAuth App (Device Flow or GitHub App) |
| Store listing | Cannot reuse upstream screenshots/marketing without permission |

> **Finding L-06** — Rebranding is legally permitted under both Apache-2.0 and GPL-3.0, but
> **trademark and attribution must be handled explicitly**. Confidence: High.

---

## 7. Dependency health and replacement options

| Dependency | Maintenance | Security note | Replacement option |
| --- | --- | --- | --- |
| libgit2 1.9.7 | Active (10.6k★, 2026-10 release) | CVE history exists; pin + monitor | JGit (pure JVM) |
| Mbed TLS | Active (7k★) | Dual license; strong | OpenSSL 3 / Conscrypt |
| Tree-sitter | Very active (27.5k★) | MIT; safe | — |
| OkHttp 5.5 / Retrofit 3.0 | Active | Safe | Ktor client |
| Hilt 2.60 | Active | Safe | Manual DI / Koin |
| Room 2.8.5 | Active | Safe | SQLDelight |
| JGit 7.3.0 | Active (439★ but Eclipse-backed) | EDL | libgit2 |
| JSch (mwiede) 0.2.22 | Active (1k★) | BSD | Apache MINA SSHD, libssh2 |
| Sora Editor | Active (1.4k★) | LGPL obligations | Squircle-CE (Apache-2.0) |
| CodeMirror (Gitling bundle) | Old bundled version | Unknown vintage | Replace with maintained CodeMirror 6 or Sora |

> **Finding L-07 (MEDIUM)** — Gitling bundles **old JavaScript** (CodeMirror compressed + jQuery) whose
> versions are not pinned in Gradle and are hard to audit for CVEs. **VERIFIED (bundled assets).**
> Risk: MEDIUM.

---

## 8. License verdict for HMX Git

| Decision point | Recommendation |
| --- | --- |
| Base license | **Fork Nexora Git under Apache-2.0** — preserves freedom to keep HMX Git proprietary if desired |
| libgit2 | Link as-is; do **not** modify; ship `COPYING` + linking-exception notice |
| Mbed TLS | Use Apache-2.0 option |
| Tree-sitter + grammars | MIT; attribution in NOTICE |
| libssh2 (if added) | BSD-3-Clause; attribution |
| JGit (if ever used) | EDL-1.0; attribution |
| Sora Editor (if used) | Keep as a **separate, dynamically linked, replaceable** module; publish any modifications under LGPL-2.1 |
| Gitling/PuppyGit code | **Do not copy** unless HMX Git accepts GPL-3.0; use as *reference* only |
| Attribution | Maintain `NOTICE`; mark modified files; include all third-party license texts |
| Trademark | New name, new icon, new OAuth app, no implied affiliation |

**Net position:** **Nexora Git + libgit2/Mbed TLS/Tree-sitter is a clean Apache-2.0 chain.** Gitling
and PuppyGit are GPL-3.0 and would force HMX Git's license. This is the second-strongest reason
(after functional scope) to prefer Option A.
