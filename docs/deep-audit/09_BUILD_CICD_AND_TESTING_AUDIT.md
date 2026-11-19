# 09 — Build, CI/CD & Testing Audit

**Critical disclosure:** **No Android build was executed in this audit.** The audit environment has
JDK 17 (Gitling requires JDK 21), no Android SDK/NDK, no Gradle distribution beyond the wrappers, and
no network access to `dl.google.com` SDK components. Every build/test statement below is derived
from (a) build configuration files, (b) CI check-run conclusions retrieved from the GitHub API, and
(c) the published Gitling APK. Nothing is fabricated. See
[`16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md`](16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md).

---

## 1. Build configuration comparison

| Item | Nexora Git | Gitling | PuppyGit |
| --- | --- | --- | --- |
| AGP | **9.4.0** | **9.2.0** | **8.13.2** |
| Kotlin | 2.2.10 | 2.3.10 | 2.2.0 |
| Gradle wrapper | 9.6.0 | 9.4.1 | not inspected in depth |
| JDK (CI) | 17 | **21** | not verified |
| compileSdk | 37 | 37 | not verified |
| targetSdk | 36 | 37 | not verified |
| minSdk | **26** | **23** | not verified |
| NDK | 27.2.12479018 | none (JGit only) | custom script-built `.so` |
| CMake | 3.22.1 | n/a | custom scripts |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 | n/a (plus Conscrypt 4 ABIs) | 4 ABIs |
| Modules | `:app`, `:baselineprofile` | `:app` | `:app` |
| Version catalog | `gradle/libs.versions.toml` | `gradle/libs.versions.toml` | inline versions |
| Release minify | **true** | **false** | not verified |
| Core-library desugaring | not required (minSdk 26) | **true** + ASM JGit patch | n/a |
| Repositories | google, mavenCentral | google, mavenCentral, **JitPack** | google, mavenCentral, **JitPack** |
| Determinism aids | wrapper SHA-256, pinned native SHAs, container-cache | standard | manual |

> **Finding B-01** — Nexora is the only project that **pins its Gradle distribution by SHA-256** and
> its **native dependencies by immutable commit**. **VERIFIED.** Confidence: High.

> **Finding B-02** — Gitling's build is **not reproducible from a clean checkout without its ASM
> bytecode transformer**, which depends on AGP classloader internals. **VERIFIED** (build file
> comments). Risk: MEDIUM.

> **Finding B-03** — PuppyGit requires **pre-built native libraries** (`libgit24j.so`, `libgit2.so`,
> `libssh2.so`, OpenSSL) produced by shell scripts in `lib_build_scripts/`; the Gradle build alone
> does not build the engine. **VERIFIED.** Risk: HIGH for onboarding/CI.

---

## 2. CI/CD

### 2.1 Nexora Git — 18 workflows

| Workflow | Trigger | Key steps | Latest verified conclusion |
| --- | --- | --- | --- |
| `android-ci.yml` | push `main`/`phase-*`, PR → main, path-filtered | `assembleDebug`, `assembleDebugAndroidTest`, `:baselineprofile:assemble`, `testDebugUnitTest`, `lintDebug`, config-obfuscation check, upload debug APK | **success** (`b36c868`) |
| `native-git-ci.yml` | push/PR on `native/**` | cmake build, `ctest`, **ASan+UBSan**, `clang-tidy`, **fuzz** secure decoder, pinned offline build | **success** (`3d0f933`) |
| `production-ci.yml` | push/PR | `assembleRelease`, `bundleRelease`, `testDebugUnitTest`, `lintRelease`, secret scan, size budgets, VPS release-signing integration test | **success** (`b36c868`) |
| `release.yml` | tag `v*` | signed release from VPS keystore, metadata + homepage validation, secret scan | configured; **never executed** (0 tags) |
| `codeql.yml` | push/PR/schedule | Kotlin/Java, C/C++, Go, TS/JS | success on `f4befb6` (Kotlin/Java **failure** seen on `3d0f933`) |
| `dependency-review.yml` | PR | dependency diff review | active |
| `foundation-ci.yml` | push/PR | repo foundation validation | success |
| `performance.yml` | push/manual | Android performance qualification | active |
| `fdroid-offline-android-ci.yml` | push | isolated offline Gradle rebuild | **success** (run 37904332417) |
| `fdroid-offline-native-ci.yml`, `fdroid-reproducibility.yml` | push | offline native + reproducibility | active |
| `auth-broker-ci.yml` | push `auth-broker/**` | Go build + tests | success |
| `ci-ephemeral-signing.yml` | PR | ephemeral signed build (`com.nexora.git.ci`) | success |
| `community-readiness.yml`, `web-ci.yml`, `vps-installer-ci.yml`, `store-distribution-ci.yml`, `marketing-screenshots.yml`, `render-store-artwork.yml` | various | supporting gates | active |

