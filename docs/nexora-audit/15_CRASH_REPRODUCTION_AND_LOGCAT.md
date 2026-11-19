# 15 — Crash Reproduction and Logcat Capture

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **This audit did not reproduce the crash.** No device, emulator, ADB or APK was available, and the
> user prohibited local builds on the Termux host. This report is a **procedure** for the user, not a
> record of results.

---

## 1. Package identities

| Build | applicationId | Launcher component |
| --- | --- | --- |
| Release | `com.nexora.git` | `com.nexora.git/.MainActivity` |
| Debug | `com.nexora.git.debug` | `com.nexora.git.debug/com.nexora.git.MainActivity` |
| Ephemeral CI | `com.nexora.git.ci` | `com.nexora.git.ci/com.nexora.git.MainActivity` |

**Using the wrong package is the most common reason a reproduction "does nothing".** Confirm the
installed package first:

```bash
adb shell pm list packages | grep nexora
```

Expected output (one or more):

```
package:com.nexora.git
package:com.nexora.git.debug
```

---

## 2. Prerequisites

- A device or emulator running **Android 8.0 (API 26) or newer**.
- USB debugging enabled, or a working emulator.
- A **prebuilt** APK. Do **not** build on the low-RAM Termux device.
  Use one of:
  - a GitHub Actions artifact from the project's `production-ci.yml` / `ci-ephemeral-signing.yml`
    runs (ephemeral builds are `com.nexora.git.ci`);
  - a personal machine or cloud runner that executes `./gradlew :app:assembleDebug`.

Verify ADB:

```bash
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
```

---

## 3. Reproduction matrix

Test all six cells. The goal is to prove or falsify the night-theme hypothesis.

| # | Install state | System theme | Expected if BUG-001 is the cause |
| ---: | --- | --- | --- |
| 1 | Fresh (clear data) | **Dark** | **Crash** |
| 2 | Fresh (clear data) | **Light** | Launches to Login |
| 3 | Existing signed-in data | **Dark** | **Crash** |
| 4 | Existing signed-in data | **Light** | Launches to Home |
| 5 | Fresh, then toggle to dark while running | Dark | Crash on Activity recreation |
| 6 | Fresh, then change language (Settings → Spanish) on a dark device | Dark | Crash on recreation |

Cells **1 vs 2** are the discriminating pair. If both crash, BUG-001 is not the (only) cause; go to
§7.

---

## 4. Commands

### 4.1 Install and clear

```bash
# Use the package that matches your APK.
PKG=com.nexora.git

adb install -r -d app-release.apk
adb shell pm clear "$PKG"
```

`pm clear` simulates a fresh install's data directory (it does not remove the APK).

### 4.2 Force dark mode

```bash
# Query current mode
adb shell cmd uimode night

# Force night (dark)
adb shell cmd uimode night yes

# Force day (light)
adb shell cmd uimode night no

# Follow system
adb shell cmd uimode night auto
```

On some OEMs the command is silently ignored unless the system theme is set in Settings. In that
case use the device UI: **Settings → Display → Dark theme**.

Verify with:

```bash
adb shell cmd uimode night
# expected: Night mode: yes
```

### 4.3 Start the app and capture

```bash
PKG=com.nexora.git

# Clear the buffer so only this launch is captured
adb logcat -c
adb logcat -b crash -d    # (optional) flush any stale crash buffer

# Start the app
adb shell am start -W -n "$PKG/.MainActivity"

# Capture immediately
adb logcat -d -v threadtime > nexora-launch-$(date +%s).txt

# Also capture the dedicated crash buffer
adb logcat -b crash -d > nexora-crash-buffer-$(date +%s).txt
```

### 4.4 If the app crashed before the logcat command returns

Use the crash buffer and DropBox:

```bash
adb logcat -b crash -d
adb shell dumpsys dropbox --print data_app_crash | tail -200
adb shell dumpsys activity processes | grep -A3 nexora
```

`data_app_crash` in DropBox retains the last uncaught exception even after the buffer rolls.

### 4.5 Full bugreport (if the above is inconclusive)

```bash
adb bugreport nexora-bugreport.zip
```

The bugreport contains the full tombstone, dropbox entries, package dumps and device configuration.

---

## 5. What to look for

### 5.1 BUG-001 (night theme)

```
FATAL EXCEPTION: main
Process: com.nexora.git, PID: ...
java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
    at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:861)
    at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:806)
    at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:201)
    at androidx.activity.compose.ActivityComposeKt.setContent(ActivityCompose.kt:...)
    at com.nexora.git.MainActivity.onCreate(MainActivity.kt:40)
```

**Discriminating string:** `You need to use a Theme.AppCompat theme`.

One-line check:

