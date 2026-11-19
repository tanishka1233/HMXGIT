# Nexora Git — Complete Source Audit, Bug Hunt and Startup-Crash Investigation

**Audit target:** [Nexora Git](https://github.com/Gh0stDeveloper/Nexora-Git)
**Audited commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833`
**Branch:** `main`
**Commit date:** 2026-10-09
**Audit date:** 2026-11-19
**Mode:** read-only, source-only static audit.

> **No source file was modified, no dependency was changed, no package was renamed, no branding was changed, no file was deleted, and nothing was pushed upstream.**
>
> **No APK was built and no application was installed or launched.** The user explicitly prohibited local builds because the audit host is a low-RAM Android device running Termux with `proot-distro login ubuntu`. The startup crash therefore has **not been reproduced** and **no Logcat was captured by this audit**. See `18_AUDIT_LIMITATIONS.md`.

---

## 1. Purpose

This report set answers four questions:

1. What is actually inside Nexora Git — architecture, source inventory, build system, native engine, authentication, storage and UI?
2. Which defects are provable from the source code, and how severe are they?
3. Why does the application crash immediately after launch?
4. Can Nexora Git become a reliable foundation for **HMX Git**?

The answer to (4) is a conditional **yes**, gated on fixing the startup defect and the other P0/P1 items first. See `19_FINAL_TECHNICAL_ASSESSMENT.md`.

---

## 2. Headline finding

The strongest, source-provable explanation for an immediate crash on launch is a **platform-theme / AppCompat incompatibility in night mode**:

- `MainActivity` extends `AppCompatActivity` (`app/src/main/java/com/nexora/git/MainActivity.kt:26`).
- The default theme is a valid AppCompat theme (`app/src/main/res/values/styles.xml:3`).
- The **night** resource override replaces it with a framework theme:
  `app/src/main/res/values-night/styles.xml:3` →
  `<style name="Theme.NexoraGit" parent="android:style/Theme.Material.NoActionBar">`.
- AppCompat throws when the window decor is created if the theme does not provide the
  **AppCompat** attribute `windowActionBar`:
  *"You need to use a Theme.AppCompat theme (or descendant) with this activity."*

Because `MainActivity.onCreate()` calls `setContent { ... }` (`MainActivity.kt:40`), the failure occurs during the very first Activity creation, before Compose can render. On a device whose system UI is in dark mode this is deterministic. On a light-mode device it does not occur.

**This is a confirmed source defect. The runtime crash is unconfirmed because no device could be used.** The complete evidence chain, alternative hypotheses and falsification tests are in `14_STARTUP_CRASH_ROOT_CAUSE_ANALYSIS.md` and `15_CRASH_REPRODUCTION_AND_LOGCAT.md`.

### Findings at a glance

| Metric | Value |
| --- | ---: |
| Total findings | **40** (`BUG-001`–`BUG-040`) |
| Confirmed from source | **33** |
| Suspected / runtime-dependent | **7** |
| Critical (P0) | 2 |
| High (P1) | 8 |
| Medium (P2) | 17 |
| Low (P3) | 13 |

The full register with evidence and fix direction is `13_MASTER_BUG_AND_ERROR_REGISTER.md`.

---

## 3. Report index

| File | Contents |
| --- | --- |
| `00_AUDIT_OVERVIEW.md` | Scope, method, evidence rules, headline counts |
| `01_REPOSITORY_AND_SOURCE_INVENTORY.md` | Git identity, file counts, package map, LOC, dead code |
| `02_ARCHITECTURE_DEEP_AUDIT.md` | Layers, DI graph, startup path, navigation, coupling |
| `03_BUILD_AND_GRADLE_AUDIT.md` | Gradle/AGP/Kotlin/NDK, R8, signing, native build, CI evidence |
| `04_KOTLIN_JAVA_BUG_AUDIT.md` | Kotlin/Java defects with code and line numbers |
| `05_CPP_JNI_NATIVE_AUDIT.md` | JNI surface, reference handling, UTF, exceptions, leaks |
| `06_LIBGIT2_GIT_ENGINE_AUDIT.md` | Every Git operation, credentials, conflicts, LFS, limits |
| `07_GITHUB_API_AUTHENTICATION_AUDIT.md` | OAuth/PKCE/broker/token storage/refresh/multi-account |
| `08_SECURITY_VULNERABILITY_AUDIT.md` | Threat model, secrets, network, deep links, supply chain |
| `09_EDITOR_UI_NAVIGATION_AUDIT.md` | Screens, routes, editor, Tree-sitter, LSP, placeholder features |
| `10_STORAGE_DATABASE_LIFECYCLE_AUDIT.md` | Room v4, migrations, DataStore, SAF, process death |
| `11_PERFORMANCE_AND_MEMORY_AUDIT.md` | Startup cost, serial Git work, caches, monoliths |
| `12_TESTING_AND_CI_AUDIT.md` | 38 unit-test files, 24 instrumentation tests, 18 workflows |
| `13_MASTER_BUG_AND_ERROR_REGISTER.md` | Consolidated register of all findings |
| `14_STARTUP_CRASH_ROOT_CAUSE_ANALYSIS.md` | Ranked causes, exact failure mechanism, confidence |
| `15_CRASH_REPRODUCTION_AND_LOGCAT.md` | Reproduction matrix and exact ADB commands |
| `16_FIX_PRIORITY_AND_RECOMMENDATIONS.md` | P0–P3 remediation roadmap (no fixes applied) |
| `17_SOURCE_EVIDENCE_INDEX.md` | Claim → file:line evidence map |
| `18_AUDIT_LIMITATIONS.md` | What was not done and why |
| `19_FINAL_TECHNICAL_ASSESSMENT.md` | Foundation verdict for HMX Git |

---

## 4. Evidence rules used in this audit

- Every code claim cites a repository-relative path and a line number at commit `f4befb6`.
- Runtime claims are labelled **Confirmed (static)**, **Suspected**, or **Unverified**.
- CI results are **external** evidence from the GitHub check-runs API for the audited SHA, queried on 2026-11-19. They are not local build results.
- No stack trace is presented as captured unless it was captured. The reconstructed stack in report 14 is explicitly labelled as reconstructed from source.
- Absence-of-evidence is stated as such. The absence of a per-repository lock, for example, is evidenced by an exhaustive grep, not by assumption.

---

## 5. Fast path for the reader

1. If you only need the crash reason: read `14_STARTUP_CRASH_ROOT_CAUSE_ANALYSIS.md`.
2. If you need to prove it on your device: read `15_CRASH_REPRODUCTION_AND_LOGCAT.md`.
3. If you need the defect backlog: read `13_MASTER_BUG_AND_ERROR_REGISTER.md` and `16_FIX_PRIORITY_AND_RECOMMENDATIONS.md`.
4. If you need the adoption decision: read `19_FINAL_TECHNICAL_ASSESSMENT.md`.
