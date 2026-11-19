# 20 — GitHub Actions Baseline Verification

> **Repository:** https://github.com/tanishka1233/HMXGIT
> **Branch:** `main`
> **Verified baseline commit:** `3f8aa6c55f5a49d85eee90ccf66313fffe07c068`
> **Prior verification commit:** `354a742c8baf173e40a444e4b06c60d81c464ea5`
> **Verification date:** 2026-11-19 (UTC)
> **Mode:** CI observation + one CI-only workflow trigger change. **No application source code,
> Gradle, native code or assets were modified.**

---

## 1. Decision

| Question | Answer |
| --- | --- |
| Baseline green? | **YES**, after excluding one non-applicable workflow by owner decision |
| Applicable workflows verified | **12 of 18** (11 pass + 1 excluded) |
| Failing workflows | **0 applicable** |
| Excluded by distribution decision | **1** — `Render Play Store artwork` |
| Non-applicable / not triggered | **5** (PR-only, manual-only or tag-only) |
| Application source editing authorised? | **Not yet — awaiting explicit go-ahead** |

---

## 2. Repository state

| Check | Result |
| --- | --- |
| `git branch --show-current` | `main` |
| Local HEAD | `3f8aa6c55f5a49d85eee90ccf66313fffe07c068` |
| Remote `refs/heads/main` | `3f8aa6c55f5a49d85eee90ccf66313fffe07c068` |
| Working tree | clean (report file excluded from this check — see §10) |
| `origin` | `https://github.com/tanishka1233/HMXGIT.git` |

History:

```
3f8aa6c  ci: make Play Store artwork verification manual-only (direct-APK distribution, not Play Store)
354a742  ci: import Nexora Git CI workflows at f4befb6… (unmodified)
3b10d26  vendor: import Nexora Git source tree at f4befb6… 
4bd0181  docs: Nexora Git complete source audit (f4befb6) and next-step recommendation
```

---

## 3. Owner decision — Play Store artwork excluded

HMX Git is distributed as a **directly installed APK** and will **not** be published on Google
Play. `Render Play Store artwork` verifies Play Store listing graphics only; it has no relationship
to the Android build, the APK, native code, tests or security. It is therefore **not applicable** to
this project's distribution strategy.

The workflow file was **kept** (not deleted) and the committed artwork was left untouched, so store
work can be resumed later by running it manually.

---

## 4. Why it ran on every push

The original trigger block was:

```yaml
on:
  push:
    branches: [main]
    paths:
      - "docs/store/artwork/**"
      - "fastlane/metadata/android/**/images/*.png"
      - "scripts/ci/validate-s17-store-artwork.py"
      - ".github/workflows/render-store-artwork.yml"
  pull_request:
    branches: [main]
    paths:  # same four paths
  workflow_dispatch:
```

It fired on the import commit because that commit **added every one of those paths** (the whole
upstream tree, including store artwork and the workflow itself). It also watches its own file, so
any edit to it re-triggers it.

The job re-renders the SVGs with an **unpinned** `librsvg2-bin` + `fonts-dejavu-core` on
`ubuntu-latest` and then requires a **byte-identical** PNG via `cmp`. On 2026-11-19 it failed
deterministically at byte `85848` of `en-US/featureGraphic.png` on two attempts, because the runner
image's renderer/font stack has drifted since the PNGs were generated. The committed assets are
byte-identical to upstream `f4befb6` (verified by SHA-256), so the failure was never caused by this
repository's code.

---

## 5. Change applied (CI only)

A single file was edited: `.github/workflows/render-store-artwork.yml`. Both automatic triggers were
removed; the workflow is now manual-only.

```yaml
name: Render Play Store artwork

# NOT APPLICABLE to HMX Git's distribution strategy: the project is shipped as a
# directly-installed APK and is not published on Google Play, so Play Store
# artwork verification is not part of the required CI baseline.
#
# Kept for reference only; no automatic push or pull-request trigger.
# Run manually through workflow_dispatch if store listing work is ever resumed.
on:
  workflow_dispatch:
```

Committed as `3f8aa6c`. No other workflow was touched. No build, test, lint, native, security or
code-quality check was removed or weakened.

---

## 6. Status of all 18 workflows

Statuses: **PASS** = a run on the applicable commit concluded `success`; **EXCLUDED** = deliberately
removed from the automatic baseline by owner decision; **NOT APPLICABLE** = trigger conditions
cannot be met by a push to `main`; **NOT TRIGGERED** = dispatchable but not run.