**Evidence discipline used here:** conclusions were read from
`GET /repos/.../commits/{sha}/check-runs`, not from README badges.

Verified check-runs:

- `b36c868`: `Build, lint and unit test` **success**; `Release build and production gate`
  **success**; all four CodeQL jobs **success**.
- `3d0f933`: `ASan and UBSan native tests` **success**; `Build and test libgit2 core` **success**;
  `Fuzz secure native runtime decoder` **success**; `Pinned native build without network`
  **success**; `clang-tidy project-owned native code` **success**; **`Analyze Kotlin/Java`
  failure**.
- `f4befb6` (main HEAD): only **CodeQL** jobs present in check-runs; Android CI / native CI /
  production CI did **not** run on this SHA (path filters).

> **Finding B-04 (positive)** — Nexora's CI is **substantial and real**: build + lint + unit tests +
> release build + native sanitizer/fuzz + CodeQL + secret scanning + artifact size budgets.
> **VERIFIED.** Confidence: High.

> **Finding B-05 (MEDIUM)** — The **`main` HEAD has no Android build evidence** on its own SHA, and
> CodeQL's Kotlin/Java analysis failed on a recent commit. Badges are green for earlier commits.
> **VERIFIED.** Confidence: High.

> **Finding B-06 (HIGH)** — `release.yml` has **never run**: there are 0 tags and 0 releases.
> The signing, VPS keystore and store-metadata pipeline is **UNPROVEN**. **VERIFIED.** Confidence:
> High.

### 2.2 Gitling — 3 workflows

| Workflow | Trigger | Steps | Evidence |
| --- | --- | --- | --- |
| `build.yml` | push to **`main`** (default branch is `master`), PR opened/synchronized | `./gradlew --no-daemon clean assembleDebug testDebug` on JDK 21 | 113 runs; latest 10 all `pull_request` → **success** |
| `release.yml` | tag `v[0-9]+.[0-9]+.[0-9]+` | decode keystore, `assembleRelease -Pspecial …`, publish APK | **success** for `v1.0.66` |
| Pages | push | website | dynamic |

> **Finding B-07 (MEDIUM)** — `build.yml` triggers on pushes to `main`, but the **default branch is
> `master`**. Direct pushes/merges to `master` are **not built**. PR checks compensate, but the
> v1.0.66 merge commit itself shows only the `release` check. **VERIFIED.** Confidence: High.

> **Finding B-08 (positive)** — Gitling's **signed release pipeline works**: 68 releases, latest
> `v1.0.66` (2026-11-17) produced a 37,473,461-byte APK. **VERIFIED.** Confidence: High.

> **Finding B-09 (HIGH)** — Gitling CI runs **`testDebug`**, but `testDebug` executes only the
> **5 existing unit tests**; there is **no instrumentation test job**, no API test, no native test
> (none needed), no fuzzing, no dependency-vulnerability scan. **VERIFIED.** Confidence: High.

