# 16 — Fix Priority and Recommendations

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **IMPORTANT:** this is a **plan**, not an implementation. No file was changed. Each item names the
> intended change, the evidence, and how to verify it.

---

## 1. Ordering principle

Fixes are ordered by **blast radius × likelihood**, not by implementation difficulty:

1. Anything that makes the app impossible to open (P0).
2. Anything that can lose data or credentials silently (P1).
3. Anything that degrades correctness or performance in normal use (P2).
4. Hygiene and edge cases (P3).

Two rules apply to HMX Git:

- **Do not fork before Phase 0 is verified on a real dark-mode device.** A fork that inherits
  BUG-001 inherits the user's crash report.
- **Do not rewrite the Git engine.** It is the strongest subsystem; harden it, do not replace it.

---

## 2. Phase 0 — Unblock launch (P0, hours)

| # | Fix | Files | Verification |
| ---: | --- | --- | --- |
| 0.1 | Make the night theme AppCompat-compatible | `app/src/main/res/values-night/styles.xml:3` | Change parent to `Theme.AppCompat.DayNight.NoActionBar` and remove/translate framework-only items; build; launch with `adb shell cmd uimode night yes`; no `IllegalStateException` |
| 0.2 | Make the in-app theme authoritative | `MainActivity.kt`, `SettingsViewModel.kt`, new `ThemeModeApplier` | Map `SYSTEM/LIGHT/DARK/AMOLED` → `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM/NO/YES/YES)`; assert the Activity recreates into the chosen mode |
| 0.3 | Add a night-mode launch test | `app/src/androidTest/.../StartupThemeTest.kt` (new) | Launch `MainActivity` with night mode forced; assert no crash |
| 0.4 | Add a startup smoke test in CI | `android-ci.yml` or a new `startup-ci.yml` | Run the instrumentation test above on an emulator; fail the job on crash |

**Exit criteria:** the app launches on:
1. fresh data + dark mode;
2. fresh data + light mode;
3. existing signed-in data + dark mode;
4. after toggling dark mode while running;
5. after changing language on a dark device.

**Effort:** 0.5–1 day including the emulator job.
**Risk of not doing it:** every HMX Git user in dark mode reports the same crash on day one.

> A minimal alternative for 0.1 only: delete the `values-night/styles.xml` override entirely and let
> the AppCompat DayNight parent handle night colors. That is the smallest safe change, but it does
> not fix 0.2.

---

## 3. Phase 1 — Data and credential integrity (P1, 1–2 weeks)

| # | Fix | Files | Verification |
| ---: | --- | --- | --- |
| 1.1 | Guard native library loads | `NativeGitBridge.kt`, `NativeSyntaxBridge.kt` | Wrap `System.loadLibrary` in `runCatching`; expose an `isAvailable` flag; show a clear "device unsupported" screen instead of a process death |
| 1.2 | Serialize Git per repository | `Libgit2GitEngine` or a new `RepositoryLockRegistry` | `ConcurrentHashMap<String, Mutex>`; `withLock` around every mutating operation; test two concurrent commits |
| 1.3 | Add progress and cancellation | `git_core.cpp` (`remote_callbacks`), JNI, Kotlin engine | Install `transfer_progress` and `sideband_progress`; pass an atomic cancel flag; Kotlin cancellation sets it; test a cancelled clone leaves no partial dir |
| 1.4 | Room recovery | `DatabaseModule.kt`, `NexoraDatabase.kt` | Add `fallbackToDestructiveMigrationOnDowngrade()` and a version-gated recovery for unrecoverable forward states; commit `app/schemas`; add `MigrationTestHelper` tests 1→4 |
| 1.5 | Exception boundaries on startup flows | `AuthViewModel.kt`, `OnboardingViewModel.kt`, `SettingsRepository` | `.catch { emit(fallback) }` on every startup collector; surface an error screen; test with a corrupted DB and DataStore |
| 1.6 | Fail loudly on config JNI failure | `SecureRuntimeConfigNative.kt`, `secure_config_jni.cpp` | Distinguish "unconfigured" from "library/config broken"; log/build-fail the latter |
| 1.7 | CA trust store | `NativeGitBridge.kt`, native `initialize` | Resolve the CA directory from the platform (APEX + legacy fallback), not a hard-coded path; test a Git fetch over HTTPS on Android 14+ |
| 1.8 | Release signing gate | `app/build.gradle.kts`, `validate-production.sh` | Fail `assembleRelease`/`bundleRelease` when `releaseSigning == null` for a release task; keep debug unsigned-friendly |
| 1.9 | Atomic auth writes | `SecureAuthStorage.kt`, `AuthSessionRepository.kt` | Use `commit()` for token writes; write account row then token then active id with a recovery reconciliation at startup |
| 1.10 | Per-account refresh lock | `AuthSessionRepository.kt` | Replace the global `Mutex` with `Map<Long, Mutex>`; add a `callTimeout` to the broker client |

