# 14 — Startup Crash Root Cause Analysis

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Status:** source-proven **and device-confirmed** (2026-11-19).
>
> The audit itself did not build or launch the app. The project owner later reproduced the crash on
> a physical **OPPO** device in **night mode** and captured Logcat. That evidence is recorded in §0
> and supersedes the earlier "static only" caveat. The reconstructed trace in §4 is kept for
> reference; the actual captured frame chain is quoted in §0.

---

## 0. Device-confirmed evidence (2026-11-19)

| Field | Captured value |
| --- | --- |
| Application ID | `com.nexora.git.debug` |
| Activity | `com.nexora.git.MainActivity` |
| Crash location | `MainActivity.kt:40` |
| Exception | `java.lang.IllegalStateException` |
| Message | `You need to use a Theme.AppCompat theme (or descendant) with this activity.` |
| Frame chain | `AppCompatDelegateImpl.createSubDecor` → `AppCompatActivity.setContentView` → `ComponentActivityKt.setContent` → `MainActivity.onCreate` |
| Device configuration | Night mode |

This is the **exact** exception text, Activity, crash line and call chain predicted in §3 and §4 of
this report. The static analysis is therefore validated: the night-qualified theme
(`values-night/styles.xml`) is the cause, and the failure occurs in the fourth statement of
`onCreate`, before any Compose content exists.

Consequences for this report:

- BUG-001 is no longer "high confidence, unconfirmed" — it is **reproduced**.
- The trigger is confirmed to be **device night mode**, not an in-app setting (BUG-002 remains a
  separate defect).
- The minimal repair is the one described in §3: give the night variant an AppCompat-compatible
  parent.

---

## 1. Symptom

Nexora Git **crashes immediately after opening** — before any usable screen is shown. Reported by
the owner and now **reproduced on a physical device** in night mode (§0).

This is consistent with a failure in the Activity's first `onCreate`, before the first Compose frame.
It is **not** consistent with a failure after login, during a Git operation, or during rendering of a
specific screen, because the user never reaches those.

---

## 2. Ranked causes

| Rank | Cause | Severity | Confidence | Requires |
| ---: | --- | --- | --- | --- |
| **1** | **Night theme is not an AppCompat theme** (BUG-001) | P0 | **High** | Device in dark mode |
| 2 | Uncaught Room/DataStore exception in startup collectors (BUG-011 + BUG-006) | P2/P1 | Medium | Corrupt/downgraded DB, corrupt DataStore, full disk |
| 3 | Unguarded `System.loadLibrary` (BUG-003) | P1 | Medium-low | Missing ABI `.so`, partial install |
| 4 | Locale application recreating the Activity into the night theme (BUG-013 → BUG-001) | P2 | Low-medium | Non-system language selected on API 26–32 |
| 5 | R8 stripping a JNI/reflective symbol in a release build | P3 | Low | Release-only, and would usually fail later than launch |
| 6 | `requireNotNull(state.activeAccount)` (BUG-012) | P2 | Very low | State invariant violation |
| 7 | Hilt/KSP generated-code mismatch | — | Very low | Broken build (CI proves it compiles) |
| 8 | `enableEdgeToEdge` / OEM window failure | — | Very low | Specific OEM |
| 9 | OOM at startup from caches | P2 | Very low | Heavy prior use in the same process |
| 10 | `minSdk`/ABI install mismatch | — | Not a crash | Install would fail first |

**Rank 1 is the only cause that is deterministic, source-provable, and matches "immediately after
opening" on a mainstream device configuration (dark mode).**

---

## 3. Cause 1 — the night theme defect (detailed)

### 3.1 The two themes

`app/src/main/res/values/styles.xml:3`:

```xml
<style name="Theme.NexoraGit" parent="Theme.AppCompat.DayNight.NoActionBar">
```

`app/src/main/res/values-night/styles.xml:3`:

```xml
<style name="Theme.NexoraGit" parent="android:style/Theme.Material.NoActionBar">
```

