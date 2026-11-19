# 18 — Audit Limitations

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
>
> This report exists so that no reader mistakes the audit for something it is not. The limits below
> are **known, deliberate, and material**.

---

## 1. Environment constraints

| Constraint | Consequence |
| --- | --- |
| Audit host is a low-RAM Android device running Termux with `proot-distro login ubuntu` | No Android SDK/NDK, no Gradle build, no JDK 21 toolchain |
| User explicitly prohibited local builds | No APK/AAB was produced; no compile verification beyond CI evidence |
| No connected device, emulator or ADB | No install, launch, interaction, screenshot or Logcat |
| No Android Studio / Compose preview | No visual verification of any screen |
| No network access to the broker (none deployed) | OAuth flow not exercised end-to-end |

The audit therefore ran entirely as **read-only static analysis**: `git`, `find`, `grep`, `wc`,
file reading, and GitHub's public API.

---

## 2. What was not done

| Activity | Status | Impact on conclusions |
| --- | --- | --- |
| Gradle build (debug/release) | **Not done** | Compile correctness relies on external CI |
| Unit test execution | **Not done** | Test pass/fail relies on external CI |
| Native build (`cmake`) | **Not done** | Native build correctness relies on external CI |
| Native test execution (`ctest`) | **Not done** | Relies on external CI |
| ASan/UBSan run | **Not done** | Memory-safety conclusions are from source reading |
| Fuzzing | **Not done** | Fuzzing infrastructure is verified to exist; results are not |
| APK install / launch | **Not done** | **Crash not reproduced** |
| Logcat capture | **Not done** | Crash cause is static, not device-confirmed |
| Monkey/UIAutomator exploration | **Not done** | No dynamic UI coverage |
| OAuth flow with a real broker | **Not done** | Broker behavior inferred from Go source |
| GitHub API calls with a real token | **Not done** | API behavior inferred from client code |
| Git clone/fetch/push against GitHub | **Not done** | Engine behavior inferred from libgit2 calls |
| Memory profiling | **Not done** | Performance statements are complexity analysis |
| Startup timing measurement | **Not done** | No cold-start number |
| Accessibility scan (TalkBack, contrast) | **Not done** | Accessibility only spot-checked in source |
| Dependency CVE scan (OSV/Trivy/Grype) | **Not done** | No CVE list; Dependabot presence only |
| License/legal review of dependencies | **Not done** | Apache-2.0 project license noted only |
| Static analyzer run (Detekt/ktlint/Android Lint locally) | **Not done** | Relies on CI lint |
| Binary/APK inspection | **Not done** | No manifest merge result, no DEX inspection |
| Reproducible-build verification | **Not done** | Offline native job is close but unproven for Android |

---

## 3. Coverage limits by area

### 3.1 Kotlin

- 272 files were enumerated; **not every line was read**. Coverage was driven by:
  - the startup path (Application → Activity → Compose → ViewModels);
  - every file referenced by a finding;
  - systematic greps for risky patterns (`loadLibrary`, `!!`, `GlobalScope`, `runBlocking`,
    `Mutex`, `catch (Exception)`, `runCatching`, `apply()`, `commit()`, `System.loadLibrary`);
  - package-level sampling of the 20 `core/platform` files and 18 feature packages.

- **False negatives are possible** in feature code that was not opened line by line (for example
  `AdvancedGitHubScreen`, large parts of `GitWorkspaceScreen`, some `core/platform` helpers).

### 3.2 Native

- 8,713 LOC were reviewed at the level of JNI surface, callbacks, error paths, handle frees and
  string handling.
- **No exhaustive path-by-path proof** of memory safety was attempted. The absence of a finding is
  not proof of absence of a bug.
- Refcount/ownership correctness for every libgit2 call (there are hundreds) was not formally
  verified. The audit checked representative patterns (repository, remote, index, reference,
  signature, rebase, status list, conflict iterator) and flagged the one clear leak it found.

### 3.3 Gradle/AGP

- The configuration was read, not executed. AGP 9.4 / Gradle 9.6 / compileSdk 37 behavior is taken
  from the declared configuration and CI success, not from a local run.
- Manifest merging, resource shrinking output and R8 behavior were not inspected in a built artifact.

### 3.4 Runtime behavior

- No screen was seen. UI findings are based on source structure, not on rendered output.
- No animation, gesture, insets or edge-to-edge behavior was observed.
- No low-memory kill, configuration change, process death or WorkManager scheduling was exercised.

