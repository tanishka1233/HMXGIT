# 21 — GitHub OAuth 404 Investigation and Authentication Repair

> **Repository:** https://github.com/tanishka1233/HMXGIT
> **Branch:** `main`
> **Application ID under test:** `com.nexora.git.debug` (version `1.0.0-debug`)
> **Investigation date:** 2026-11-19
> **Device:** OPPO CPH2579, Android 15, night mode
> **Commit of the tested APK:** `729f11c70248280efe5a419c0a3d4e0e516fbb25`
> **Repair commit:** `2e5812b`

---

## 1. Executive summary

**The 404 is not an OAuth endpoint bug and not a URL-construction bug.**

The application builds exactly the correct GitHub authorization URL:

```
https://github.com/login/oauth/authorize?client_id=…&redirect_uri=…&state=…&code_challenge=…&code_challenge_method=S256&prompt=select_account
```

The `client_id` in the APK the user installed is **not a real GitHub App client ID**. It is the
GitHub Actions placeholder baked into every CI artifact:

```
client_id     = Iv1.NexoraObfuscationSentinel
redirect_uri  = https://obfuscation-sentinel.example/oauth/callback
broker        = https://obfuscation-sentinel.example
```

GitHub cannot resolve that client, and its authorization page resolves to **404 — Page not
found**. There is no OAuth application registered anywhere for this repository: `gh secret list`
is empty, `nexora.local.properties` does not exist, and no broker deployment exists. Therefore the
flow cannot authenticate until a real GitHub App, a broker and a correctly configured build are
supplied.

A code-side repair was applied so that a placeholder build **never opens a broken GitHub URL
again**: `AuthConfig.isConfigured` now rejects CI sentinels and reserved placeholder hosts, so the
login screen disables the button and shows the existing "not configured" message instead of
launching a 404.

---

## 2. Trace: what happens when "Continue with GitHub" is tapped

| # | File / line | Action |
| ---: | --- | --- |
| 1 | `feature/auth/LoginScreen.kt:66` | `Button(onClick = onSignIn)` |
| 2 | `ui/NexoraGitApp.kt:114` | passes `state.configured` into `LoginScreen` |
| 3 | `feature/auth/AuthViewModel.kt:88` | `authSessionRepository.beginAuthorization()` |
| 4 | `core/auth/AuthSessionRepository.kt:39-41` | if `!authConfig.isConfigured` → `NOT_CONFIGURED` |
| 5 | `core/auth/AuthSessionRepository.kt:44-53` | generate PKCE pair + state, persist pending authorization |
| 6 | `core/auth/GitHubAuthorizationUrlFactory.kt:11-22` | build the authorization `Uri` |
| 7 | `ui/NexoraGitApp.kt:78-83` | `CustomTabsIntent.Builder().build().launchUrl(context, uri)` |
| 8 | `core/auth/OAuthCallbackParser.kt:11-40` | validate the return callback (`scheme/host/path`) |
| 9 | `core/auth/AuthSessionRepository.kt:177-215` | state validation → broker exchange → `/user` → Keystore session |

The URL factory itself (`GitHubAuthorizationUrlFactory.kt`) is the single place the authorization
request is constructed. It is correct.

---

## 3. The actual authorization URL

Reconstructed from source plus the configuration compiled into the installed APK. `state` and
`code_challenge` are single-use random values and are shown as placeholders.

```
https://github.com/login/oauth/authorize
  ?client_id=Iv1.NexoraObfuscationSentinel
  &redirect_uri=https%3A%2F%2Fobfuscation-sentinel.example%2Foauth%2Fcallback
  &state=<43-char base64url random>
  &code_challenge=<43-char base64url SHA-256>
  &code_challenge_method=S256
  &prompt=select_account
```

| Parameter | Value in the tested APK | Assessment |
| --- | --- | --- |
| host / path | `github.com/login/oauth/authorize` | **correct** |
| `client_id` | `Iv1.NexoraObfuscationSentinel` | **invalid — CI placeholder** |
| `redirect_uri` | `https://obfuscation-sentinel.example/oauth/callback` | **unreachable reserved host — CI placeholder** |
| `state` | 32 random bytes, base64url, constant-time compared, 10-minute window | correct |
| `code_challenge` / method | S256 of a 32-byte verifier | correct (see §9.3 caveat) |
| `prompt` | `select_account` | not a GitHub parameter; ignored (cosmetic) |
| `scope` | absent | correct for a GitHub App (permissions are configured on the App) |