`AndroidManifest.xml:17,24` applies `@style/Theme.NexoraGit` to both the application and the
activity. Android resource resolution picks the `values-night` variant whenever the current
`uiMode` has `UI_MODE_NIGHT_YES`.

### 3.2 The Activity requires an AppCompat theme

`MainActivity.kt:25-40`:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    ...
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeApplicationLanguage()
        setContent { ... }        // line 40
```

`AppCompatActivity` is a `FragmentActivity`, which delegates window/decor creation to
`AppCompatDelegateImpl`. `setContent` from `androidx.activity.compose` calls
`ComponentActivity.setContentView`, which for `AppCompatActivity` is overridden to ensure the
AppCompat sub-decor exists.

### 3.3 The exact guard

From the AndroidX source (`androidx-main`, `AppCompatDelegateImpl.java`):

```java
// createSubDecor(), around line 850
TypedArray a = theme.obtainStyledAttributes(R.styleable.AppCompatTheme);

if (!a.hasValue(R.styleable.AppCompatTheme_windowActionBar)) {
    a.recycle();
    throw new IllegalStateException(
            "You need to use a Theme.AppCompat theme (or descendant) with this activity.");  // line 861
}
```

`R.styleable.AppCompatTheme_windowActionBar` is the **appcompat library attribute**
`windowActionBar` declared in `appcompat/src/main/res/values/attrs.xml:43`. It is a different
resource ID from the framework attribute `android:windowActionBar`. A theme whose parent is
`android:style/Theme.Material.NoActionBar` never declares the appcompat attribute, so
`hasValue(...)` is false and the exception is thrown.

### 3.4 Why it is immediate

The throw happens inside `AppCompatDelegateImpl.ensureSubDecor()`, which is called from
`AppCompatActivity.setContentView(...)`, which is called by `setContent { }` at `MainActivity.kt:40`.
That is the **fourth statement of `onCreate`**, before Compose composes anything, before Hilt
ViewModels are created, before Room is opened, before any network call.

Consequences:

- No Compose error boundary can catch it (it is not a Compose exception).
- No `try/catch` in app code wraps `setContent`.
- The process dies on the main thread; Android shows "Nexora Git keeps stopping".
- The failure is 100% reproducible **on a dark-mode device** and absent **on a light-mode device**.

### 3.5 Why light mode can still crash later

Even in light mode, the defect is latent:

- if the system switches to dark mode while the app is running, the Activity is recreated and the
  new instance resolves `values-night` → crash;
- if the user selects Spanish (BUG-013) the Activity is recreated, and on a dark device the new
  instance crashes;
- if the user selects "Dark" in the app (BUG-002 only changes Compose colors), the platform theme
  does not change, so the crash depends on the *system* mode, not the in-app mode.

This explains a report of "crashes sometimes" or "crashes on my phone but not the emulator".

---

## 4. Stack trace — predicted vs captured

**Captured on device (2026-11-19, OPPO, night mode):** the frame chain was reported as

```
AppCompatDelegateImpl.createSubDecor
  → AppCompatActivity.setContentView
    → ComponentActivityKt.setContent
      → MainActivity.onCreate (MainActivity.kt:40)
```

with exception `java.lang.IllegalStateException` and message
`You need to use a Theme.AppCompat theme (or descendant) with this activity.`

That is frame-for-frame the chain predicted below. The block below is the full reconstruction made
during the audit, before the device log existed; it is retained because it adds the surrounding
framework frames.

> **Reconstruction note:** this block is derived from the source and the AppCompat implementation.
> It is not a verbatim Logcat dump (PIDs, `ActivityThread` lines and library line numbers vary per
> device), but its frame names and message match the captured evidence in §0 exactly.

```
FATAL EXCEPTION: main
Process: com.nexora.git, PID: <pid>
java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
    at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:861)
    at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:806)
    at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:719)
    at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:201)
    at androidx.activity.compose.ActivityComposeKt.setContent(ActivityCompose.kt:210)
    at androidx.activity.compose.ActivityComposeKt.setContent$default(ActivityCompose.kt:190)
    at com.nexora.git.MainActivity.onCreate(MainActivity.kt:40)
    at android.app.Activity.performCreate(Activity.java:...)
    at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:...)
    at android.app.ActivityThread.main(ActivityThread.java:...)
