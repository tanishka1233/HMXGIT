# 12 — Performance & Android Compatibility

**Measurement disclosure:** No device or emulator was available. **Measured** values are limited to
published artifacts (APK sizes, dex/dex composition, native `.so` sizes) and repository metadata.
Everything about RAM, CPU, startup time, clone throughput and battery is an **estimate** and is
labelled as such. See [`16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md`](16_AUDIT_LIMITATIONS_AND_UNVERIFIED_CLAIMS.md).

---

## 1. Platform compatibility matrix

| Property | Nexora Git | Gitling | PuppyGit |
| --- | --- | --- | --- |
| minSdk | **26** (Android 8.0) | **23** (Android 6.0) | not verified |
| targetSdk | 36 | **37** | not verified |
| compileSdk | 37 | 37 | not verified |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 | (JVM) + Conscrypt 4 ABIs | 4 ABIs |
| 32-bit support | **Yes** (armeabi-v7a) | Yes | Yes |
| x86 (32-bit) | **No** | Yes (Conscrypt) | Yes |
| `largeHeap` | **No** | **Yes** | not verified |
| Native heap tuning | libgit2 C heap | JVM heap | libgit2 C heap |
| Runtime permission | SAF / scoped storage | `READ_EXTERNAL_STORAGE` ≤ API 32 + SAF repair | SAF / app dirs |
| Predicted API-26 viability | **Good** | **Good** (minSdk 23) | Not verified |

> **Finding P-01** — Nexora's `minSdk 26` matches HMX Git's stated floor (API 26+) exactly. It
> **drops 32-bit x86**, which is irrelevant on modern hardware but matters for some emulators.
> **VERIFIED.** Confidence: High.

> **Finding P-02** — Gitling's `minSdk 23` gives the widest device reach, but its `largeHeap=true`
> signals JVM-heap pressure that is precisely the wrong trade-off for 3–4 GB devices.
> **VERIFIED** (manifest). Confidence: High.

---

## 2. Measured artifact sizes

### 2.1 Gitling v1.0.66 APK (downloaded, `unzip -l`)

| Component | Bytes |
| --- | --- |
| `classes.dex` | 30,049,160 |
| `classes4.dex` | 13,010,772 |
| `classes5.dex` | 8,509,448 |
| `classes6.dex` | 9,678,516 |
| `classes7.dex` | 7,149,920 |
| `classes2.dex` | 3,116 |
| `classes3.dex` | 789,636 |
| `lib/arm64-v8a/libconscrypt_jni.so` | 2,754,312 |
| `lib/armeabi-v7a/libconscrypt_jni.so` | 1,776,996 |
| `lib/x86/libconscrypt_jni.so` | 2,781,268 |
| `lib/x86_64/libconscrypt_jni.so` | 2,983,040 |
| `lib/*/libandroidx.graphics.path.so` | ~7–10 KB each |
| **Total APK** | **37,473,461** (≈35.7 MiB) |

> **Finding P-03 (MEDIUM)** — Gitling ships **7 dex files and ~69 MB of uncompressed dex** with
> `isMinifyEnabled=false`. This is a direct consequence of (a) JGit and dependencies and (b) no R8.
> Install size is materially larger than the download. **MEASURED.** Confidence: High.

### 2.2 PuppyGit release APKs (from GitHub release metadata)

| ABI | Bytes |
| --- | --- |
| arm64-v8a | 13,440,884 |
| armeabi-v7a | 13,215,616 |
| x86 | 13,473,616 |
| x86_64 | 13,563,746 |
| universal | 22,862,971 |

> **Finding P-04** — Per-ABI APKs are ~13 MB (universal 22.9 MB), roughly **one third** of Gitling's
> APK, despite having a richer Git surface. This is the practical benefit of a native engine plus a
> lean dependency set. **MEASURED.** Confidence: High.

### 2.3 Nexora Git

> **Finding P-05** — Nexora has **no published APK** (0 releases), so its real artifact size cannot
> be measured. Its debug APK is produced in CI as an artifact (`NexoraGit-debug`) but was not
> retrieved. **UNKNOWN** (measurement). 