There is **no URL-encoding defect** and **no wrong endpoint**. `Uri.Builder` percent-encodes the
parameters correctly.

---

## 4. Root cause — evidence chain

1. **The APK under test is a CI artifact.** The installed package was verified by hash on-device:

   ```
   /data/app/~~HBuGPe7e2LSTnMaqqjJixA==/com.nexora.git.debug-BJ_gi_cipqtUAVTT7xwiqA==/base.apk
     = 26ed0d7fab0aeea3e1ef35208d4d1e166524b40f77ead9d1080151910232a91c
   CI artifact app-debug.apk (run 44550719684, commit 729f11c)
     = 26ed0d7fab0aeea3e1ef35208d4d1e166524b40f77ead9d1080151910232a91c
   ```

2. **Android CI deliberately injects placeholders.** `.github/workflows/android-ci.yml:80-82`
   (and `:94-96`):

   ```yaml
   NEXORA_GITHUB_CLIENT_ID: Iv1.NexoraObfuscationSentinel
   NEXORA_AUTH_BROKER_BASE_URL: https://obfuscation-sentinel.example
   NEXORA_GITHUB_CALLBACK_URL: https://obfuscation-sentinel.example/oauth/callback
   ```

   `ci-ephemeral-signing.yml:35-37` uses a second inert sentinel
   (`Iv1.NexoraEphemeralCiOnly` / `ci-signing.invalid`).

3. **The values are compiled into the native library.** `native/config/cmake/NexoraSecureConfig.cmake`
   reads those environment variables, validates the pair, and XOR-encodes them into
   `nexora_secure_config.generated.hpp`; `SecureRuntimeConfigNative.readConfig()` decodes them at
   runtime. That is why a plain `strings` scan of `libnexoragit.so` shows nothing — the guard script
   `scripts/ci/validate-runtime-config-obfuscation.sh` enforces exactly that.

4. **The placeholder pair passed the runtime validation.** `AuthConfig.isConfigured` only checked
   that the broker URL and callback URL share a host/port and that the paths are
   `/oauth/callback` and `/oauth/android/callback`. `https://obfuscation-sentinel.example` satisfies
   all of that, so `configured = true`, the button was enabled, and the Custom Tab opened GitHub.

5. **GitHub's response.** The client does not exist, so the authorization page cannot be rendered
   and the user is served GitHub's generic 404. `obfuscation-sentinel.example` is a reserved,
   non-resolvable hostname; even a real client ID with that redirect target could never complete.

6. **No real configuration exists anywhere in this environment.**

   - `gh secret list` → empty (no `NEXORA_GITHUB_CLIENT_ID`, `NEXORA_AUTH_BROKER_BASE_URL`,
     `NEXORA_GITHUB_CALLBACK_URL`, no signing secrets).
   - `nexora.local.properties` → absent.
   - No broker deployment domain exists in the repository; every host is a placeholder
     (`example.com`, `auth.example.com`, `invalid`, `test`).

**Conclusion:** the 404 is caused by a CI placeholder credential baked into a test artifact, not by
the OAuth implementation. The implementation is architecturally sound; it is unconfigured.

---

## 5. What is *not* the problem

Checked and confirmed healthy:

- Endpoint, query parameters and encoding (§3).
- `state` generation, 10-minute expiry, constant-time comparison (`PendingAuthorizationValidator`).
- Callback parsing restricted to `scheme`, `host` and `path` (`OAuthCallbackParser`).
- `autoVerify` HTTPS App Link for `https://${nexoraAuthHost}/oauth/android/callback`
  (`AndroidManifest.xml:30-39`).
- Token storage in Keystore-backed AES-GCM (`KeystoreCipher`, `SecureAuthStorage`).
- No token or secret logging: no `Log.*`/`println` in the auth packages; Production CI runs
  `scripts/ci/scan-android-artifacts-for-secrets.sh`.
- Broker forwarding and revocation implemented (`auth-broker/main.go`).
- PKCE verifier forwarded to the broker (`AuthBrokerClient.exchange`, `code_verifier`).

---