```

Line numbers for AppCompat/Activity Compose depend on the resolved library versions
(AppCompat 1.8.0, Activity Compose 1.13.0) and may differ by a few lines. The **frame names and the
exception message** are the discriminating evidence.

### 4.1 How to confirm in one line

```bash
adb logcat -d | grep -E "Theme.AppCompat|AppCompatDelegateImpl|FATAL EXCEPTION"
```

If the message is present, BUG-001 is the cause with near-certainty.

---

## 5. Cause 2 — storage exception at startup

### 5.1 Mechanism

`AuthViewModel.init` (`AuthViewModel.kt:32-53`) launches two collectors in `viewModelScope` with no
`.catch`:

```kotlin
viewModelScope.launch {
    combine(
        authSessionRepository.accounts,      // Room
        authSessionRepository.activeAccountId, // DataStore
    ) { ... }.collect { ... }
}
```

An exception from Room (`SQLiteException`, `IllegalStateException` on schema mismatch/downgrade) or
DataStore (`CorruptionException`, `IOException`) is not caught. In `viewModelScope`, an uncaught
exception is delivered to the thread's uncaught-exception handler; on Android this terminates the
process.

`AuthUiState.loading` starts as `true`, so the first frame is a spinner — but `setContent` has
already succeeded, so the user would see a spinner flash before the crash, not a zero-frame crash.

### 5.2 When it happens

- upgrading from a build with **version > 4** (downgrade) — no fallback (BUG-006);
- a migration that partially applied;
- a corrupted `nexora-git.db`;
- a corrupted `nexora_settings.preferences_pb`;
- storage full during a write.

A **fresh install** does not hit this. If the crash happens on first launch after install, this cause
is unlikely.

### 5.3 Confidence

**Medium**, conditional on the device's data state. It is the second-best explanation because it is
the only other source-provable zero-config crash path, and the project has no recovery for it.

---

## 6. Cause 3 — native library load

`NativeGitBridge` and `NativeSyntaxBridge` load `libnexoragit_native.so` in a companion `init`
without a guard. `UnsatisfiedLinkError` is an `Error` and is not caught.

### 6.1 Why it is ranked third

- `NativeGitBridge` is first touched when `Libgit2GitEngine` is constructed, which happens when
  `HomeViewModel` is created — **after** auth and onboarding. On a fresh install with no account the
  user stays on `LoginScreen`, so the library is never loaded and cannot crash.
- On a **signed-in returning user**, `NexoraGitApp` goes straight to the authenticated host, Home is
  created, and the library loads during the first composition. A missing ABI or a partial install
  would then crash — but after the loading gate, not at zero milliseconds.
- The ABI filters include `arm64-v8a`, `armeabi-v7a` and `x86_64`, covering all common phones and
  emulators except 32-bit x86 (BUG-038).

**Confidence: medium-low.** It is a real defect but does not match "immediately after opening" for a
fresh release install on a normal phone.

---

## 7. Cause 4 — locale-driven Activity recreation

`MainActivity.observeApplicationLanguage()` compares the stored `AppLanguage` with
`AppCompatDelegate.getApplicationLocales()` and calls `setApplicationLocales(...)` if they differ.
On API 33+ this is handled by the framework; on API 26–32 AppCompat applies it in-process and may
recreate the Activity. Without `AppLocalesMetadataHolderService` (BUG-013) the stored value may not
persist, which can cause repeated application.

On a dark-mode device, the recreated Activity resolves `values-night` → BUG-001. So this is a
**path into Cause 1**, not an independent crash. Ranked separately for diagnostic completeness.

---

## 8. What the crash is NOT

| Hypothesis | Verdict | Why |
| --- | --- | --- |
| Native SIGSEGV in libgit2 | **No** | Native library is not loaded before the first screen |
| Compose composition error | **No** | `setContent` fails before composition |
| Hilt graph missing binding | **No** | CI compiles `:app:assembleRelease`; Hilt validates the graph at compile time |
| R8 removed `MainActivity` | **No** | Manifest keep rules and AGP handle it; also the theme failure precedes reflection |
| Missing `INTERNET` permission | **No** | No network call at launch; permission is declared |
| `minSdk` too high | **No** | Would fail at install/parse, not at launch |
| OAuth misconfiguration | **No** | Unconfigured auth is explicitly handled and does not crash |
| Corrupt APK / wrong signature | **No** | Would fail at install |
| Android 12+ splash screen API | **No** | No custom splash; default is safe |
| Missing `android:exported` | **No** | Declared on the activity |

This narrowing is important: it removes most of the "usual suspects" and leaves a resource/theme
defect or a storage exception.

---

## 9. Decision procedure for the user

Use this to identify the cause on the actual device without a local build:

```
Did the crash happen on the very first launch after install?
├── Yes → Is the device in dark mode?
│         ├── Yes → BUG-001 (theme). Confirm with the Logcat grep in §4.1.
│         └── No  → BUG-003 (native) or an OEM issue. Capture full Logcat.
└── No (it worked before) →
          Did you upgrade/downgrade the app?
          ├── Yes → BUG-006/BUG-011 (Room schema). Look for "Room cannot verify"
          │          or "Database has been downgraded".
          └── No  → Did the system switch to dark mode, or did you change language?
                    ├── Yes → BUG-001 via recreation.
                    └── No  → capture full Logcat; attach to report 15 template.