**Exit criteria:** no known single-point data-loss path; a corrupted DB shows a recovery screen, not
a crash; a cancelled clone leaves a clean workspace; release builds fail without a signer.

---

## 4. Phase 2 — Correctness and performance (P2, 2–4 weeks)

| # | Fix | Files | Verification |
| ---: | --- | --- | --- |
| 2.1 | Bound caches by bytes | `GitHubResponseCache.kt`, `GitHubGraphQlCache.kt` | Track total body bytes; evict LRU when over a budget (e.g. 8 MB); drop bodies > 256 KB |
| 2.2 | Parallelize Home summaries | `HomeViewModel.kt` | `coroutineScope { map { async { ... } } }`; cache by workspace HEAD/mtime; benchmark 10 workspaces |
| 2.3 | Bound diff size | Native diff + parser | Cap patch bytes; return a summary + "diff too large" flag; never build a multi-MB JSON string |
| 2.4 | Add Paging 3 to long lists | Issues, PRs, commits, Actions | PagingSource per endpoint; verify memory on 1,000-issue repo |
| 2.5 | Editor analysis window | `MobileEditorViewModel`, `syntax_engine` | Analyze the viewport ± N lines or cap at a smaller size; keep the 512 KiB edit cap |
| 2.6 | Fix theme context cast | `Theme.kt:73-81` | Walk `ContextWrapper` chain to `Activity`; no-op if absent |
| 2.7 | Account-scope the API cache | `GitHubResponseCache` key | Include `accountId` in the key; clear on `switchAccount` |
| 2.8 | Warm OAuth callbacks | `MainActivity` | Override `onNewIntent` and dispatch `intent.data` to `AuthCallbackBus` |
| 2.9 | Guard Custom Tab launch | `NexoraGitApp.kt` | `runCatching`; map to `AuthFailure.NO_BROWSER` |
| 2.10 | UTF-8 correctness | `git_jni.cpp` | Replace `GetStringUTFChars` with `GetStringChars` + surrogate-aware conversion (copy the `syntax_jni.cpp` approach) |
| 2.11 | Zeroize credentials | `git_jni.cpp`, `git_core.cpp` | Wipe `std::string` buffers with a volatile loop on destruction |
| 2.12 | Locale metadata | Manifest + `res/xml` | Declare `AppLocalesMetadataHolderService` with `autoStoreLocales`, or restrict per-app language to API 33+ |

---

## 5. Phase 3 — Capability and hygiene (P3, ongoing)

| # | Item | Notes |
| ---: | --- | --- |
| 3.1 | SSH transport | Enable libssh2 in the native build; add key management UI; this is a product decision, not a quick fix |
| 3.2 | Shallow/partial clone + prune | Add depth and prune options to clone/fetch |
| 3.3 | Conflict editor integration | Allow arbitrary resolutions, not only OURS/THEIRS |
| 3.4 | LSP implementation or removal | Do not advertise language intelligence until a transport exists |
| 3.5 | Foreground clone worker | Progress notification; survives backgrounding |
| 3.6 | Stale `index.lock` recovery | Detect and offer to remove after a crash |
| 3.7 | Editor drafts | Persist unsaved text across process death |
| 3.8 | GitHub deep links | Register repo/issue/PR URL patterns |
| 3.9 | Toolchain pinning | Consider a conservative AGP/Gradle/SDK combination for the fork |
| 3.10 | ProGuard hardening | Replace `-keepclasseswithmembernames` with explicit `-keepclasseswithmembers` where required |
| 3.11 | Native leak fixes | `rename_remote` strarray (BUG-033), config local ref (BUG-034), `git_libgit2_shutdown` (BUG-035) |
| 3.12 | Documentation honesty | Remove unverified README claims (BUG-036) until device QA exists |
| 3.13 | Dead code | Remove `GitHubApi`/Retrofit or complete it (BUG-018); remove `FoundationScreen` |
| 3.14 | SBOM + dependency scanning | Add OSV/Trivy or equivalent to CI |