## 6. Authentication type determination

The repository implements a **GitHub App user-to-server OAuth web flow with PKCE**, not a plain
OAuth App flow and not Device Flow:

- `GitHubAuthorizationUrlFactory` → `github.com/login/oauth/authorize`.
- GitHub callback goes to a broker (`/oauth/callback`), which forwards `code` + `state` to the
  verified App Link `/oauth/android/callback` (`auth-broker/main.go:132-154`).
- Token exchange is performed by the broker, which holds `GITHUB_APP_CLIENT_SECRET`
  (`auth-broker/main.go:168-190`) — required because GitHub's token endpoint will not accept a
  secretless exchange.
- Expected token shapes `ghu_` / `ghr_` (`AuthBrokerClient`, revoke handler) are GitHub **App
  user access tokens**.
- Revocation uses the GitHub App application-token endpoint (`auth-broker/main.go` `revoke`).

**Implication:** this architecture **requires a deployed HTTPS broker** in addition to a registered
GitHub App. A client secret cannot be placed in the APK. Replacing it with Device Flow would be an
architecture change and was not made without justification.

---

## 7. Repair implemented

`app/src/main/java/com/nexora/git/core/auth/AuthConfig.kt`

```diff
     val isConfigured: Boolean
         get() = githubClientId.isNotBlank() &&
+            !isPlaceholderRuntimeConfig(
+                githubClientId = githubClientId,
+                brokerBaseUrl = brokerBaseUrl,
+            ) &&
             isValidAuthEndpointPair(...) &&
             isValidAppCallbackUri(...)
```

New pure function `isPlaceholderRuntimeConfig` rejects:

- the project's own CI sentinels (`Iv1.NexoraObfuscationSentinel`, `Iv1.NexoraEphemeralCiOnly`,
  `Iv1.example`, `Iv1.your-client-id`) and any client ID containing `sentinel`, `example` or
  `your-client-id`;
- empty values;
- reserved/unresolvable broker hosts (`localhost`, `example.com`, `*.example.com`, `*.example`,
  `*.invalid`, `*.test`).

It deliberately does **not** attempt a strict GitHub client-ID format check, so a real client ID
from any current GitHub format is accepted.

Effect: with a placeholder build the login screen renders the existing
`login_not_configured` message and the button is disabled (`LoginScreen.kt:65-83`). No GitHub tab is
opened, so the 404 can no longer be reached from a CI artifact.

Tests added in `app/src/test/java/com/nexora/git/core/auth/AuthEndpointPolicyTest.kt`:
`rejectsCiSentinelRuntimeConfiguration`, `rejectsReservedOrEmptyRuntimeConfiguration`,
`acceptsRealisticRuntimeConfiguration`.

No authentication behavior, endpoint, callback or broker was changed.

---

## 8. Required GitHub configuration (account-level, not code)

These steps are the owner's to perform. No credentials are invented here.

### 8.1 Create the GitHub App

1. GitHub → Settings → Developer settings → GitHub Apps → **New GitHub App**.
2. Name: `Nexora Git`.
3. Homepage URL: `https://AUTH_HOST`.
4. **Callback URL**: `https://AUTH_HOST/oauth/callback` (exact, no trailing wildcard).
5. Enable **User authorization** and **Expire user authorization tokens**.
6. Permissions: start least-privilege, matching `docs/GITHUB_APP.md`
   (Metadata: Read; Contents: Read/Write; Pull requests: Read/Write; Issues: Read/Write; Actions:
   Read; Commit statuses/Checks: Read).
7. **Client ID** (`Iv1.…`) is public configuration. **Client secret** never leaves the broker.

`AUTH_HOST` must be a real domain you control with valid HTTPS.

### 8.2 Deploy the auth broker

```env
GITHUB_APP_CLIENT_ID=Iv1.<from GitHub>
GITHUB_APP_CLIENT_SECRET=<from GitHub, confidential>
GITHUB_CALLBACK_URL=https://AUTH_HOST/oauth/callback
APP_CALLBACK_URI=https://AUTH_HOST/oauth/android/callback
PORT=8080
```

`gh secret list` is currently empty, so `NEXORA_AUTH_BROKER_BASE_URL` /
`NEXORA_GITHUB_CALLBACK_URL` are not configured for CI either. Once set, a CI/VPS build can use
them without exposing the secret.