| # | Workflow | Trigger | Status | Evidence |
| ---: | --- | --- | --- | --- |
| 1 | Android CI | push/PR, path-filtered | **PASS** | run 44538536172 @ `354a742` |
| 2 | Auth Broker CI | push/PR, path-filtered | **PASS** | run 44538536288 @ `354a742` |
| 3 | Ephemeral Signed Android CI | PR + dispatch | **NOT APPLICABLE** | no PR; needs ephemeral signing secret |
| 4 | CodeQL | push/PR/schedule | **PASS** | run 44545910672 @ `3f8aa6c` |
| 5 | Community Readiness | push/PR, path-filtered | **PASS** | run 44538536107 @ `354a742` |
| 6 | Dependency Review | PR only | **NOT APPLICABLE** | cannot run on a push |
| 7 | F-Droid Offline Android Qualification | PR + dispatch | **NOT TRIGGERED** | dispatchable, not run (experimental) |
| 8 | F-Droid Offline Native Qualification | push/PR, path-filtered | **PASS** | run 44538536085 @ `354a742` |
| 9 | Foundation CI | push/PR, no path filter | **PASS** | run 44545910785 @ `3f8aa6c` (attempt 2) |
| 10 | Marketing Screenshots | dispatch only | **NOT APPLICABLE** | manual only |
| 11 | Native Git CI | push/PR, path-filtered | **PASS** | run 44538536353 @ `354a742` |
| 12 | Android Performance Qualification | dispatch + RC tags | **NOT APPLICABLE** | no tag; manual only |
| 13 | Production CI | push/PR, path-filtered | **PASS** | run 44545910690 @ `3f8aa6c` |
| 14 | Signed Release | tag `v*.*.*` | **NOT APPLICABLE** | tag-only; publishing not requested |
| 15 | Render Play Store artwork | **manual only (changed)** | **EXCLUDED** | not applicable to direct-APK distribution |
| 16 | Store Distribution CI | push/PR, path-filtered | **PASS** | run 44538536031 @ `354a742` |
| 17 | VPS Installer CI | push/PR, path-filtered | **PASS** | run 44538536119 @ `354a742` |
| 18 | Web CI | push/PR, path-filtered | **PASS** | run 44538536086 @ `354a742` |

**Totals: 11 PASS · 1 EXCLUDED · 6 NOT APPLICABLE/NOT TRIGGERED · 0 applicable failures.**

---

## 7. Runs on the current baseline commit `3f8aa6c`

The change commit touches only `.github/workflows/render-store-artwork.yml`, which matches the path
filters of `production-ci.yml` (`.github/workflows/**`), while `codeql.yml` and `foundation-ci.yml`
have no path filters. Those three therefore ran, and nothing else needed to.

| Workflow | Run | URL | Result |
| --- | ---: | --- | --- |
| Production CI | 44545910690 | https://github.com/tanishka1233/HMXGIT/actions/runs/44545910690 | **success** |
| CodeQL | 44545910672 | https://github.com/tanishka1233/HMXGIT/actions/runs/44545910672 | **success** |
| Foundation CI | 44545910785 | https://github.com/tanishka1233/HMXGIT/actions/runs/44545910785 | **success** (attempt 2) |

`Render Play Store artwork` correctly produced **no run** on this commit.

### 7.1 Runs on the imported tree `354a742`

| Workflow | Run | Result |
| --- | ---: | --- |
| Android CI | 44538536172 | success |
| Production CI | 44538536318 | success |
| Foundation CI | 44538536550 | success |
| Native Git CI | 44538536353 | success |
| CodeQL | 44538536373 | success |
| Auth Broker CI | 44538536288 | success |
| Web CI | 44538536086 | success |
| VPS Installer CI | 44538536119 | success |
| Store Distribution CI | 44538536031 | success |
| Community Readiness | 44538536107 | success |
| F-Droid Offline Native Qualification | 44538536085 | success |
| Render Play Store artwork | 44538536314 | failure (now excluded; historical) |

### 7.2 Job-level evidence (not no-ops)

| Run | Jobs |
| --- | --- |
| Android CI | `Build, lint and unit test` |
| Production CI | `Release build and production gate` |
| Native Git CI | `Build and test libgit2 core`; `clang-tidy project-owned native code`; `Fuzz secure native runtime decoder`; `ASan and UBSan native tests` |
| CodeQL | `Analyze C/C++`; `Analyze TypeScript/JavaScript`; `Analyze Go Auth Broker`; `Analyze Kotlin/Java` |

---

## 8. Incident during verification — Foundation CI HTTP 429

- **Run:** 44545910785 (attempt 1) — failed in step `Validate foundation`.
- **Cause:** Gradle dependency resolution was rate-limited by Maven Central:

```
Could not GET 'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/
kotlin-gradle-plugins-bom/2.3.21/kotlin-gradle-plugins-bom-2.3.21.pom'.
Received status code 429 from server: Too Many Requests
```

- **Classification:** external infrastructure / transient rate limit (the first push started a dozen
  workflows that resolved dependencies simultaneously). Not a code or configuration defect.
- **Action:** the job was re-run unchanged (attempt 2) → **success**.
- No workflow, test or check was modified to get past it.

---

## 9. Verification scope and caveats