```bash
grep -E "Theme.AppCompat|AppCompatDelegateImpl" nexora-launch-*.txt
```

### 5.2 BUG-011 / BUG-006 (storage)

```
android.database.sqlite.SQLiteException: ...
java.lang.IllegalStateException: Room cannot verify the data integrity.
    Looks like you've changed schema but forgot to update the version number.
androidx.datastore.core.CorruptionException: Unable to read ...
Caused by: java.io.IOException: ...
```

Look for `AuthViewModel` or `AuthSessionRepository` in the stack.

### 5.3 BUG-003 (native)

```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libnexoragit_native.so" not found
    at java.lang.Runtime.loadLibrary0(...)
    at com.nexora.git.core.git.NativeGitBridge.<clinit>(NativeGitBridge.kt:...)
```

Look for `NativeGitBridge`/`NativeSyntaxBridge` in the stack.

### 5.4 BUG-013 (locale loop)

```
ActivityTaskManager: ... relaunch ... config change
```

Repeated `ActivityTaskManager` relaunch lines with no other exception indicate a recreation loop.
Grep the file for `MainActivity` relaunch and check whether the crash follows it.

---

## 6. Logcat template (copy into your report)

```
Device: <model>, Android <version> (API <n>), ABI <abi>
Package: <com.nexora.git | .debug | .ci>
Install state: fresh (pm clear) / existing
System night mode: yes / no / auto
APK source: <CI run URL or local build>
Result: crash / launches to <screen>

FATAL EXCEPTION: <paste>
First 20 stack frames:
  1. ...
  2. ...
Discriminating message:
  <paste the IllegalStateException / SQLiteException / UnsatisfiedLinkError line>
```

Compare the discriminating message with §5. If it matches §5.1, the audit's primary root cause is
confirmed on the device.

---

## 7. Falsification tests

Run these if the primary hypothesis does not match.

| Observation | Conclusion |
| --- | --- |
| Crashes in **light** mode too on a fresh install | Not BUG-001. Capture full Logcat; inspect for storage (BUG-011) or native (BUG-003). |
| Crashes only after sign-in | Not a startup theme crash; look at `HomeViewModel` → `Libgit2GitEngine` → native load (BUG-003) or Git credential resolution. |
| Crashes only with an existing data directory | Storage (BUG-006/BUG-011). Reproduce by upgrading from a previous build, or by corrupting the DB. |
| Crashes only on one OEM | OEM window/insets issue, or locale/theme quirk. Capture `dumpsys activity` and the full bugreport. |
| No crash at all, app shows "Authentication is disabled" | Expected for a stock build without broker config (BUG-007). This is not a crash. |
| `am start` says "Error type 3 / Activity class does not exist" | Wrong package (debug suffix or `.ci`) — not a crash. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | The APK lacks the device ABI. This is BUG-038 territory, but it is an install failure, not a launch crash. |

### 7.1 Corrupt the database deliberately (only on a test device)

To prove BUG-006/BUG-011 without upgrading:

```bash
adb shell run-as com.nexora.git.debug ls databases
adb shell run-as com.nexora.git.debug sh -c 'echo garbage > databases/nexora-git.db'
adb shell am force-stop com.nexora.git.debug
adb shell am start -W -n com.nexora.git.debug/com.nexora.git.MainActivity
```

**This destroys app data.** Use a debug build on a disposable device only.

### 7.2 Force a wrong-ABI install

```bash
adb shell getprop ro.product.cpu.abi     # e.g. x86
# Install an APK built only for arm64-v8a → INSTALL_FAILED_NO_MATCHING_ABIS
```

You cannot easily force a missing `.so` on a matching ABI without repackaging the APK, which is out
of scope for a read-only audit.

---

## 8. Reproduction status

| Item | Status |
| --- | --- |
| APK built | **No** (prohibited) |
| APK installed | **No** |
| App launched | **No** |
| Crash reproduced | **No** |
| Logcat captured | **No** |
| Reproduction procedure validated | Not executed |

**Therefore:** the crash cause remains **static/high-confidence, not device-confirmed**. The
procedure above is the shortest path to confirmation. When the user supplies the Logcat, the
discriminating string in §5 will settle the question in one step.

---

## 9. Safe-handling notes

- A Logcat can contain tokens if any library logs headers. The app redacts `Authorization` and
  `Set-Cookie`, but **review the file before sharing it publicly**. Replace any `gho_`, `ghu_`,
  `github_pat_` or `Bearer ` strings.
- Do not run the database-corruption test on a device with real repositories.
- Do not install a `.ci` build over a release build; the application IDs differ and you will end up
  with two apps.
- Uninstall the debug build when finished: `adb uninstall com.nexora.git.debug`.