### 8.3 Publish the Android App Link association

`https://AUTH_HOST/.well-known/assetlinks.json` must list:

```json
{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.nexora.git.debug",
    "sha256_cert_fingerprints": ["<DEBUG SIGNING CERT SHA-256>"]
  }
}
```

Use the **debug** package and debug certificate for the test build; the release build needs
`com.nexora.git` and the release certificate. Without this, the browser callback will not return to
the app.

### 8.4 Build with the three public values

```bash
export NEXORA_GITHUB_CLIENT_ID="Iv1.<real>"
export NEXORA_AUTH_BROKER_BASE_URL="https://AUTH_HOST"
export NEXORA_GITHUB_CALLBACK_URL="https://AUTH_HOST/oauth/callback"
./gradlew :app:assembleDebug
```

or place the same three keys in the gitignored `nexora.local.properties` copied from
`nexora.local.properties.example`.

Only the client ID and two URLs are accepted by the Android build; the secret is broker-only.

---

## 9. Security review

| Check | Result |
| --- | --- |
| Client secret in APK | **No** — only three public values are read; CMake bakes public values; the secret is used solely by the broker. |
| Tokens in logs | **No** — no logging in auth packages; Production CI secret scan. |
| Token storage | Keystore-backed AES-GCM with per-account keys (`SecureAuthStorage`, `KeystoreCipher`). |
| `state` | 32 random bytes, base64url, constant-time comparison, 10-minute window, cleared after use. |
| Redirect | Exact host/path match plus verified HTTPS App Link (`autoVerify`). |
| PKCE | Implemented and forwarded. Caveat: GitHub's documented web-flow parameters do not include PKCE, so it is not relied on as the primary control; the broker-held secret and `state` are. |
| Credentials committed | None. |
| Error handling | Cancellation/denial, state mismatch, expiry, exchange failure and identity failure each map to distinct user-facing messages. |

No credentials were printed in this report; the CI client ID shown is a public placeholder, not a
secret.

---

## 10. Device test results

| Test | Result |
| --- | --- |
| P0 night-theme startup crash | **Fixed and device-confirmed** — the app now launches (user-reported, dark mode). |
| Tap "Continue with GitHub" | Reproduced: GitHub shows **404 — Page not found**. |
| Authorization page reachable | **No** — for the placeholder client ID. |
| Login + authorization + callback | **Not performed** — no real GitHub App and no broker exist. |
| Callback return / token storage / API calls | **Not performed** — blocked by the above. |
| Cancellation / denial / invalid responses | Not testable without a working broker. |
| Post-repair placeholder guard | **Not installed on the device yet** — see limitations. |

The user-visible symptom is fully explained by §4. Authentication cannot be demonstrated until the
account-level configuration in §8 exists.

---

## 11. GitHub Actions results (repair commit `2e5812b`)

All runs have `head_sha = 2e5812b` (the same commit).

| Workflow | Run | Result |
| --- | ---: | --- |
| Android CI | 44565756557 | **success** |
| Production CI | 44565756470 | **success** |
| CodeQL | 44565756462 | **success** |
| Foundation CI | 44565756548 | **success** |

No workflow, test or check was disabled or weakened. Unit tests (including the new placeholder
guard tests) executed in Android CI.

---

## 12. Remaining limitations / next steps

1. **No GitHub App, no broker, no secrets** → authentication cannot work yet. §8 is required.
2. **The device is offline** (`192.168.0.103:45165` refused), so the placeholder-guard build has not
   been installed or launched. A device test of "button disabled + message shown" is still pending.
3. **Debug vs release**: the installed APK is a debug artifact. A real user build must be built with
   the three public values, and the App Link association must match the actual package and signing
   certificate.
4. **PKCE is not a documented GitHub web-flow control**; the broker + state + secret are the real
   protections.
5. **APK-only distribution alternative**: if a broker/VPS is not acceptable, the only way to remove
   the backend dependency is GitHub **Device Flow** (`https://github.com/login/oauth/authorize/device/code`),
   which needs no client secret. That would replace the browser flow and was not implemented
   without owner direction.

**Status:** root cause identified and documented; the misleading 404 path is closed in code; live
authentication remains blocked on account-level configuration that only the owner can perform.