- Every run counted has `head_sha` equal to the commit named in its row. No older run from a
  different commit is used as evidence.
- Path-filtered workflows that did not re-run at `3f8aa6c` were verified at `354a742`. The change
  between the two commits is **one workflow file**; `app/`, `native/`, `gradle/`, `buildSrc/`,
  `auth-broker/`, `web/` and all assets are byte-identical. Therefore the `354a742` evidence still
  describes the current code.
- A skipped, excluded or never-triggered workflow is **not** reported as passed.
- No release, publishing or deployment workflow was triggered.
- `Dependabot Updates` appears in the Actions registry as a dynamic workflow; it is not one of the
  18 repository workflows and is not counted.

---

## 10. Baseline conclusion

```
BASELINE = GREEN (for the declared direct-APK distribution strategy)

11 PASS
 1 EXCLUDED   (Render Play Store artwork — Play Store only)
 6 NOT APPLICABLE / NOT TRIGGERED (PR-only, manual-only or tag-only)
 0 applicable failures
```

All build, test, lint, native, security and code-quality checks that apply to this project are
green on `3f8aa6c`, or green at `354a742` with a byte-identical source tree.

**Application source editing is still on hold** pending explicit authorisation. This report changes
documentation only and does not alter the verified build.

---

## 11. Next step (requires authorisation)

Once authorised, the first source change is the P0 fix described in report 16:

- `app/src/main/res/values-night/styles.xml:3` — replace the framework
  `android:style/Theme.Material.NoActionBar` parent with an AppCompat-compatible parent so the night
  configuration no longer breaks `AppCompatActivity` (`BUG-001`).
- Map the in-app theme setting to `AppCompatDelegate.setDefaultNightMode` (`BUG-002`).

No fix has been applied. Awaiting the go-ahead.

---

## 12. Appendix — post-baseline P0 change verification

The owner subsequently authorised the BUG-001 repair and supplied device-captured Logcat evidence
(recorded in reports 13 §1.1 and 14 §0). The baseline itself — §1 to §10 — is unchanged and remains
the green reference at `3f8aa6c` / `ffa8036`.

### 12.1 Change under test

Commit **`729f11c70248280efe5a419c0a3d4e0e516fbb25`** — one line:

```diff
-    <style name="Theme.NexoraGit" parent="android:style/Theme.Material.NoActionBar">
+    <style name="Theme.NexoraGit" parent="Theme.AppCompat.DayNight.NoActionBar">
```

File: `app/src/main/res/values-night/styles.xml`. No other file changed.

### 12.2 CI runs on `729f11c`

| Workflow | Run | Result |
| --- | ---: | --- |
| Android CI | 44550719684 | **success (attempt 2)** — attempt 1 failed transiently, see 12.3 |
| Production CI | 44550719662 | success |
| CodeQL | 44550719705 | success |
| Foundation CI | 44550719818 | success |

Android CI completed the full `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
`:baselineprofile:assemble`, `:app:testDebugUnitTest`, `:app:lintDebug` and uploaded the debug APK.

### 12.3 Transient Android CI packaging failure (attempt 1)

Attempt 1 failed in `:app:packageDebug`:

```
Execution failed for task ':app:packageDebug'.
> A failure occurred while executing
  com.android.build.gradle.tasks.PackageAndroidArtifact$IncrementalSplitterRunnable
```

No cause line was emitted by AGP, no disk/heap/space error appeared anywhere in the log, and the same
commit passed Production CI's release packaging in parallel. The job was re-run **unchanged**
(attempt 2) and passed in full. This is recorded as a transient runner-side packaging fault, not a
defect introduced by the theme change. Nothing was modified to make it pass.

### 12.4 APK content check

The CI-produced `NexoraGit-debug` artifact (68 MB, run 44550719684) was inspected locally with
`aapt2 dump resources`. Both configurations now resolve the theme to an AppCompat parent:

```
resource 0x7f0d010e style/Theme.NexoraGit
  ()      (style) size=5 parent=style/Theme.AppCompat.DayNight.NoActionBar
  (night) (style) size=5 parent=style/Theme.AppCompat.DayNight.NoActionBar
```

The night variant keeps `windowLightStatusBar=false` (attr `0x010104e0=false`); the light variant
keeps `true`. So the compiled artifact contains the intended fix in the exact configuration that
previously crashed.

### 12.5 Device launch verification — NOT COMPLETE

Per the owner's instruction, no local Gradle build was run. The CI APK was downloaded for an
on-device test, but the wireless-ADB connection dropped during the install flow
(`192.168.0.103:45165` → `offline`, then connection refused; the host is still pingable, so the
debug port rotated or wireless debugging was switched off).

Therefore:

- the fix is **committed, CI-verified and present in the built APK**;
- an actual post-fix launch on the physical OPPO device has **not** been observed;
- the crash is **not** claimed as fixed until that launch succeeds.

Reproduction steps for the pending test are in report 15.