---

## 6. Recommended HMX Git Phase 0 (fork gate)

Before any HMX rebranding or feature work, require all of:

- [ ] BUG-001 fixed and verified in dark mode on a physical device.
- [ ] BUG-002 fixed: the in-app theme controls the platform theme.
- [ ] A night-mode launch test runs in CI and fails on regression.
- [ ] BUG-011 fixed: startup storage errors show a screen, not a crash.
- [ ] BUG-006 fixed: `app/schemas` committed and a downgrade path exists.
- [ ] `adb logcat` from the user's device captured and attached to the audit record.
- [ ] A decision recorded on the auth model (own broker vs alternative).

Only then should the fork be created. Everything else (SSH, progress, Paging, LSP) is post-fork
roadmap.

---

## 7. Effort and risk summary

| Phase | Scope | Effort | Risk if skipped |
| --- | --- | --- | --- |
| 0 | Theme + launch test | 0.5–1 day | App crashes in dark mode; fork inherits it |
| 1 | Integrity hardening | 1–2 weeks | Data loss, credential desync, unsigned releases |
| 2 | Correctness/perf | 2–4 weeks | Jank, OOM, cache bleed, non-BMP corruption |
| 3 | Capabilities | months | Feature parity gaps vs "advanced" claim |

Estimates assume one experienced Android engineer familiar with Compose and JNI. They are planning
estimates, not measured velocities.

---

## 8. What NOT to do

- **Do not rewrite the native engine.** It has real libgit2, Tree-sitter, pinned dependencies,
  sanitizers and fuzzing. Replacing it would discard the best part of the codebase.
- **Do not add `fallbackToDestructiveMigration()` to every table without thought.** Wiping the
  workspace table loses user repository registrations. Scope the fallback to cache tables, and add a
  recovery UI for the rest.
- **Do not "fix" the crash by catching `IllegalStateException` around `setContent`.** That hides the
  theme mismatch and leaves the app with a broken window/insets configuration.
- **Do not enable cleartext or disable certificate validation to "make Git work".** If BUG-010 is
  real, fix the CA path.
- **Do not claim production readiness because CI is green.** The upstream project itself marks the
  release gate NO-GO (issue #57).

---

## 9. Verification checklist for the first fixed build

```
[ ] Fresh install, dark mode → launches to Login
[ ] Fresh install, light mode → launches to Login
[ ] Existing data, dark mode → launches to Home or Login
[ ] Toggle dark mode while running → no crash
[ ] Change language to Spanish on a dark device → no crash, language persists after restart
[ ] Kill process, reopen → no crash
[ ] Corrupt DB (debug build) → recovery screen, no crash
[ ] Uninstall native .so (repackaged debug) → "unsupported device" screen, no crash
[ ] Clone a repo → progress visible, cancel works, no partial dir
[ ] Two concurrent commits on one repo → serialized, no index corruption
[ ] Release build without signer → build fails
```

Each line maps to a Phase 0/1 fix and to a finding in report 13.

---

## 10. Recommendation

**Proceed to fork only after Phase 0 and Phase 1.1/1.4/1.5 are merged and device-verified.**

The audit found no architectural reason to reject Nexora Git. It found a small number of
high-consequence defects that are all fixable in days, on top of a genuinely strong native and
authentication foundation. The correct next action is not a rewrite; it is a short, verified
hardening sprint followed by a controlled fork.
