# HopOn Rav-Kav — Static Analysis Log (New-Vulnerability Hunt)

**Goal:** find *additional* vulnerabilities (beyond the two already in `VULNERABILITY_REPORT.md`) to attach to the report, using **static analysis only**. No new live requests are sent to `api.hopon.co.il`; existing capture files are reviewed read-only. This boundary matches the recommendation already recorded in `NOTES.md` (further live guessing against production should be done by HopOn's own team).

**Authorization:** AUTH-2026-001, valid 2026-08-01 to 2027-01-01. Target `co.hopon.android.rkpos` v2.0.87-1.0.8 (test build).

**Date started:** 2026-08-14

---

## Environment / how the source was produced

- APK: `טעינה+בקיוסקים_2.0.87-1.0.8_for testing.apk` (12,222,365 bytes).
  - The original filename contains Hebrew characters, which the Windows Java console mangled ("File not found" with `?????`). **Workaround:** copied the APK to an ASCII-only scratchpad path (`hopon_rkpos_2.0.87-1.0.8.apk`) before decompiling.
- Tool: jadx 1.5.6 (`pentest/tools/jadx/lib/jadx-1.5.6-all.jar`), invoked via `java` directly because the `bin/jadx` wrapper's classpath didn't resolve on this machine:
  ```
  java -Xmx2g --enable-native-access=ALL-UNNAMED \
    -cp pentest/tools/jadx/lib/jadx-1.5.6-all.jar jadx.cli.JadxCLI \
    -d <scratch>/jadx_out <scratch>/hopon_rkpos_2.0.87-1.0.8.apk
  ```
  - Result: 5190 classes processed, "finished with errors, count: 123" (non-fatal per-method decompile failures — normal for R8/obfuscated code). 7425 files written under `jadx_out/`.
  - Output lives in the session scratchpad (not committed under `pentest/`, to avoid copying the whole app source tree into the deliverable). Key excerpts that support findings are quoted inline in this log and in the report.
- App code of interest: `co.hopon.*` (app) and `co.hopon.sdk.*` (network/SDK). Heavy obfuscation elsewhere (single-letter packages `a/`, `b/`, `defpackage/`).

---

## Method

Sweeping for issue classes not yet covered by Findings 1–2:
1. Hardcoded secrets / API keys / signing keys / OAuth client secrets.
2. `AndroidManifest.xml`: exported components (activities/services/receivers/providers), `debuggable`, `allowBackup`, custom permissions, deep links.
3. Insecure local storage (SharedPreferences/SQLite/files) of tokens, PINs, card data.
4. Weak crypto / hardcoded keys/IVs / ECB / static seeds.
5. WebView misconfig (JS bridges, `setAllowFileAccess`, `loadUrl` with untrusted input).
6. Additional broken-access-control / client-trust patterns like Finding 1.
7. Logging of sensitive values.

Each subsection below records what was searched, what was found, and a verdict (CONFIRMED finding / lead / cleared).

---

## Environment selector (checked, NOT a finding)

`co.hopon.sdk.PRODUCTION_CODE` meta-data = `@integer/production_code` = **0**. `AppLaunchBase` (`co/hopon/sdk/p/a.java:23,43`) maps code 0 → `f()` = `ProductionFlavor` (`co/hopon/sdk/p/f.java`), whose base is `https://api.hopon.co.il/v0.15/1/isr/`. So this "for testing" build points at **production** (matches `NOTES.md`, where prod was hit). The "test" refers to the test *account*, not a test server. Cleared.

`ProductionFlavor` also holds the payment-gateway config (not secret, but recorded for context): CreditGuard relay `https://cgpay4.creditguard.co.il/xpo/services/Relay`, terminal `9010260012`, mid `2000`, and `https://symcors.com`.

## Attack-surface items checked and CLEARED (documented for honesty)

- **Exported components:** only `co.hopon.android.rkpos2.Splash` is exported (it is the LAUNCHER). Every `provider`/`service`/`receiver` in the manifest is `exported="false"`. No exported-component / intent-injection surface. The `RKEXtra.EXTRA_PRE_PURCHASE_REQUEST_REPO` intent-tampering idea floated in `NOTES.md` is **not** reachable cross-app: no exported activity accepts it.
- **`transformed_amount` lead from `NOTES.md`:** `RavKavChargeRequestBody` (`.../network/v1/requests/RavKavChargeRequestBody.java`) does carry a client-supplied `transformed_amount` (double) and `coupon_discount` (double). **But `grep` for `new RavKavChargeRequestBody` / `getTransformedAmount` across all of `co.hopon` returns zero hits — the class is never constructed in this build.** It is dead/unused model code here, not reachable, so it is **not** an exploitable finding in this APK. (If a future build wires it up, the client-controlled amount should be re-examined server-side.)
- **API query-parameter secrets:** full Retrofit interface enumerated (`co/hopon/sdk/network/v1/g.java`, 20+ endpoints). The only `@Query` carrying a *secret* is `GET kiosk/validate/pin_code?pin_code=` — already Report Finding 2. Other GET queries carry non-secret identifiers (`card_serial`, `contract_id`, dates, device model) — not credentials.

---

# NEW FINDINGS (static analysis; no new live requests)

## NF-A — Authentication credentials stored under a hardcoded key + broken cipher (Medium→High)

**Class:** `SecureStorageV2` = `co/hopon/sdk/network/b.java` (decompiled name; original `SecureStorageV2.java`). Instantiated in `co/hopon/sdk/repo/t.java:157`.

**What is stored:** in SharedPreferences file `"YEK_SFERP_ETAVIRP"` (i.e. `PRIVATE_PREFS_KEY` reversed):
- key `"Y3K_N3K0T"` (`TOKEN_KEY` reversed) = the auth **token**, encrypted.
- key `"Y3K_T3RK1S"` (`SIKRET_KEY` reversed) = the **userSecret**, encrypted.
- key `"userIdentifier"` = user id, **plaintext**.

These are exactly the `{token, userSecret}` returned by `POST tokens` (confirmed in capture `hopon_capture6.flow`: `{"data":{"token":"…","userSecret":"…"}}`).

**Why the encryption is worthless against anyone holding the file:**
- Cipher = `PBEWithMD5AndDES` (`b.java:38,59,91,104`) — legacy MD5-derived **single DES, 56-bit key**. Deprecated/broken.
- PBE password is **hardcoded in the app**: `private static final char[] d = {'H','o','p','O','n'};` (`b.java:25`).
- Salt = the device `android_id` (`b.java:39`), which is readable on the same device; iteration count = 20.
- Therefore anyone with (a) the APK (→ the hardcoded password, same for every install) and (b) the target's SharedPreferences file + its `android_id` can decrypt token and userSecret directly. There is no per-user or hardware-backed key, no Android Keystore, no `EncryptedSharedPreferences`.

**Impact chain (this is the important part):** `userSecret` is not just a session cookie — it is the **HMAC-SHA1 signing key for every API request**. `HOInterceptor` (`co/hopon/sdk/network/v1/f.java:64-72`) signs each request as
`oauth_signature = base64( HMAC-SHA1( key = userSecret, msg = method + "+" + body + "+" + timestamp + "+" + token ) )`.
So recovering `token`+`userSecret` from storage lets an attacker **forge validly-signed requests as that user indefinitely** (until the server rotates the secret) — i.e. offline credential/session theft leading to full account impersonation against the API, without ever needing the user's phone/OTP again.

**Sibling weak cipher:** `CCC` = `co/hopon/sdk/c.java` uses the **same** hardcoded password `"HopOn"` (`c.java:24`) but with a **fully static salt `"abcdefgh"`** (`c.java:32`) — strictly weaker than NF-A (no per-device salt at all). Same `PBEWithMD5AndDES`.

**Severity rationale:** local-access precondition (needs the on-device prefs file), so not remotely exploitable on its own → Medium; elevated toward High because the recovered secret is the API request-signing key (impersonation), and because NF-B below removes the "needs root" precondition.

## NF-B — `allowBackup="true"` lets the credential store be extracted without root (Medium)

`AndroidManifest.xml:37` sets `android:allowBackup="true"` (and there is no `fullBackupContent`/`dataExtractionRules` exclusion). `minSdkVersion=21`. This permits `adb backup` (or cloud/USB backup transports) to pull the app's private data — **including the `YEK_SFERP_ETAVIRP` SharedPreferences from NF-A** — off a **non-rooted** device with ADB/physical access. Chained with NF-A's reversible encryption, that yields full recovery of `token`+`userSecret` → request forgery, with no root required. Fix: `allowBackup="false"` (or exclude the credential prefs via backup rules) and store secrets in Android Keystore-backed `EncryptedSharedPreferences`.

## NF-C — Cleartext traffic allowed, no network-security-config, no HSTS (Low→Medium)

`AndroidManifest.xml:39` `android:usesCleartextTraffic="true"`, and there is **no** `networkSecurityConfig` resource (confirmed: attribute absent, no `res/xml/network_security_config.xml`). API responses also carry **no `Strict-Transport-Security`** header (only `x-frame-options: SAMEORIGIN`, `server: cloudflare` — seen in captures). The app does implement TLS certificate pinning at runtime (the `scripts/unpinning.js` Frida bypass exists precisely because pinning is present), which mitigates casual in-app MITM — but `usesCleartextTraffic=true` means any `http://` code path or bundled library is unprotected and there is no platform-level downgrade protection. Fix: set `usesCleartextTraffic="false"`, add a `networkSecurityConfig` that pins + forbids cleartext, and have the server emit HSTS.

## NF-D — Sensitive values written to logcat (Low→Medium)

- `SecureStorageV2` logs the **stored token blob and its decrypted content**: `Log.w("SecureStorageV2","decryptV2-value:"+str)` (`b.java:56`), `Log.w("SecureStorageV2","decryptV2-bytes-size:"+…)` (`b.java:58`), and `Log.v("SecureStorageV2", "hasCredentials:%s", token)` (`b.java:77`).
- The **`topupToken`** — the one-time key that authorizes writing a contract onto a Rav-Kav card — is logged at `Log.i` in the charge/purchase paths: `co/hopon/sdk/repo/t.java:1093` and `:2172`, `co/hopon/sdk/repo/w.java:98`, `:446`, `:715`, `co/hopon/sdk/repo/y.java:532` (all `"request success, key " + …topupToken`).

logcat is readable by the app itself, by anything with `READ_LOGS`, in vendor bug-reports, and on rooted/older devices. Logging the card-write authorization key and the auth token is a sensitive-data-exposure issue. Fix: strip these logs from release builds (or gate on `BuildConfig.DEBUG` and never log token/userSecret/topupToken).

## NF-E — Hardcoded third-party API keys (Low / informational)

`AndroidManifest.xml:48-49` embeds the Fabric/Crashlytics key `io.fabric.ApiKey = 7e64fe64fb7295aac42f84205b6d9b4722a69001`. `res/values/strings.xml` embeds `google_api_key`/`google_crash_reporting_api_key = AIzaSyD6baPMO7xzLbLZepNniMunLvBVBRTAIEk` and `google_maps_key = AIzaSyCM3HZXk26LfuC80bC6e68wGSDVV-Our_g`. These are client-side keys that necessarily ship in the APK, so extraction is expected — but they should be **API/package-restricted server-side** (esp. the Maps key, to prevent quota/billing abuse). Verify restrictions in Google Cloud / Firebase consoles.

## Supporting detail for existing Report Finding 1 (client-side gate theme)

`isPayWithCashNeedsPinCode` (`co/hopon/sdk/repo/y.java:790-814`) decides **on the client** whether to prompt for the kiosk cash PIN, based on local DB flags (`cashEntity.isRequestPinCode`, `b.java`-style entity field `bVarA.d`) and a **client-evaluated time window** (`kioskCashCodeValidationIntervalMinutes`, compared against `System.currentTimeMillis()`), returning `FALSE` (no PIN needed) when the window is still open. This is another instance of security-relevant cash-flow gating being decided by client-held state — same architectural theme as Finding 1. Whether the server re-validates the PIN/kiosk on the actual charge is what determines real impact (server-side confirmation needed, exactly as Finding 1 recommends).

---

## Summary table of the new work

| Ref | Issue | Severity | Basis |
|-----|-------|----------|-------|
| NF-A | Auth token + userSecret (the HMAC signing key) stored under hardcoded PBE password `"HopOn"` + weak `PBEWithMD5AndDES` | Medium→High | static: `b.java`, `c.java`, `f.java`; capture confirms stored values |
| NF-B | `allowBackup="true"` → extract credential store without root | Medium | manifest:37 (+ chains with NF-A) |
| NF-C | `usesCleartextTraffic="true"`, no network-security-config, no HSTS | Low→Medium | manifest:39; captures |
| NF-D | token / decrypted secret / `topupToken` written to logcat | Low→Medium | `b.java`, `t.java`, `w.java`, `y.java` |
| NF-E | Hardcoded Fabric/Google API keys (restrict server-side) | Low/info | manifest:48-49, strings.xml |
| — | `transformed_amount` (RavKavChargeRequestBody) | not a finding here | dead code: never constructed in this build |
| — | production_code=0 → production; exported comps clean | not a finding | manifest, `p/a.java`,`p/f.java` |

All of the above are from **static analysis + read-only review of the already-captured flows**. No new requests were sent to `api.hopon.co.il` during this pass.