---

## 4. Evidence quality

| Evidence class | How it is labelled | Reliability |
| --- | --- | --- |
| Direct source read | file:line in report | High |
| Exhaustive grep (absence) | "no X found; grep empty" | High for the searched pattern, not for semantic absence |
| GitHub check-runs API | **[EXT]**, dated | High for the reported conclusion; not a local build |
| AppCompat/AndroidX source (upstream) | **[EXT]**, versioned | High for the guard mechanism |
| Reconstructed stack trace | explicitly labelled | **Not evidence**; an illustration |
| Complexity analysis | report 11 | Indicative, not measured |
| "Suspected" findings | status column | Source condition confirmed; outcome conditional |

---

## 5. Threats to validity

### 5.1 False positives

- A "Confirmed" finding means the code pattern exists and the path is reachable **as written**. It
  does not mean the harmful outcome was observed.
- Severity is judgment. Reasonable engineers may rate BUG-007 (default build cannot authenticate) as
  P0 "product unusable" rather than P1; the audit rates it P1 because the app still runs as a local
  Git client and the unconfigured state is intentional and safe.
- BUG-010 is marked Suspected precisely because the CA path may still exist on the user's device.

### 5.2 False negatives

- Files not read line by line may contain defects.
- The native engine may contain memory bugs that only ASan/fuzzing would reveal.
- Concurrency bugs (BUG-004 class) are timing-dependent and cannot be proven statically.
- Security issues requiring a live broker, a live token or a hostile network were not tested.

### 5.3 Snapshot bias

- The audit covers **one commit**. Upstream `main` may have advanced after 2026-10-09; the GitHub
  API showed `pushed_at` 2026-11-16 due to Dependabot, but `main` still pointed at `f4befb6` when
  cloned.
- Any fix merged after the clone is not reflected.

### 5.4 Tool bias

- Grep-based absence claims only cover the literal patterns searched. For example, "no `Mutex` in
  `core/git`" is strong evidence, but a custom lock class under a different name would not be found.
  The audit read the relevant files to corroborate.
- Line numbers shift with edits; they are valid only at the audited SHA.

---

## 6. What would materially raise confidence

| Action | Would confirm |
| --- | --- |
| `adb logcat` from the user's device in dark mode | BUG-001 as the actual crash |
| Launch on a light-mode device | Falsifies/confirms the theme-only hypothesis |
| Launch with a corrupted DB/DataStore | BUG-006/BUG-011 as a crash path |
| Build + install an APK with the `.so` removed (debug repackage) | BUG-003 |
| `./gradlew :app:assembleDebug` on a workstation | Local compile evidence |
| `ctest` + ASan on a workstation | Native memory evidence |
| `MigrationTestHelper` 1→4 | Migration correctness |
| A 10-minute macrobenchmark run | Startup and jank numbers |
| An OSV/Trivy scan | Dependency CVE list |
| A broker deployment + real OAuth | Auth end-to-end |

None of these was possible in this environment. The user can run the first four with the procedure
in report 15.

---

## 7. Scope exclusions

The audit did **not** cover:

- the Next.js website (`web/`) beyond noting its existence;
- the VPS installer scripts (`scripts/vps`) beyond CI presence;
- F-Droid/Play store metadata correctness;
- trademark/branding/legal suitability of "Nexora Git" for "HMX Git";
- the Go broker's production deployment, TLS termination, rate limiting or secret storage;
- GitHub App permission configuration in the GitHub UI;
- accessibility conformance (WCAG/Android a11y scanner);
- localisation completeness (only `en` and `es-MX` exist; strings were not diffed);
- upgrade paths from any *other* app (there is no prior release, so no real upgrade exists).

---

## 8. Honesty statement

1. **No build, install, launch, reproduction or Logcat occurred in this audit.**
2. The startup crash is **not reproduced**; the root cause is a **static, high-confidence inference**.
3. All CI statements are **external** and dated; they are not local results.
4. The reconstructed stack trace in report 14 is **illustrative**, not captured.
5. The 40 findings include **7 Suspected** items; they are not presented as proven runtime failures.
6. Where the audit did not verify something, this report says so rather than filling the gap with
   assumption.

Any downstream decision (fork, rebrand, release) should treat this audit as a **strong static
baseline**, not as a substitute for device QA.