### 2.3 PuppyGit — 7 workflows

`build_unsigned_apk_only.yml`, `build_libs_and_unsigned_apk.yml`,
`log_build_libs_and_unsigned_apk.yml`, `print_path.yml`, `test - lg2 sha256.yml`,
`test_download_cmake.yml`, `test_use_sdkmanager_cmake.yml`.

> **Finding B-10 (HIGH)** — PuppyGit's workflows are **build/probe scripts, not a quality gate**:
> no tests, no signing, no lint enforcement. Releases are built/signed **manually** by the author.
> **VERIFIED** (workflow names + absence of test steps). Confidence: High.

---

## 3. Test inventory and gap analysis

### 3.1 Test sources that exist

| Project | Unit tests | Instrumentation | Native tests | Total evidence |
| --- | --- | --- | --- | --- |
| **Nexora** | **102 `@Test`** in ~30 files | `FoundationScreenTest` (33 LOC), `MarketingScreenshotTest` (517 LOC) | `native_smoke.cpp` (large assertion suite + real bare-remote workflow) | **Best** |
| **Gitling** | **5 `@Test`** in 2 files (`RepoTest` ×2, `SshCredentialsProviderTest` ×3) | **none** (`app/src/androidTest` absent) | n/a | **Weak** |
| **PuppyGit** | template examples only | template example | none | **Minimal** |

### 3.2 Nexora test coverage map

| Area | Test file(s) | What it proves |
| --- | --- | --- |
| Auth | `PkceGeneratorTest`, `PendingAuthorizationValidatorTest`, `AuthEndpointPolicyTest` | PKCE + state + endpoint policy |
| GitHub API | `GitHubLinkHeaderParserTest`, `GitHubRateLimitManagerTest`, `GitHubApiErrorMapperTest`, `GitHubGraphQlErrorClassifierTest`, `GitHubPermissionResolverTest`, `GitHubUrlResolverTest` | pagination, rate limits, errors, host allow-list |
| Parsers | `GitJsonParserTest`, `AdvancedGitJsonParserTest`, `IssueJsonParserTest`, `PullRequestJsonParserTest`, `ReleasesJsonParserTest`, `GitHubActionsJsonParserTest`, `RepositoryJsonParserTest`, `ExploreJsonParserTest`, `SocialJsonParserTest` | JSON contracts |
| Editor | `EditorCoreTest`, `EditorFormatterTest`, `SyntaxJsonParserTest`, `TreeSitterGrammarSelectionTest`, `LspJsonRpcCodecTest` | undo/redo, format, syntax JSON, LSP codec |
| LFS | `GitLfsManagerTest`, `GitLfsTransportTest` | pointer parsing + transport |
| Security | `GitRemoteSecurityPolicyTest`, `WorkspacePathPolicyTest` | host allow-list, path traversal |
| Search | `ProjectSearchEngineTest` | search engine |
| Native | `native_smoke.cpp` | init/clone/fetch/pull/push/merge/rebase/cherry-pick/reset/stash/tags/submodules/blame against a **filesystem bare remote** |

> **Finding B-11 (positive)** — Nexora's native `native_smoke.cpp` creates a **real bare repository**
> and exercises a **multi-clone push/pull workflow**. This is the only genuine engine integration
> test in the audit. **VERIFIED** (source read; `native-git-ci.yml` job success recorded). Confidence:
> High.

### 3.3 Coverage gaps (all projects)