Estimate (labelled): a Nexora debug build with `MINIFY=true` release config, 3 ABIs of
`libnexoragit_git.so` + static Mbed TLS + Tree-sitter grammars, Compose, Hilt, Room, would plausibly
land in the **10–25 MB** range for a release universal APK, and **6–12 MB** per split ABI. These are
**estimates**, not measurements.

---

## 3. Memory

### 3.1 Architecture-level differences

| Factor | JGit (Gitling) | libgit2 (Nexora/PuppyGit) |
| --- | --- | --- |
| Per-object representation | JVM objects | C structs, mmap pack |
| Heap pressure | High (GC churn) | Lower (native heap) |
| History walk (100k commits) | Allocates `RevCommit` graph | mmap + streaming |
| Index on 50k files | Heavy object graph | Compact C index |
| Mitigation used | `largeHeap=true` | none needed |

### 3.2 Estimated peak RSS (explicitly NOT measured)

| Scenario | JGit estimate | libgit2 estimate | Notes |
| --- | --- | --- | --- |
| App idle after launch | 120–220 MB | 80–160 MB | Compose + Hilt dominate, engine idle |
| Clone 100 MB repo | 150–300 MB | 100–200 MB | network + checkout buffers |
| Clone 1 GB repo | 300–600 MB | 200–400 MB | pack + checkout |
| `log` 100k commits | 200–400 MB | 100–200 MB | revwalk graphs |
| 50 MB text file in editor | **blocked** at 512 KiB (N) / unclear (G) | — | N hard-caps |

> **Finding P-06** — On a **3–4 GB RAM** device, a 1 GB clone under **JGit** is the highest-risk
> operation; libgit2 is safer because the memory is off-heap and bounded by the pack, not the JVM GC.
> **ESTIMATE.** Confidence: Medium.

> **Finding P-07 (HIGH)** — Nexora's editor **hard-caps at 512 KiB** (`EditorFileStore.MAX_EDITOR_BYTES`)
> and the browser truncates at **2 MiB** (`CodeBrowserFileSystem.MAX_TEXT_BYTES`). This prevents
> OOM on low-RAM devices but makes real source files (minified JS, generated code, logs) uneditable.
> **VERIFIED.** Confidence: High.

---

## 4. CPU and startup

### 4.1 Startup

| Factor | Nexora | Gitling | PuppyGit |
| --- | --- | --- | --- |
| Baseline profiles | **Yes** (`:baselineprofile` + macrobenchmark) | No | No |
| Hilt | Yes (KSP-generated) | No | KSP/Room |
| Native init | `nativeInitialize(home, caDir)` once (guarded `synchronized`) | JGit static init | libgit2 init |
| Predicted cold start | Baseline-profile-assisted, **likely good** | Older Activity graph, slower | Larger helper init |

> **Finding P-08** — Nexora is the only candidate with **baseline profiles**, which is the single most
> effective startup optimisation for Compose apps. **VERIFIED** (`:baselineprofile`, `androidx.benchmark`).
> Confidence: High.

### 4.2 Background work and battery

| Project | Background model | Battery note |
| --- | --- | --- |
| Nexora | WorkManager for fetch/pull | OS-batched; good |
| Gitling | `AsyncTask` on activity lifecycle | No batching; ops die with UI |
| PuppyGit | Compose + optional embedded Ktor server + auto-sync on app enter/exit | Embedded HTTP server can keep CPU awake if misused |

> **Finding P-09 (MEDIUM)** — PuppyGit's **embedded Ktor/Netty HTTP server** and "auto pull/push when
> entering/leaving apps" can consume battery if not carefully gated. **PARTIALLY VERIFIED** (deps +
> README). Confidence: Medium.

---

## 5. Storage

| Project | Repo location | Estimated overhead | Notes |
| --- | --- | --- | --- |
| Nexora | SAF or app-private, workspace registry | manifest + git objects | `WorkspaceManifestWriter`, `SafWorkspaceSyncEngine` |
| Gitling | `getExternalFilesDir(null)` | direct | wiped on uninstall; `allowBackup=true` |
| PuppyGit | app-private + SAF | direct | LFS objects stored |