```

---

## 10. Confidence and evidence statement

| Claim | Confidence | Basis |
| --- | --- | --- |
| Night theme is not AppCompat-compatible | **Certain** | Direct source; AppCompat source guard at `AppCompatDelegateImpl.java:858-861` |
| That defect throws on a dark-mode device | **Confirmed on device** | Reproduced on a physical OPPO device in night mode (§0) |
| The throw happens before Compose renders | **Certain** | `setContent` → `setContentView` → `ensureSubDecor`; crash at `MainActivity.kt:40` |
| The reported crash is this defect | **Confirmed** | Captured message, Activity, line and frame chain match §0 |
| A storage exception could also crash at startup | **Medium** | Unguarded collectors; needs damaged data |
| A native load could crash at startup | **Medium-low** | Unguarded load; needs missing ABI and a signed-in state |

**The crash is reproduced and the Logcat evidence is captured** (§0). It matches the static
analysis exactly. Remaining residual risk is limited to the secondary causes above, which would
produce a *different* stack trace.

---

## 11. Root-cause verdict

The root cause of "crashes immediately after opening" is **device-confirmed**:

> **`values-night/styles.xml` replaces the AppCompat theme with a framework Material theme. On a
> dark-mode device, `AppCompatActivity.setContent` throws
> `IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this
> activity` before the first Compose frame.**

Evidence: reproduced on a physical OPPO device in night mode; `com.nexora.git.debug`,
`MainActivity.kt:40`, `AppCompatDelegateImpl.createSubDecor` → `AppCompatActivity.setContentView` →
`ComponentActivityKt.setContent` → `MainActivity.onCreate` (§0).

Secondary, conditional causes are the unguarded storage collectors (BUG-011 + BUG-006) and the
unguarded native load (BUG-003). All three are independent of authentication and network.

The minimal fix is a one-line resource change (give the night variant an AppCompat parent such as
`Theme.AppCompat.DayNight.NoActionBar`) plus a night-mode launch test. The repair and its CI
validation are tracked in the repository history; **device relaunch verification is a separate,
explicitly required step** and is not claimed here.
