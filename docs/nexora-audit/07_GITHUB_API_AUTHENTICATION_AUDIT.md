# 07 — GitHub API and Authentication Audit

> **Commit:** `f4befb6c1954d956f0a67b14f4ab1642aa185833` · **Audit date:** 2026-11-19
> **Scope:** `core/auth` (14 files), `core/network` (4), `core/platform` (20), the Go auth broker,
> and the manifest app-link / callback configuration.
> **Method:** static reading and flow tracing. No OAuth flow was executed; no broker was contacted.

---

## 1. Authentication architecture

Nexora Git does **not** implement the GitHub OAuth device flow or a client-secret exchange in the
app. It uses a **GitHub App + PKCE + self-hosted broker** design:

```
MainActivity (AppCompat)
   │  startSignIn()
   ▼
AuthSessionRepository.beginAuthorization()
   │  PkceGenerator → state + verifier/challenge
   │  SecureAuthStorage.savePendingAuthorization() (Keystore AES-GCM)
   ▼
Custom Tabs → github.com/login/oauth/authorize?client_id=…&state=…&code_challenge=…
   │  user approves on GitHub
   ▼
GitHub redirects to https://<broker>/oauth/callback?code=…&state=…
   │  broker stores the code and redirects to
   ▼
https://<broker>/oauth/android/callback  (app link, autoVerify)
   │  MainActivity receives intent.data → AuthCallbackBus (Channel.BUFFERED)
   ▼
AuthViewModel collector → AuthSessionRepository.completeCallback(uri)
   │  OAuthCallbackParser → validate state (constant-time) + 10-min window
   │  AuthBrokerClient.exchange(code, code_verifier) → TokenBundle
   │  GitHubIdentityClient.getIdentity(accessToken) → GitHubIdentity
   │  SecureAuthStorage.saveTokenBundle + Room upsert + active account
   ▼
GitHubGitCredentialProvider → libgit2 credential callback (x-access-token)
```

The **client secret never ships in the APK**. It lives only in the Go broker. Runtime OAuth
configuration (client ID, broker URL, callback URL, app callback URI) is compiled into
`libnexoragit.so` via `NexoraSecureConfig.cmake` and read through `RegisterNatives`.

**Design verdict: sound.** This is the correct architecture for a public OSS Android GitHub client:
GitHub App tokens, PKCE, no embedded secret, token refresh handled server-side.

---

## 2. OAuth correctness

| Property | Implementation | Verdict |
| --- | --- | --- |
| PKCE method | S256 | Correct (`PkceGenerator.kt`) |
| Code verifier entropy | Random, base64url | Correct |
| `state` generation | Independent random value | Correct |
| `state` comparison | `MessageDigest.isEqual` (constant-time) | Correct |
| Authorization window | 10 minutes, rejects clock skew | Correct (`PendingAuthorizationValidator.kt:15-19`) |
| Pending authorization storage | Encrypted SharedPreferences | Correct |
| Pending authorization cleared | On success path **and** on validation failure | Correct (`AuthSessionRepository.kt:187,191`) |
| Callback parsing | Strict host/path/query validation | Correct |
| Error path | Revokes token if identity fetch fails | Correct (`AuthSessionRepository.kt:207-216`) |

This is materially better than most hobby OAuth implementations. The 10-minute window, constant-time
compare and revoke-on-failure are exactly what a security review would ask for.

**One correctness caveat:** `completeAuthorizationCode` clears the pending authorization *before*
the exchange (`AuthSessionRepository.kt:191`). If the exchange fails transiently (network), the user
must restart sign-in. That is safe (no replay) but less forgiving. Not a defect.

---

## 3. Token storage and lifecycle

### 3.1 At rest

- Access and refresh tokens are encrypted with an **Android Keystore AES-GCM** key
  (`KeystoreCipher.kt`), stored in `SharedPreferences("nexora_git_secure_auth")`
  (`SecureAuthStorage.kt`).
- Format: `v1:<base64 iv>:<base64 ciphertext>`.
- On decrypt failure, the token is **deleted** rather than returned
  (`SecureAuthStorage.getDecrypted` → delete path). This is fail-closed, which is correct.
- `allowBackup=false` and backup rules exclude everything, so tokens are not backed up.

### 3.2 Defects