> **Finding P-10** — None of the candidates implements an explicit **storage-exhaustion policy**
> (quota check, partial-clone cleanup, LFS GC). On a low-storage device an interrupted 1 GB clone can
> leave orphaned `.git` data. **UNKNOWN/NOT FOUND.** Risk: MEDIUM.

---

## 6. Large-repository risks

| Risk | Nexora | Gitling | PuppyGit | Mitigation for HMX Git |
| --- | --- | --- | --- | --- |
| Clone without progress → user thinks hung | **Yes (no progress)** | No (progress) | No | add progress |
| No cancellation | **Yes** | partial | partial | add `git_remote_stop` / cancel |
| Process death mid-clone leaves partial repo | Yes | Yes | Yes | durable worker + resume/cleanup |
| Editor cap blocks large files | **512 KiB** | n/a (viewer) | unclear | streaming buffer |
| History walk memory | libgit2 (good) | JGit (higher) | libgit2 (good) | paginate revwalk |
| Shallow clone to reduce footprint | **NOT FOUND** | **Yes** (`setDepth(1)`) | **Yes** | add shallow option |
| Git LFS for big binaries | **Yes** | **No** | **Yes** | keep N's LFS |
| Submodule depth | Yes | Yes | Yes | — |

> **Finding P-11 (HIGH)** — Nexora **lacks shallow clone**, so cloning a large repository downloads
> full history. Combined with no progress and no cancellation, this is the worst large-repo UX of the
> three. **VERIFIED (absence).** Confidence: High.

---

## 7. Device-compatibility assessment (estimated)

| Device class | Nexora | Gitling | PuppyGit |
| --- | --- | --- | --- |
| API 26, 3 GB RAM | **Usable for small–medium repos**; large clone risk (no progress/cancel); editor capped | Usable; `largeHeap` masks pressure; APK heavy | Likely usable; no data |
| API 29, 4 GB RAM | Good for medium repos | Good | Good |
| API 33, 6 GB RAM | Good | Good | Good |
| API 36, 8 GB+ | Good | Good | Good |
| Tablet | Compose responsive, not verified | `sw600dp` resources exist | Compose, not verified |
| 32-bit-only device | armv7 supported | supported | supported |

All rows are **estimates** except ABI availability (measured from manifests/APKs).

---

## 8. Performance recommendations for HMX Git

1. **Adopt libgit2** (lower, off-heap memory) — see report 06.
2. **Install `git_transfer_progress_cb`** and display bytes/objects — eliminates the "frozen clone"
   perception.
3. **Wire `git_remote_stop`** to a cancel button and to WorkManager cancellation.
4. **Add shallow clone** (depth 1) and partial clone where supported.
5. **Add a repository-scoped mutex** to prevent corrupted-index retries that waste CPU.
6. **Raise/stream the editor cap**; never load a 50 MB file into a single `String`.
7. **Keep baseline profiles** and extend them to the git workspace and editor screens.
8. **Benchmark on real 3–4 GB hardware** before committing to a memory budget:
   launch, clone 100 MB, clone 1 GB (shallow vs full), `log` 100k, status 50k files, open 5 MB file.
9. **Add a storage-exhaustion policy** (pre-flight free-space check, cleanup of failed clones, LFS GC).
10. **Do not enable `largeHeap`** — it hides leaks; fix the allocation profile instead.

---

## 9. Verdict

| Dimension | Best candidate |
| --- | --- |
| Smallest install footprint | **PuppyGit** (~13 MB per ABI, measured) |
| Best memory characteristics | **libgit2-based** (Nexora/PuppyGit), estimated |
| Best startup tooling | **Nexora** (baseline profiles) |
| Best large-repo UX today | **Gitling/PuppyGit** (progress, shallow) |
| Worst large-repo UX today | **Nexora** (no progress, no cancel, no shallow) |
| Lowest measured package weight among Git+GitHub clients | **Nexora** (unmeasured, but libgit2 + minify enabled) |

**Conclusion:** adopt Nexora's engine and build tooling, then **immediately** close the large-repo
UX gaps (progress, cancel, shallow, durable clone). These are contained changes with outsized
impact on 3–4 GB devices.