| Gap | Nexora | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Concurrent Git operations | ✗ | ✗ | ✗ |
| Cancellation mid-transfer | ✗ | ✗ | ✗ |
| Interrupted clone/push recovery | ✗ | ✗ | ✗ |
| Merge/rebase conflict resolution e2e | ✗ | ✗ | ✗ |
| JNI boundary tests from Kotlin | ✗ | n/a | ✗ |
| Live GitHub API contract tests | ✗ | ✗ | ✗ |
| OAuth flow tests against GitHub | ✗ | ✗ | n/a |
| Large-repository performance tests | partial (`performance.yml`) | ✗ | ✗ |
| Low-memory/device matrix | ✗ | ✗ | ✗ |
| Fuzzing of repository parsing | ✗ (config decoder only) | ✗ | ✗ |
| Dependency vulnerability scanning | ✓ (`dependency-review`) | ✗ | ✗ |
| Secret scanning | ✓ | ✗ | ✗ |

> **Finding B-12 (HIGH)** — Across all candidates, **there is no test that proves an interrupted or
> concurrent Git operation is safe**, and none proves conflict resolution. These are exactly HMX
> Git's riskiest paths.

---

## 4. Build blockers for HMX Git adoption

| Blocker | Applies to | Severity | Notes |
| --- | --- | --- | --- |
| Android SDK 37 + build-tools installation | all | Medium | `dl.google.com` not reachable in this audit env |
| NDK 27.2.12479018 + CMake | Nexora, PuppyGit | **High** | multi-GB download; arm64+armv7+x86_64 builds |
| JDK 21 | Gitling | Medium | audit env has JDK 17 |
| External secrets (`NEXORA_GITHUB_CLIENT_ID`, broker URL, callback) | Nexora | **High** | build fails without them; CI uses sentinel values |
| Auth broker deployment (Go + domain + TLS) | Nexora | **High** | runtime requirement, not build |
| JitPack availability | Gitling, PuppyGit | Low | third-party repo dependency |
| ASM JGit transformation under AGP 9.x | Gitling | Medium | classloader-coupled |
| Prebuilt native libs | PuppyGit | **High** | not built by Gradle |

---

## 5. Release maturity

| Project | Signed release ever produced | Store distribution | Reproducibility evidence |
| --- | --- | --- | --- |
| Nexora | **No** | None | F-Droid offline/reproducibility workflows exist but F-Droid qualification is **open** (#50) |
| Gitling | **Yes (68)** | Play + F-Droid + GitHub | No reproducibility workflow; standard AGP build |
| PuppyGit | **Yes** (multiple) | IzzyOnDroid + GitHub | Manual author build |

> **Finding B-13 (CRITICAL for Nexora)** — Nexora has **never produced a public signed release**, and
> its own release gate (#57) is titled **"release NO-GO"** with unchecked VPS/device/store items.
> **VERIFIED.** Confidence: High.

> **Finding B-14 (positive for Gitling)** — Gitling's release engineering is the most mature and
> should be used as a **process reference** (tag → keystore decode → `assembleRelease -Pspecial` →
> publish) even if its code is not adopted.

---

## 6. Verdict

| Question | Answer |
| --- | --- |
| Which project has the best CI? | **Nexora** (18 workflows, sanitizers, fuzz, CodeQL, secret scan, size budgets) |
| Which project has the best tests? | **Nexora** (102 unit + real native integration suite) |
| Which project has the best release pipeline? | **Gitling** (68 signed releases, store distribution) |
| Which project is buildable in this audit env? | **None** (no SDK/NDK/JDK 21) |
| What is the biggest CI risk for HMX Git? | Nexora's `main` lacks build evidence on HEAD, `release.yml` never ran, and CodeQL Kotlin/Java failed on a recent commit |
| What must be proven before adoption? | A clean local build (SDK 37, NDK 27.2, JDK 17) **and** a device smoke test **and** one signed release dry-run |

**Adoption gate (Phase 0):**

1. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` on a clean machine.
2. `cmake --build` + `ctest` for `native/git` for all three ABIs.
3. Run `native_smoke.cpp` against a real bare remote on-device (arm64 + armv7 emulator/device).
4. Produce a **signed** debug-equivalent APK and install on API 26 and API 33 devices.
5. Re-run `android-ci.yml` on `main` HEAD and confirm green.