| ID | Severity | Status | Defect |
| --- | --- | --- | --- |
| BUG-015 | P2 | Confirmed | Token writes use `SharedPreferences.apply()` (async). A process kill between `apply()` and disk flush can leave a Room account with no token. |
| BUG-016 | P2 | Suspected | `getOrCreateKey()` is not synchronized; concurrent first use can race key generation. |
| BUG-017 | P2 | Confirmed | Token write, Room upsert and active-account write are three non-atomic stores. |
| BUG-040 | P2 | Confirmed | Native credential copies are not zeroized. |

None of these is a remote vulnerability. All are reliability/defense-in-depth issues.

### 3.3 Token refresh

`getValidAccessToken(accountId)` takes a **global** `refreshMutex` (`AuthSessionRepository.kt:28,92-110`)
and, inside the lock, may perform a network refresh through the broker. Consequences:

- A slow refresh for account A blocks token access for account B and for every Git operation that
  needs credentials (BUG-014).
- There is no per-account backoff; a failing refresh returns `REFRESH_FAILED` and the operation
  fails.
- The broker client uses its own `OkHttpClient` with default timeouts (no explicit connect/read
  timeout), so a hung broker can hold the lock for a long time.

**Fix direction (not applied):** per-account mutex keyed by `accountId`, refresh outside the lock
with a double-check, and an explicit `callTimeout` on the broker client.

### 3.4 Sign-out and revoke

- `signOutActiveAccount` calls the broker's `/v1/oauth/revoke` inside `runCatching`, then deletes
  tokens and the Room row.
- If revoke fails, local state is still cleared. The remote token may remain valid until it expires.
  This is a **security consideration** (P3): a compromised device sign-out is best-effort.

---

## 4. Broker configuration and the "not configured" state

`AuthConfig` reads the native config once at construction and computes `isConfigured` from:

1. non-blank client ID;
2. broker URL and GitHub callback URL form the **same HTTPS origin**, with callback path exactly
   `/oauth/callback`;
3. app callback URI is a valid HTTPS URL on the same host.

If any check fails, `isConfigured = false`, `beginAuthorization()` returns
`AuthFailure.NOT_CONFIGURED`, and the login button is disabled with an explanatory string
(`login_not_configured`).

**This is correct and does not crash.** It is also why a stock build of the repository — with no
`nexora.local.properties` — shows "Authentication is disabled in this build" rather than crashing.

**BUG-007 (HIGH, Confirmed)** is a product-availability finding, not a code bug: the default build
cannot authenticate at all. HMX Git must ship with its own broker or choose a different OAuth
design. The app-link host defaults to `nexora.invalid` when unconfigured, so the callback intent
filter is inert.

**BUG-027 (P2, Confirmed):** a broken `libnexoragit.so` (`JNI_OnLoad` failure) is swallowed by
`SecureRuntimeConfigNative` and looks identical to "not configured". A packaging regression would
silently disable authentication instead of failing loudly.

---

## 5. GitHub identity and REST/GraphQL layer

### 5.1 Identity

`GitHubIdentityClient` calls `GET https://api.github.com/user` with:

- `Authorization: Bearer <token>`
- `Accept: application/vnd.github+json`
- `X-GitHub-Api-Version: 2026-03-10`
- a per-request `User-Agent`

It parses `id`, `login`, `name`, `avatar_url`. Only a non-blank `id` and `login` are required.
Correct.

### 5.2 REST client

- `GitHubRestClient` wraps OkHttp with:
  - `GitHubHeadersInterceptor` (User-Agent, default Accept, API version on `api.github.com` non-GraphQL);
  - `GitHubRateLimitManager` (reads `X-RateLimit-*`, backs off);
  - `GitHubLinkHeaderParser` (pagination);
  - `GitHubApiErrorMapper` (status → typed error);
  - `GitHubResponseCache` (in-memory).
- Endpoints are hand-written URL builders, not Retrofit interfaces. The only Retrofit interface
  (`GitHubApi.getRateLimit`) is **dead code** and `provideRetrofit` has **no converter factory**
  (BUG-018).

### 5.3 GraphQL client

- `GitHubGraphQlClient` posts to `https://api.github.com/graphql`.
- Classifies GraphQL errors (`GitHubGraphQlErrorClassifier`) and has a cache
  (`GitHubGraphQlCache`).
- Uses `org.json`, no codegen. This is consistent with the REST client.

### 5.4 API version

`GitHubApiConfig.REST_API_VERSION` is sent on every REST call. At audit time GitHub documents
`2022-11-28` and `2026-03-10`; the client uses `2026-03-10`, which is current. Good.

### 5.5 Rate limits

`GitHubRateLimitManager` observes `X-RateLimit-Remaining` and `X-RateLimit-Reset` and exposes a
backoff. It is unit-tested (`GitHubRateLimitManagerTest.kt`). It does **not** implement secondary
rate-limit retry (`Retry-After`), and it does not persist core/search/graphql buckets separately in
durable storage. Adequate for v1, P3.

---

## 6. Scopes and permissions

Because the app uses a **GitHub App**, authorization is by installation and fine-grained
permissions rather than classic OAuth scopes. The app performs:

- identity read;
- repository read/write (issues, PRs, contents, actions, releases, social).

The audit did **not** find a client-side scope request list, which is expected for GitHub Apps: the
broker and the GitHub App manifest define permissions. The broker's GitHub App manifest was not
independently verified against live permissions (no broker deployment). This is recorded as an
**Unverified** item in report 18, not a bug.

Missing capability: there is no explicit handling of "installation suspended", "repository not
selected for installation", or "SSO enforcement" responses. These would surface as generic API
errors. P3 capability note.

---

## 7. Deep links and callback security

`AndroidManifest.xml`:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
          android:host="${nexoraAuthHost}"
          android:path="/oauth/android/callback" />
</intent-filter>
```

Findings:

- **Positive:** HTTPS app link with `autoVerify`, exact path, singleTask activity, and
  `state` validation on the callback. A malicious app cannot inject a callback without the correct
  `state` stored in the encrypted pending record.
- **Positive:** the callback is dispatched through a buffered `Channel`, so a cold-start callback is
  not lost (`AuthCallbackBus.kt:12-18`).
- **Gap:** `intent.data` is dispatched in `onCreate` only; there is no `onNewIntent` override. With
  `launchMode="singleTask"`, a callback received **while the Activity is already running** arrives
  at `onNewIntent`, not `onCreate`. Neither `MainActivity` nor a base class overrides
  `onNewIntent`. This means the second and subsequent OAuth callbacks in the same process may be
  dropped.

  This is registered as **BUG-025 (P3, Suspected)** because it depends on whether the Custom Tab
  keeps the Activity alive and whether the system recreates it. It is a real functional risk for
  "switch account / re-authenticate" flows.

- **Gap:** no `android:exported` mismatch; the activity is exported (required for the app link), and
  the OAuth callback is validated by `state`, so export is safe.

---

## 8. Multi-account behavior

- Accounts are stored in Room (`auth_accounts`) and tokens per account in encrypted prefs.
- Switching accounts writes `activeAccountId` in DataStore.
- The global refresh mutex (BUG-014) serializes all refreshes.
- Deleting the active account promotes an arbitrary remaining account (`getAll().firstOrNull()`),
  which is acceptable but not ordered by recency.
- `clearAllLocalSessions` wipes tokens and rows but does not revoke remote tokens.

UI: the profile/settings screens expose account switching. No per-account cache partition was found
for GitHub API caches — `GitHubResponseCache` is a process-wide singleton keyed by URL. After
switching accounts, a cached response from the previous account could be served if the URL is
identical and the entry has not expired. Registered as **BUG-028 (P2, Suspected)**: cross-account
cache bleed.

---

## 9. Authentication findings summary

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| BUG-007 | HIGH | Confirmed | Default build cannot authenticate; requires a deployed broker |
| BUG-014 | P2 | Confirmed | Global refresh mutex serializes all accounts |
| BUG-015 | P2 | Confirmed | `apply()` token persistence can desync from Room |
| BUG-016 | P2 | Suspected | Unsynchronized Keystore key creation |
| BUG-017 | P2 | Confirmed | Non-atomic token/account/active-account writes |
| BUG-025 | P3 | Suspected | No `onNewIntent` for warm OAuth callbacks |
| BUG-027 | P2 | Confirmed | Config JNI failure silently disables auth |
| BUG-028 | P2 | Suspected | Global response cache not partitioned by account |
| BUG-040 | P2 | Confirmed | Native credentials not zeroized |

---

## 10. Authentication verdict

The **protocol design is strong**:

- GitHub App + PKCE S256 + state + 10-minute window + broker-held secret + Keystore-encrypted tokens
  + GitHub-only credential binding.

The **operational weaknesses** are:

1. authentication is inert without a deployed broker (BUG-007);
2. multi-account refresh is globally serialized (BUG-014);
3. token/account state is spread across three non-atomic stores (BUG-015/017);
4. the in-memory API cache is not account-scoped (BUG-028).

None of these explains an immediate startup crash; in fact, the unconfigured path is explicitly
designed not to crash. The startup crash is the theme defect (report 14).
