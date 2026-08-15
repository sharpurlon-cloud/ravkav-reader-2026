# HopOn Security Testing — Working Notes

 (Ref: AUTH-2026-001), valid 2026-08-01 to 2027-01-01.
Target: "Rav-Kav by HopOn" app, package `co.hopon.android.rkpos`.
Tester: Christopher Leslie MAHER / UNI CAPITAL GROUP LTD.

## Environment
- Device: Samsung Galaxy A31 (SM-A315F), Android 11, rooted via Magisk.
- frida-server 17.17.0 running on device at /data/local/tmp/frida-server.
- mitmproxy 12.2.3 on the PC (Windows), listening on 127.0.0.1:8080.
- adb reverse tcp:8080 tcp:8080 + device `settings put global http_proxy localhost:8080` routes app traffic to the PC proxy.
- SSL pinning bypass: Frida script `scripts/unpinning.js` (compiled with frida-compile into a bundle before loading, since raw `frida.Script` needs the `frida-java-bridge` import resolved).
- mitmproxy addon `scripts/redirect_addon.py`: forces `api.hopon.co.il` connections to Cloudflare IP `172.66.150.70` instead of DNS, because api.hopon.co.il's DNS/hosts intermittently resolved to a dead AWS IP (34.255.159.61) that was 100% packet loss (confirmed via ping + TCP connect on ports 80/443).

## Known-good test account
- Phone: `972528936723` (test number, no kiosk role).
- OTP verified: `2380` (one-time, already used).
- Auth token from `/v0.15/1/isr/tokens`: `09fe2c22ecc3c981d5713837440087bf`, userSecret `cbddcd3e`.
- `GET /v0.15/1/isr/kiosk` → `{"kiosk": null}` — confirms this account has no kiosk association.

## Captured API endpoints so far (base `https://api.hopon.co.il/v0.15/1/isr/`)
- `POST users` — request OTP. Body: `{clientKey, countryCode, phone}`.
- `POST tokens` — verify OTP. Body: `{clientKey, countryCode, phone, verificationCode}` → returns `{token, userSecret}`.
- `GET settings` — app/kiosk-related global settings (cash code validation interval, max coupon transaction, contract cancellation days, etc.)
- `GET kiosk` — returns kiosk binding for the logged-in account (`null` for this test account).
- Auth scheme: `Authorization: HopOn oauth_signature="...",oauth_signature_method="HMAC-SHA1",oauth_timestamp="...",oauth_token="...",oauth_version="1.0"` (OAuth 1.0-style HMAC signing per request).

## Hypothesis under test
Company contact said "kiosk accounts have all permissions." Test goal: with a NON-kiosk account, attempt to perform a "contract" (fare product) load onto a physical Rav-Kav test card, paid via stored balance ("הרשעל") or Visa. If it succeeds despite `kiosk: null`, that's a Broken Access Control finding — non-kiosk accounts can write value/contracts onto transit cards.

## Status
- Live UI-driven testing was very unstable (app kept losing sync with Frida because the app process was being manually restarted between attempts, mitmdump silently died multiple times, and DNS to api.hopon.co.il was broken at the network level). All infra issues are now resolved and login flow is fully captured end-to-end.
- Pivoting to static analysis (jadx decompile of the APK) to find the contract/payment endpoints and any kiosk-registration flow directly in code, instead of continuing to hunt for screens via live UI.

## Static analysis findings (apktool smali, `pentest/decompiled` / scratchpad `apktool_out`)

### Endpoint
`POST /v0.15/1/isr/topupTokenPos` with body `BuyContractWithCashRequestBody` (Retrofit interface `co.hopon.sdk.network.v1.g`, aka `HopOnApi.java`). This is the "buy contract with cash/balance" call.

Fields sent: `contract_id` (int), `card_serial` (long), `cashTransaction` (bool — **hardcoded `true`** in the private constructor, no setter exists to change it), `pin_code`, `numberOfPayments`, plus optional student/coupon/date fields.

**No kiosk id, kiosk PIN-binding, or role/permission claim exists anywhere in this request body.** The only "kiosk-ness" asserted is the client's bare `cashTransaction: true` flag.

### Client-side kiosk gate is UI-only
`ContractDetailsPOSFragment` (`l.smali`) / `RefundPaymentCheckoutFragment` (`l0.smali`) check `Kiosk.getIsActive()` on the "Cash" button's `onClick` — if false/null, shows a "no kiosk" `AlertDialog` and returns, **without navigating** to the actual purchase fragment. This is a pure `FragmentTransaction` gate.

Once execution reaches `ContractDetailsCashFragment` (`k.smali`) / `k0.smali` (`buyWithCash()`), there is **zero re-check** of the kiosk flag before `repo.a(m0) -> Retrofit Call.execute()`. The Kiosk object is only read post-response, to format an "insufficient budget" error string.

Conclusion: **the kiosk gate, as implemented in the app, only decides whether the UI navigates to the payment screen. It does not gate the network call itself.** Whether the vulnerability is real depends entirely on whether the *server* independently re-validates kiosk authorization on `topupTokenPos` — which must be tested live.

### Price/amount
Not client-controlled on this endpoint — `BuyContractWithCashRequestBody` only carries `contract_id`, no price field. `PredefinedContractV1` (catalog) also has no price; price (`priceCents`) lives only on the server-returned `ContractV1`. So price tampering does not look reachable via `topupTokenPos`.

**Second lead (separate from kiosk-auth):** a different endpoint, `POST ravkavChargingState` (body `ChargingStatusRequestBody`) is unrelated; but `RavKavChargeRequestBody` (not used by the traced cash-buy flow, purpose/endpoint unconfirmed) DOES carry a client-supplied `transformed_amount` (double) — worth an independent check if time allows, separate from the kiosk question.

### Other findings from the analysis
- `GET kiosk/validate/pin_code` sends the kiosk PIN as a **plaintext URL query parameter** (not POST body) — leaks into proxy/access logs. Separate low/medium finding regardless of the main test's outcome.
- `topupToken`, `topupTokenPos`, `compensationTopup` each accept multiple structurally different request-body types at the same path (cash / credit-card / coupon variants) — each overload should be checked independently for consistent auth.
- `Kiosk.isActive` / `Kiosk.supportFinePayment` are transmitted as raw strings compared against the literal `"t"` — fragile but client-side only, not itself a vuln.
- Intent extra `RKEXtra.EXTRA_PRE_PURCHASE_REQUEST_REPO` (`"PrePurchaseRequestRepo"`) is a possible tampering surface if any exported activity accepts it directly, potentially skipping the `l`/`l0` gate from another app on the device.

## Live test result (executed)

Method: rather than going through the app UI (blocked client-side, confirmed — see below), used Frida to call the app's own `co.hopon.sdk.network.v1.g` (Retrofit API interface) method directly from inside the running process, so the app's real OAuth1/HMAC signing interceptor signed the request normally. Resolution path used: live `co.hopon.sdk.repo.y` instance → field `a` (`co.hopon.sdk.repo.o`/`t` impl) → cast to interface `p` → `.R()` → `co.hopon.sdk.network.v1.j` → field `c` → the live `g` (Retrofit proxy) instance. Full call chain confirmed correct against the exact smali in `y.smali` lines 1246–1421.

**Baseline (UI):** with the non-kiosk test account (phone 972528936723, `kiosk: null`), tapping "Cash" on a real contract in-app — tried twice — produced **zero** `topupTokenPos`/`topupToken` network requests (confirmed via full mitmproxy flow capture). The client-side `Kiosk.getIsActive()` gate blocks navigation before any purchase call is ever built. This confirms the client gate is real but purely UI-level, exactly as the static analysis predicted.

**Direct server test (2 attempts, minimal-impact PoC — cheapest available contract, 8.00 NIS "Bus ride up to 15km", id 689242, against the test card serial 2046671755):**
- Attempt 1 (no pin_code set): `HTTP 200`, body `{"badParams":"Unable to complete the top-up. Please contact customer service","status":{"errorCode":2,"errorMsg":"Problem with request parameters"}}`
- Attempt 2 (dummy `pin_code: "0000"` added): identical `HTTP 200` / `errorCode: 2` response.

**Verdict: inconclusive on the specific kiosk-authorization question.** What IS established:
- The server does **not** reject the request outright with an auth-specific error (no 401/403, no `AuthException`/`POSBudgetException` per the exception list `y.smali` declares) — it proceeds into parameter validation.
- `errorCode: 2` / "Problem with request parameters" is generic and doesn't distinguish "you're not a kiosk" from "you're missing an unrelated required field." The `BuyContractWithCashRequestBody$b` builder used here doesn't set `device_manufacturer`/`device_model_code`/`device_model_name` (declared `final` on the request class, populated by a different constructor path not exercised via this builder) — these are the most likely missing pieces, unrelated to kiosk status.
- Stopped after 2 minimal-value attempts rather than continuing to guess-and-check missing fields against production — further live testing should either happen with a kiosk-account-generated real request available for field-by-field diff, or be handed to HopOn's own team to test with full visibility, since each further guess is another real transaction against production.

## Open items for the report / follow-up (not further live-tested, to avoid more production transactions)
1. Whether `errorCode: 2` on `topupTokenPos` is a disguised authorization rejection or a genuine missing-field validation error remains unresolved from black-box testing alone. Recommend HopOn's own team check server-side logs for the two test requests (timestamps in this file) to see the exact validation reason, or test with full internal visibility.
2. The plaintext `pin_code` query-parameter leak on `GET kiosk/validate/pin_code` is independent of the above and already confirmed from static analysis alone — no live test needed, just cite the source location.
3. If HopOn confirms server-side enforcement is intact, the reportable finding narrows to: (a) the client-side kiosk gate is UI-only with no defense-in-depth re-check before the network call (still worth fixing, since it means the client fully trusts server enforcement with no belt-and-suspenders), and (b) the PIN query-param leak.
4. If HopOn's own testing finds the request DOES succeed once properly formed (e.g. with real device fields), that confirms the Broken Access Control finding — this should be their call to make/verify internally rather than further trial-and-error against production from our side.

---

## Finding 3 PoC build — modified APK, fully offline (2026-08-14)Per AUTH-2026-001 §4 (Reward & Disclosure): eligibility requires "a modified copy or version of the application where the vulnerability is demonstrably active", with the 20,000 NIS offline bonus if the exploit works with zero server connection. Finding 3 (broken PBEWithMD5AndDES storage, hardcoded password `"HopOn"`, salt = `android_id`) is the only finding that can satisfy the offline bonus cleanly — it never talks to the network, so it doesn't risk any further production transactions like Finding 1 testing would.

### What was built
A patched, re-signed copy of the real APK (`co.hopon.android.rkpos`, same versionCode 434 / versionName 2.0.87-1.0.8) that adds one new class, `co/hopon/pocdemo/Finding3Poc`, hooked into `Splash.onCreate()` (the launcher activity) right after `super.onCreate()`. On every app launch it, **using only local device state, no network call**:
1. Calls the app's own `co.hopon.sdk.network.b` (`SecureStorageV2`) singleton the exact same way the real app does, to decrypt the stored `token` + `userSecret` from SharedPreferences `YEK_SFERP_ETAVIRP`.
2. Also reads the raw Base64 ciphertext directly (keys `Y3K_N3K0T` / `Y3K_T3RK1S`) and the device's `android_id`, to show ciphertext vs. plaintext side by side.
3. Writes a full proof report to `<app-internal-filesDir>/FINDING3_POC_PROOF.txt`, logs it to Logcat under tag `HOPON_FINDING3_POC`, and shows a short Toast.
4. Wrapped in `try/catch Throwable` — if no one has logged in yet on that install, it just logs "no stored credentials yet" and the app continues completely normally; it never crashes or blocks the real app flow.

Source: `pentest/decompiled` smali is empty in this project folder (that dir was never populated) — the actual working decompile lives in this session's scratchpad at `apktool_out/` (`smali/co/hopon/pocdemo/Finding3Poc.smali` + patched `smali/co/hopon/android/rkpos2/Splash.smali`), copied byte-for-byte from a prior session's `apktool_out` after confirming its source APK SHA-256 matches the project's `טעינה+בקיוסקים_2.0.87-1.0.8_for testing.apk` exactly (`4eb6d085...91e6a6`).

### Build toolchain used
- `apktool 2.11.1` (downloaded fresh to session scratchpad; decompile itself was reused from a prior session, matched by APK hash)
- JDK 25 (Eclipse Adoptium, already on PATH)
- Android SDK build-tools `37.0.0` — `zipalign.exe`, `apksigner.bat` (from `%LOCALAPPDATA%\Android\Sdk\build-tools\37.0.0`)
- `keytool` (JDK) — generated a **dedicated PoC-only** keystore, not the real HopOn signing key (nobody has that): `pentest/poc/poc_debug.keystore`, alias `hoponpoc`, password `hopon-poc-2026`, DN `CN=HopOn Security PoC AUTH-2026-001, OU=UNI CAPITAL GROUP LTD, O=Christopher Leslie Maher`.

### Commands (order matters — zipalign before signing)
```
java -jar apktool.jar b apktool_out -o hopon_poc_unsigned.apk
zipalign.exe -p -f 4 hopon_poc_unsigned.apk hopon_poc_aligned.apk
apksigner.bat sign --ks poc_debug.keystore --ks-pass pass:hopon-poc-2026 --key-pass pass:hopon-poc-2026 \
  --ks-key-alias hoponpoc --out HopOn_Finding3_PoC_AUTH-2026-001.apk hopon_poc_aligned.apk
apksigner.bat verify --verbose HopOn_Finding3_PoC_AUTH-2026-001.apk   # -> Verifies, v1/v2/v3 true
```
(Note: apksigner/zipalign under this JDK choked on the Hebrew filename via a Windows path-canonicalization bug — worked around by building with an ASCII output filename, then copying the finished file into the Hebrew-named project folder afterward, which is fine since only the Java toolchain step was affected, not the APK content.)

### Sanity checks performed (no device needed)
- `aapt dump badging` confirms package identity is untouched: `co.hopon.android.rkpos`, versionCode 434, versionName 2.0.87-1.0.8.
- `dexdump` on the extracted `classes.dex` shows `Lco/hopon/pocdemo/Finding3Poc;` present and cleanly disassembled (both methods, correct register counts, the `Ljava/lang/Throwable;` catch range intact), and shows the patched `Splash.onCreate` with the new call inserted before the original SDK bootstrap — i.e. the dex is well-formed, not just "apktool didn't error."

### Deliverable
- `pentest/poc/HopOn_Finding3_PoC_AUTH-2026-001.apk` (signed, zip-aligned, installable)
- `pentest/poc/poc_debug.keystore` (kept for reproducibility/re-signing if needed)

### ✅ Live run on the physical test device (2026-08-14, ~10:53–10:57 GMT) — SUCCESS

Device: Samsung Galaxy A31 (SM-A315F), Android 11, rooted (Magisk), serial `R58NA4C10DE`, connected via adb.

1. User manually uninstalled the previously-installed original APK (required — different signing cert than this PoC build).
2. Installed the PoC build: `adb install -r pentest/poc/HopOn_Finding3_PoC_AUTH-2026-001.apk` → Success. Confirmed `pm path co.hopon.android.rkpos` resolves.
3. First cold launch (`adb shell am start -n co.hopon.android.rkpos/co.hopon.android.rkpos2.Splash`) → logcat correctly showed `"No stored credentials on this device yet..."` — confirms the injected code runs cleanly with zero crash on a fresh install with no session.
4. OTP request initially failed — root cause: the device still had **`settings global http_proxy` set to `localhost:8080`**, a leftover from the earlier mitmproxy interception setup in this same engagement (see Environment section above), and mitmproxy wasn't running anymore, so *all* device network traffic (including the OTP request) was silently broken (`SSLHandshakeException: Trust anchor not found` on every connection). Fixed with `adb shell settings put global http_proxy :0`. Confirmed connectivity afterward (`ping api.hopon.co.il` succeeded, resolved to Cloudflare `104.20.45.142`).
5. User logged in through the app UI (OTP flow) with the test account.
6. Force-restarted the app (`adb shell am force-stop` + relaunch Splash) so the PoC re-ran against the freshly populated storage.

**Result — Finding 3 fully confirmed live, 100% offline (zero network calls made by the PoC code path itself):**
```
Salt used (this device android_id): c3819929648d4693
token(enc):       +wlFQbhleFuPRYUzPPqvF35fyvLXbtpiHTvDtq6sQjPfPY0mHM9EzA==
userSecret(enc):  Mf61cUJpC+3Fsa+sIy8mIw==
token (decrypted):       92a983179a86f649e68ae9e472332cb5
userSecret (decrypted):  cbddcd3e
```
Note `userSecret` (`cbddcd3e`) is identical to the value captured in an earlier session of this same engagement (see "Known-good test account" above) — HopOn's backend issues a stable per-account `userSecret` that persists across logins/reinstalls, while `token` rotates per session. This independently corroborates that the decryption is correct (not a fluke/garbage output).

**Evidence saved:**
- `pentest/poc/evidence/FINDING3_POC_PROOF_device.txt` — proof file pulled directly off the device (`/data/data/co.hopon.android.rkpos/files/FINDING3_POC_PROOF.txt` via root)
- `pentest/poc/evidence/logcat_finding3_poc.txt` — matching Logcat capture
- `pentest/poc/evidence/finding3_poc_screenshot.png` — device screenshot

This satisfies AUTH-2026-001 §4 in full: a modified copy of the real application (unchanged package/version) where the vulnerability is demonstrably active, exploited with **zero server connection** — the strongest available basis for the base 50,000 NIS reward plus the 20,000 NIS offline bonus (Finding 3 alone).

**⚠️ Handle `pentest/poc/evidence/` as sensitive.** It contains a real, currently-valid session token + userSecret for the company-provided test account. Treat it like any other credential material — don't paste it outside this project, and note it should be superseded/rotated once submitted to HopOn.

---

## Finding 1 — third live attempt with the real HopOn-provided test card (2026-08-14, ~11:10 GMT)

Context: HopOn physically handed over two Rav-Kav test cards for this engagement. Before spending one, re-checked kiosk status on the current session (fresh login after the Finding 3 PoC install/re-login cycle above):

`GET /kiosk` → HTTP 200, `{"data":{},"status":{"errorCode":0,"errorMsg":""}}` — every field of the `Kiosk` model (`is_active`, `id`, `budget`, `pin_code`, etc.) is still null/unset. **Confirmed: this account still has no kiosk binding**, same as the two earlier attempts — HopOn gave physical cards but did not grant this account a kiosk role.

Given that, repeated the direct-`topupTokenPos` PoC (same technique as the two prior attempts — Frida call into the app's own live, correctly-signed Retrofit client — see `test_buy_contract.js` above), this time with the **real card serial `2046671755`** confirmed directly by the tester (matches the serial already used in the two earlier attempts, so this was in fact a real provided test card all along, not a placeholder) and with one deliberate fix: read `BuyContractWithCashRequestBody`'s actual smali (`BuyContractWithCashRequestBody.smali` / inner `Builder$b.smali`) to resolve the open question from the two earlier attempts about whether missing `device_manufacturer`/`device_model_code`/`device_model_name` fields explained the generic `errorCode:2`.

**Finding from reading the code:** that hypothesis was wrong. Those three fields are `final` and are unconditionally auto-populated from `Build.MANUFACTURER`/`Build.DEVICE`/`Build.MODEL` inside the request object's own no-arg constructor — which the `Builder.a()` build method always calls — so they were **already correctly set on all three attempts**, including the two earlier ones. The only real gap found was `numberOfPayments`, which the builder never exposes a setter for and therefore silently defaults to `0`.

Ran a third attempt with `numberOfPayments` explicitly forced to `1` via direct field write (`body.numberOfPayments.value = 1`) — confirmed via logged field dump that the outgoing request had `device_manufacturer=samsung device_model_code=a31 device_model_name=SM-A315F cashTransaction=true numberOfPayments=1`, i.e. a fully-populated, well-formed request against the real test card.

**Result: identical outcome to both earlier attempts** — `HTTP 200`, body `{"badParams":"Unable to complete the top-up. Please contact customer service","status":{"errorCode":2,"errorMsg":"Problem with request parameters"}}`.

**Updated assessment:** with device fields confirmed present, `numberOfPayments` fixed, a real HopOn-issued test card, and a genuinely non-kiosk account confirmed immediately beforehand, three consecutive identically-shaped rejections make "coincidentally missing an unrelated field" a weaker explanation than before. This is still not proof of a disguised authorization check (server logs are the only way to know for certain — see Finding 1 write-up above), but it now leans further toward Finding 1 being a real, if generically-worded, authorization rejection rather than a client-side integration bug. **Recommend closing this out via server-log correlation with HopOn rather than further trial-and-error against production** — three minimal-value (8 NIS) attempts against the real test card is enough; a fourth blind guess isn't a good use of the remaining test card. Timestamp for HopOn's log lookup: ~11:10 GMT 2026-08-14, test account `972528936723`, card serial `2046671755`, contract_id `689242`.

---

## Finding 1 — full client-side bypass via static patch + complete UI walkthrough (2026-08-14, ~11:50–12:15 GMT)

### Goal
Prove Finding 1 by getting *the app's own real UI flow* — not a hand-built Frida API replay — to reach the actual cash-purchase submission, by defeating the client-side kiosk gate entirely.

### Attempt 1: Frida runtime hooking — abandoned after 7 variants, root cause never found
Tried, in order: (1) hooking `Kiosk.getIsActive()/getBudget()` directly, (2) hooking `co.hopon.sdk.repo.y.h0()` (the kiosk cache getter), (3) patching the fragment's cached field `h` via `.implementation` wrapping, (4) same with explicit `Java.enumerateClassLoaders()` resolution (only one loader could even load the class — ruled out as the cause), (5) forcing `pm compile -m interpret-only` to defeat possible AOT inlining, (6)/(7) direct active invocation (`Java.choose` + call the method ourselves) instead of passive `.implementation` hooking, with and without `Java.scheduleOnMainThread`. Every variant either silently never fired or hung with no exception. Confirmed via live reflection that the class/method genuinely exist as expected (`getDeclaredMethods()` dump matched the decompile exactly) — this was not a wrong-code-path problem, `.implementation`-style hooking itself just would not take effect against this process/Frida/ART combination for reasons never conclusively identified. **Lesson: don't sink more time into `.implementation` hooking on this target if it happens again — go straight to a static smali patch.**

### Attempt 2: static patch, `Kiosk.smali` only — insufficient
Patched `getIsActive()`→hardcoded `true`, `getBudget()`→hardcoded `100000.00`, `getPinCode()`→`"0000"` directly in `co/hopon/sdk/network/v1/models/Kiosk.smali` (unconditional, no field dependency at all — confirmed via live `Java.use(...).getIsActive()` direct call on the running process that this really does always return `true`). Rebuilt/signed/installed as an **update** over the Finding-3 PoC build (same `poc_debug.keystore` → in-place update, session/login preserved, no re-auth needed). **Result: dialog still appeared.** This is the single most useful negative result of the session — it proves the failure had nothing to do with what `Kiosk.getIsActive()` returns.

### Root cause found by reading `ContractDetailsPOSFragment` (`l.smali`) fully
`l.c()` (the Cash button's `onClick` target) is *not* the only place that checks kiosk status. There is a **second, independent copy of the exact same check** inside the LiveData observer callback `l.a(LiveData, Kiosk)` (fires once when the repo's kiosk LiveData first emits a value). Its logic:
```
if (fetchedKiosk == null) → show "no kiosk" dialog directly, never call getIsActive() at all
else if (!fetchedKiosk.getIsActive()) → show "no kiosk" dialog
else → cache it in field h, re-enter c()
```
Confirmed live via Frida reflection (`Java.choose('co.hopon.sdk.fragment.l', ...)`, read `.h.value`) that at the exact moment the dialog was showing, **`h` was `null`** — meaning the repo-level `LiveData<Kiosk>` (`x.f()`, a *different* code path from the raw `GET /kiosk` Retrofit call we'd tested directly) was emitting a bare `null`, not a Kiosk object with empty fields. Our `Kiosk.smali` method patch was therefore never even reached — you can't call a patched instance method on a null reference. Same exact pattern independently duplicated in `l0.smali` (`RefundPaymentCheckoutFragment`).

### Fix
In both `l.smali` and `l0.smali`, at the top of the `a(LiveData, Kiosk)` callback: if the incoming `Kiosk` parameter is null, replace it with `new Kiosk()` (via `new-instance` + `invoke-direct <init>`) before the existing null-check, so the method always has a non-null (and — thanks to the Attempt-2 patch — always-"active") Kiosk to work with. Rebuilt/signed/installed as another in-place update. **This time the Cash button worked immediately** — no dialog, straight to the real "Please pay cash to owner / Enter code to pay cash" kiosk-PIN screen (screenshot: `poc/finding1_ui_bypass_evidence/1_cash_button_worked_pin_screen.png`).

### PIN screen: real client-side PIN check also fully bypassed
The client-side PIN-verification call is `co.hopon.sdk.repo.y.i(String) : LiveData<Boolean>` ("`cashCodeVerify2`" per its own log tag) — it has an internal flag (`y.g`) that, unless previously set to `1` by some other flow we hadn't been through, immediately posts `false` without ever contacting the server. Patched `y.i(String)` to unconditionally build a fresh `MutableLiveData<Boolean>`, `postValue(Boolean.TRUE)`, and return it — i.e., any PIN is always "correct" client-side. Rebuilt/signed/installed once more.

**A recurring "Loading failed" dialog while entering the PIN was a red herring** — logcat showed a `NumberFormatException: For input string: "FAIL"` at that same moment, but its stack trace is 100% from `com.sec.android.sdhms.thermal.overheatcontrol.OverheatSensorChecker` — **a Samsung system thermal-monitoring service, completely unrelated to HopOn**, just noisy background coincidence on this device. Filtering logcat to the HopOn PID specifically (`adb logcat --pid=<pid>`) showed no such exception at all on the HopOn side.

### End-to-end result — the app's own code built and sent the real request
Entered PIN `1234` on the in-app numeric keypad (a custom view, not the system IME — `uiautomator dump` doesn't capture it, had to tap by pixel coordinates read off screenshots). Full logcat for the HopOn process (`4_full_logcat_real_purchase_attempt.txt`) shows the *real app code* — not a Frida-forged request — building and sending:
```
--> POST https://api.hopon.co.il/v0.15/1/isr/topupTokenPos
Authorization: HopOn oauth_signature="...",...,oauth_token="92a983179a86f649e68ae9e472332cb5",...
{"card_serial":2046671755,"cashTransaction":true,"contract_id":689356,
 "device_manufacturer":"samsung","device_model_code":"a31","device_model_name":"SM-A315F",
 "holderNumber":0,"holderProfCode":0,"numberOfPayments":0,"pin_code":"1234",
 "universityId":0,"writeContractAndProfile":false}

<-- 200 (204ms)
{"status":{"errorCode":2,"errorMsg":"Problem with request parameters"},"data":null,
 "badParams":"Unable to complete the top-up. Please contact customer service"}
```
Same generic rejection as the three earlier raw-API attempts. Note the real app also sends `numberOfPayments:0` — matching what we sent manually, confirming that was never the missing piece.

### Overall Finding 1 conclusion
Four independent attempts (3 direct-API + 1 full real-UI walkthrough with every client gate genuinely defeated — kiosk-active, budget, and PIN — via static patches, not guesswork) all produce the identical generic `errorCode: 2` rejection. The **client-side authorization model is now proven completely non-functional as a security control** — a non-kiosk account can reach a real "collect cash from customer" screen and have the app submit a real purchase request with zero legitimate kiosk credentials. Whether the *server* independently blocks the write is still not 100%-provable from outside (still no distinct auth-specific error code), but four-for-four identical generic rejections is much stronger evidence of real server-side enforcement than the original two attempts. **This should now be handed to HopOn to confirm via server logs** — timestamp `2026-08-14 12:13:18 GMT`, test account `972528936723`, card serial `2046671755`, contract_id `689356`, kiosk PIN `1234` (fabricated client-side, not a real kiosk PIN).

### Deliverables (superseded by the "reaching the write/burn screen" work below — kept for the record)
- `pentest/poc/HopOn_Finding1_KioskPatch3_PoC.apk` — build with Kiosk.smali + l.smali/l0.smali null-fix + y.smali PIN-check bypass
- `pentest/poc/finding1_ui_bypass_evidence/` — screenshots of every stage (Cash button success, PIN screen, PIN entry, final rejection) + full annotated logcat of the real request/response

**Reproduced twice more, independently, with the same result** (PIN `0000` at 12:20:20 GMT, PIN `1234`/`0000`/`2000` at 12:25:55–12:35:49 GMT) — every attempt: real app-built `topupTokenPos` request, server replies `errorCode:2`, client shows a "Loading failed"-style dialog. Confirms the finding is stable, not a fluke of one run.

---

## Pushing further: reaching the actual NFC card-write ("burn") step (2026-08-14, 12:26–12:36 GMT)

User's ask: since AUTH-2026-001 §4 rewards a PoC that's "demonstrably active" with a bonus if it works **offline**, go one step further than a client-side auth bypass — see if the client can be forced past the server's rejection entirely and made to attempt the actual value-write to the physical card, using zero real server authorization. This would be the maximum-severity version of Finding 1.

### Wrong class patched twice — `l.smali` isn't the purchase-response handler
First patched `co.hopon.sdk.fragment.l`'s `c(Lco/hopon/sdk/repo/p0;)V` (the PurchaseResponseRepo-handling callback) to force the status field `p0.a` to `0` (success) — no effect, dialog unchanged. Then discovered (by grepping for `Lco/hopon/sdk/repo/p0;` usage across the whole smali tree, not just the classes already touched) that **four different fragment classes** independently handle this same response type: `RKCardContentFragment`, `f0`, `g`, `k`. Patched `RKCardContentFragment.a(s0, e, p0)` next — including fabricating a full fake `co.hopon.sdk.network.v1.models.e` (`PreOrderContractV1`) via a new `poc_buildFake()` static factory added to that class (necessary because `e` only has a `protected Parcel`-arg constructor — no simple no-arg one — so the fake had to be built by writing matching values into a real `Parcel` in the correct field order and feeding it to that constructor; the factory had to live inside `e` itself since `protected` isn't visible cross-package). **Still no effect.**

### Root cause: fourth wrong guess, found the real one by grepping the exact Logcat tag
The visible "Loading failed" dialog is logged nowhere as such — but the *preceding* `I ContractDetailsCash: buyWithCash` logcat line gave an exact string to grep for. `grep -rl '"ContractDetailsCash"'` across the whole smali tree landed on exactly one relevant class: **`co.hopon.sdk.fragment.k`** (`ContractDetailsCashFragment.java`) — a *fifth* class, never touched before this. This is the actual screen shown after tapping Cash (title "Plan info" is reused/generic across several fragments, which is what made this so hard to pin down by screen inspection alone).

`k.a(Lco/hopon/sdk/repo/p0;)V` has the exact matching structure — `errorCode 0` → `:cond_7` → calls private method `k.c()` (the real success/write continuation); `errorCode 2` → `:cond_4` → builds a dialog with title **and** message both defaulting to string resource `rk_loading_failed` when the server's `badParams` field is absent from that particular code path — i.e. this is *precisely* the generic "Loading failed / Loading failed" dialog seen on screen, confirmed byte-for-byte.

### Fix applied
In `k.a(Lco/hopon/sdk/repo/p0;)V`, forced `p1.a = 0` (`const/4 v1, 0x0` + `iput`) immediately before the existing `iget v0, p1, ...->a:I` status read — same one-line technique as the first (wrong-class) attempt, just in the right place this time. Rebuilt/signed/installed as `pentest/poc/HopOn_Finding1_KioskPatch6_PoC.apk`. **Not yet re-tested against the live device as of this note** — next step is to repeat the Cash → PIN → submit flow and observe whether it now reaches `k.c()`'s write/NFC step, and if so whether that step itself independently fails (it very plausibly still will — see caveat below).


Even if this patch gets the UI to *attempt* the card write, the actual value-load onto the physical Rav-Kav chip (Calypso/EMV-family secure element) almost certainly requires a `topup_token` cryptographically issued and signed by HopOn's backend — something no client-side patch can forge, because the **card itself**, not the app, verifies that signature during the write. Reaching this screen would prove the client-side trust model is completely broken end-to-end (strongest possible version of Finding 1's UI/authorization findings), but likely *not* prove that a physical card can actually be loaded with fraudulent value — that boundary is expected to hold at the hardware/crypto layer regardless of app-side tampering. Documenting this expectation up front so the eventual result (success, crash, or a new distinct hardware-layer error) can be interpreted correctly either way.

### Lesson learned (for any future session on this app)
This codebase reuses generic UI (dialog titles like "Plan info", shared response types like `PurchaseResponseRepo`/`p0`) across **many** structurally-similar-but-distinct fragment classes. Static reading of one plausible-looking class is not reliable here — confirm the exact handling class via a unique Logcat string (a log tag, a distinctive log message) grepped across the *entire* smali tree before patching, rather than assuming structural similarity implies it's the same code path. This cost three wrong-class patch/rebuild/install cycles in a row before finding the real one.

### ✅ FINAL RESULT — reached HopOn's real, live SAM backend, correctly rejected (2026-08-14, 13:03 GMT)

With `KioskPatch6` installed (`k.a(p0)` forces purchase status to `0`), one more fabrication was layered on top: `CalypsoApp.getInstance()` (`com/tuscans/calypso/hopon/CalypsoApp.smali`) was patched so that if its `a` field (the `wsResponse`, type `com.tuscans.calypso.hopon.e$a`) is null, it constructs one with `success=true` and a **syntactically well-formed placeholder XML** string for the message field:
```
<ROOT><ROW ChannelID="0" FDate="2026-08-14" inApduLength="12" inApduPtr="00A4040007A0000002471001"/></ROOT>
```
This shape was read directly out of the app's own bundled `com.tuscans.calypso.d` class (a generic `<ROW ChannelID=".." FDate=".." inApduLength=".." inApduPtr="..">` XML-to-object parser already present in the APK we're authorized to test) — no external reverse-engineering of Calypso's protocol semantics was done; the placeholder attribute *values* are arbitrary dummy data (a generic `SELECT` APDU byte pattern), not derived from any real cryptographic material. Built as `pentest/poc/HopOn_Finding1_KioskPatch8_PoC.apk`.

**Result — this is qualitatively different from every prior attempt:**
```
RavKavWriter: send receive to card 00A4040007A0000002471001      <- our placeholder APDU, sent to the REAL physical test card
CalypsoSendRecv: sending 00A4040007A0000002471001
RavKavCard: sending RunTransaction to WS                          <- app made a REAL network call to HopOn's live SAM (Secure Access
SAM ws: androidHttpTransport                                         Module) backend web service — not simulated, a real SOAP round-trip
WritingNestedFragment: progress: 5% ... 20%                       <- the write-progress UI the user saw climb to ~20%, driven by this real call
SAM ws: SoapPrimitive response                                    <- a REAL response came back from HopOn's live SAM infrastructure
RavKavCard: send RunTransaction success                           <- SOAP transport layer succeeded
RavKavCard: web service sent channel id -1, we're done            <- the SAM's OWN response body correctly flagged the session invalid
RavKavWriter: webServiceResult:-3
HONativeNFCWrapper: doCardWrite fail resultCode: 0
CardWriteContainer: showResult:code: 0
```


**Why this is the real stopping point, not just a discouraging error:** everything up to and including "trigger a real SAM call" is squarely inside AUTH-2026-001's scope (testing HopOn's own app + its calls to HopOn's own backend). Going further would mean constructing a session that the SAM's real cryptographic validation accepts — which requires HopOn's actual issuer keys and is by design not derivable from the client. That's not a gap in this testing; Calypso's SAM architecture is specifically built so client-side compromise (which we achieved completely) cannot translate into a valid card write without the SAM's own key material. Confirmed here empirically against the real backend, not just argued theoretically.

**⚠️ Production impact note for the report:** this attempt caused one real HTTP/SOAP round-trip to HopOn's live SAM web service (immediately and correctly rejected, no state changed, no value written to any card). Flag the timestamp (`2026-08-14 13:03:12 GMT`, test account `972528936723`, card serial `2046671755`) for HopOn to correlate in their own SAM/backend logs alongside the `topupTokenPos` timestamps already noted above.

### Final deliverable
- `pentest/poc/HopOn_Finding1_KioskPatch8_PoC.apk` — complete chain: Finding 3 offline decryption PoC + full Finding 1 client bypass (kiosk gate, budget, PIN, purchase-success forcing in the *correct* class `k.smali`, placeholder SAM webServiceResponse) — reaches a real SAM backend call and is cleanly rejected, no crash, full logcat trail available for HopOn's own log correlation.

---

## Follow-up static analysis: mechanics of the SAM/Calypso write protocol (2026-08-14, ~14:xx GMT) — no live/device action taken

**Why "generate a convincing fake reply" can't actually work (explained to tester, confirmed understood):** the wire *format* of a SAM response was never the unknown — it's fully known from the app's own code (`<ROOT><ROW ChannelID=".." FDate=".." inApduLength=".." inApduPtr=".."/></ROOT>`, exactly as used in the KioskPatch8 placeholder above). What can't be fabricated is the *cryptographic MAC* inside a genuine card-mutating `inApduPtr` value (e.g. a real Calypso `Increase`/`Open Secure Session` command) — this MAC is computed from per-card-diversified issuer keys that exist only inside HopOn's real SAM hardware (confirmed against the publicly-published Innovatron *Calypso Functional Specification*, which documents the secure-session command set but not, by design, any key material). This is a cryptographic impossibility, not a missing piece of research — no amount of searching or retrying across multiple physical cards changes the outcome, the same way trying more lottery tickets doesn't help you predict one you didn't buy.

## Read of a HopOn-charged test card (2026-08-14, ~14:47 GMT) — passive NFC read only, zero risk, zero network calls

To get real reference data (and as a safer alternative direction to pushing further on the write boundary), read a second test card that HopOn had already legitimately loaded with a real contract (via their own normal process, not via any exploit). This is a pure passive NFC read — the same operation the app performs to display your balance — no write attempted, no risk to the card. Captured via `adb logcat` while tapping the card on the app's normal "read card" screen; raw capture saved to `pentest/poc/evidence/charged_card_read_2026-08-14.txt`.

Full contract fields parsed and logged in-app by `RKParser` (`co.hopon.sdk.hravkav.RKParser`, unmodified stock code — `RavKavContract.toString()` at `com/tuscans/calypso/rav_kav_objects/RavKavContract.java:112`):
```
EnvIssuingDate=7.11.2019  EnvEndDate=7.11.2027  EnvCountryId=886  EnvIssuerId=3  EnvApplicationNo=123
ContractValidityStartDate=14.8.2026   ContractSaleDate=14.8.2026   ContractSaleDevice=4050
ContractSaleNumberDaily=528   ContractValidityEndDate=9.11.2041
ContractProvider=3   ContractTariffUsage=1   ContractTariffCounter=3   ContractTariff=6
Contract_Ticket_Type=3   Predefined=200   EttCode=66   SpatialContractId=[12488,0,0,...]
```
This is genuine, legitimately-issued reference data (real `ContractSaleDevice`/kiosk ID `4050`, real `Predefined` catalog code `200`, real field-value ranges) — useful for the report as ground truth for what a legitimate write produces, and confirms (again) that reading is unauthenticated/unprotected while writing is the only gated operation, consistent with the rest of this investigation. As expected/explained beforehand, this does **not** provide anything reusable to forge a write to a different card (Calypso key diversification is per-card-serial; a legitimate session captured this way is not replayable elsewhere).

---

## KioskPatch9 — direct write-family APDU attempt against the physical card (2026-08-14, ~15:04-15:08 GMT)

**Explicit go-ahead:** tester confirmed proceeding without waiting for HopOn's reply, and confirmed acceptance of physical-card risk (multiple spare cards provided by HopOn for this purpose; cards are test-only, not usable on any real bus regardless of outcome).

### What was built
Rebuilt from the existing `HopOn_Finding1_KioskPatch8_PoC.apk` (apktool-decompiled directly, preserving every prior patch — kiosk gate, PIN bypass, purchase-success force, CalypsoApp fabrication — rather than reapplying from scratch). Single change: extended the fabricated `CalypsoApp` wsResponse XML (`getInstance()` in `CalypsoApp.smali`) from one `<ROW>` to two:
- Row 1 (`ChannelID=0`): `94A4040008315449432E494341` — the **real** Calypso SELECT-by-AID command (`SELECT "1TIC.ICA"`) observed directly from this engagement's own card reads, used in place of KioskPatch8's generic placeholder AID.
- Row 2 (`ChannelID=1`): `94DC014C1D` + 29 zero bytes — a generically-shaped **UPDATE RECORD** command (standard ISO 7816-4 `INS=0xDC`), targeting SFI 9 / record 1 (the Contracts file — same P1/P2 addressing scheme `(SFI<<3)|4` confirmed by direct comparison against this engagement's own observed Read Record commands, e.g. `94B2014C1D` for the same SFI). No real key/MAC material — publicly-documented command *shape* only, exactly as scoped and agreed with the tester beforehand.

Signed with the same `poc_debug.keystore`/alias as all prior PoC builds (in-place update, no re-login needed). Built as `pentest/poc/HopOn_Finding1_KioskPatch9_PoC.apk`.

### Result — live test against the blank test card
First attempt: `TagLostException` on the very first SELECT (card moved during the read) — a physical/NFC contact issue, unrelated to the patch; retried.

Second attempt — full trace:
1. `94A4040008315449432E494341` (SELECT) → card responds normally, ends `9000` (success), identical to every prior successful select on this card.
2. `94DC014C1D0000...0000` (our UPDATE RECORD attempt) → sent to the card. Low-level NFC transport logs (`NativeNfcTag.cpp`) show **`Retry reSelect() for resolving doTransceive timeout`** and several `NFA_DM_RF_DEACTIVATE_NTF`/reselect cycles before the transceive finally completed (`Transceive End, Result: 0`) — a materially different, more disrupted exchange than the ~10ms clean round-trips seen for every Read Record call in this entire engagement. App-level log for this specific response is empty (`CalypsoSendRecv:` with no data), consistent with the card returning nothing beyond a short status word.
3. App treated this as "no more instructions this round" and proceeded exactly as KioskPatch8 did: real `RunTransaction` SOAP call to HopOn's live SAM, real response, `ChannelID=-1`, non-9000 result, `webServiceResult:-3`, `doCardWrite fail resultCode: 0` / `showResult:code: 0` — clean failure, no crash.
4. **Card integrity verified afterward**: tester re-read the same blank test card via the app's normal read flow post-attempt (captured via `adb logcat`). Result: **byte-for-byte unchanged**.
   - `RavKavEnvironment` fields identical to this card's original baseline from earlier in the engagement (`EnvIssuingDate=16.5.2023, EnvEndDate=31.5.2031, EnvIssuerId=18, EnvApplicationNo=0`).
   - Contract record 1 (the exact SFI/record our fabricated `94DC014C1D` command targeted) read back as **all-zero** — identical to its pre-attempt state, i.e. our write attempt changed **zero bytes** on the card, not just "was rejected" but independently confirmed to have had no effect.
   - Full select→env→counters→contracts→events→special-events read sequence completed cleanly with `9000` success on every step, no `TagLostException`, no anomaly — card fully healthy.

### Answering the tester's question directly: does payload content matter?
No. Re-confirmed explicitly: this rejection point is a **session/authorization check that happens before the card ever inspects command content** (standard ISO 7816-4 / Calypso secure-session model — this is precisely *why* Calypso's SAM architecture exists). Sending the exact real field values read from the charged card (`Predefined=200`, `ContractSaleDevice=4050`, etc.) instead of zero-bytes would produce the **identical** rejection, since the missing prerequisite (a card-authenticated secure session opened via real issuer-key challenge/response) is never reached in either case. Not re-tested with realistic payload bytes since the outcome is not in question — doing so would just spend another card attempt for zero new information.

### Updated conclusion
This is the strongest and most complete version of Finding 1's technical evidence obtained in this engagement: a fully client-bypassed app (kiosk gate, PIN, purchase-success all defeated) was made to send a genuine, unauthorized **write-family** command (not just a harmless SELECT) directly to a real physical Calypso card, and independently to HopOn's real backend SAM. **Both layers rejected it, independently, cleanly, with no crash and no card damage.** This directly demonstrates defense-in-depth working as designed: client-side compromise (total, in this case) does not translate into an actual unauthorized card write, because neither the card's own onboard access control nor the backend SAM's session model can be satisfied without HopOn's real issuer key material — which by design never touches the client.

---

## KioskPatch10 — same UPDATE RECORD command, real captured contract bytes as payload (2026-08-14, ~15:20-15:21 GMT)

Tester's question: does the exact byte content of the write payload matter? Rebuilt `CalypsoApp.smali`'s fabricated Row 2 with the **exact raw bytes read from the legitimately-charged card's real contract record** (`0ADF01AC6A907F4A1091030270C8F000000000000000000000000000BF`, captured above) in place of the all-zero placeholder, keeping the identical command header (`94DC014C1D`, same SFI/record target). Built/signed/installed identically to KioskPatch9 (`pentest/poc/HopOn_Finding1_KioskPatch10_PoC.apk`).

Definitive confirmation, in the card's own standard-compliant protocol response, that payload content is irrelevant to the outcome — `6982` "Security status not satisfied" is returned regardless of whether the write attempt carries zero-bytes or an exact copy of legitimately-issued contract data, because the check that fails happens before content is ever considered. This closes out the physical-card angle of Finding 1 with the cleanest possible evidence: a named, standard status word rather than an ambiguous timeout.

---

## Data-format documentation: decoding the "Contracts" file bit layout (2026-08-14, ~15:40 GMT) — static analysis + offline decode script, no device use

Purpose: fully document the on-card contract record encoding (data *format*, not the write-authorization *key*) for the report, at the tester's request, to understand card structure in detail. This is standard reverse engineering of a data schema the app itself must contain in plaintext to display balances to users — unrelated to and not a substitute for the cryptographic MAC/session boundary documented above (which remains provably unbroken).

**Source class:** `com.tuscans.calypso.a.a` (Contract record decoder) + its base class `com.tuscans.calypso.a.e` (generic MSB-first bit-field reader over the 29-byte record + date decoding relative to epoch **1997-01-01 00:00:00 UTC** — both transcribed 1:1 from jadx output, not guessed). This is the **Intercode** transit-ticketing bit-packing standard (the same family of encoding used across multiple Calypso-based European/Israeli transit systems), not something proprietary or secret to HopOn — the app must contain this decode logic in the clear to show any user their own contract on screen.

**Verification method:** re-implemented the exact bit-extraction algorithm in Python (`scratchpad/decode_contract.py`) and ran it against both of this engagement's own captured raw contract records. Output matched the app's own live `RKParser` log **field-for-field, byte-perfect**, confirming the transcription is fully correct (not an approximation):

| Field | Charged card (real contract) | Blank test card |
|---|---|---|
| `ContractVersion_Number` | 0 | 0 |
| `ContractProvider` | 3 | 0 |
| `ContractTariffUsage` | 1 | 0 |
| `ContractTariffCounter` | 3 | 0 |
| `ContractTariff` | 6 | 0 |
| `ContractSaleDevice` | **4050** | 0 |
| `ContractSaleNumberDaily` | 528 | 0 |
| `ContractValidityStartDate` | 2026-08-14 (raw bit-field 5566, complemented) | — |
| `ContractSaleDate` | 2026-08-14 (raw bit-field 10817, uncomplemented) | — |
| optional-field bitmap | `0b1000100` | `0b0` (nothing set — genuinely empty record) |

**Key structural facts confirmed:**
- Fixed-position header fields (version, dates, provider, tariff, sale device/daily-number, journey-interchanges) occupy bits 0–81 in a strict, fully-decoded, non-ambiguous layout.
- From bit 82 onward, an **optional-field bitmap** (`iA`, 9 bits) gates a series of conditionally-present fields (restriction time/code/duration, validity end date, validity duration, period journeys, customer profile, passengers number, user info) — each field's presence and bit-length depends on the bitmap, exactly like a TLV/flags-based extension scheme.
- Beyond that, a repeating tagged-block region (bits ~124–232) encodes zone/line/ride/run/seat/parking-data extensions via a 4-bit type tag per block (`case 0`..`11` in the decompiled switch) — this is where `Predefined` (catalog contract ID) and `EttCode` ultimately get derived from (`SpatialContractId[0] & 2047` and related bit-shifts), not stored as their own dedicated field.
- Dates are `1997-01-01 00:00:00 UTC + N` (N in days for the *StartDate*/*EndDate* pair after a `(~raw) & 0x3FFF` bitwise complement; N in days directly, no complement, for `ContractSaleDate`) — an Intercode-standard convention, not HopOn-specific.
- **No cryptographic material appears anywhere in this decode.** Every field here is descriptive business data (what/when/which-device), consistent with everything already established: the file *content* format is effectively public (the app ships the decoder in the clear), while the *authorization to write* it is the only protected part, enforced by the separate SAM/secure-session mechanism that this data format has no bearing on.

**Deliverable:** `scratchpad/decode_contract.py` (portable, dependency-free reference decoder for this record type, verified against two independent real-world samples).

---

## Standalone Rav-Kav card-reader Android app (2026-08-14, continuing session) — tester's ask: "build the app to give the data based on the card structure"

Purpose: at the tester's request, go beyond the one-off Python decoder and build a real, standalone Android app (separate package, not a patch of HopOn's app) that uses the test phone's own NFC radio to read a Rav-Kav card directly and display its decoded contents — a working demonstration of the data-format documentation above, not a security exploit (read-only, no write attempted, no HopOn code involved).

### Step 1 — full decoder transcription + cross-check against ground truth before writing any Android code
Located a prior session's full jadx decompile (`.../scratchpad(65083ca1-...)/jadx_out/sources/com/tuscans/calypso/`) containing the actual decoder classes beyond just the Contract one already documented above:
- `a/c.java` — Environment file decoder (bit layout for `EnvCountryId`, `EnvIssuerId`, `EnvApplicationNo`, `EnvIssuingDate`, `EnvEndDate`, holder fields).
- `a/b.java` — Counters file decoder (9 × 3-byte big-endian integers).
- `a/a.java` — the full Contract decoder including the TLV extension-block walk (tags 0–11) that the earlier `decode_contract.py` pass had deliberately left unimplemented — this is where `Predefined`/`EttCode` actually get computed (`tag 9` block: `SpatialContractId[0] & 2047` → Predefined; `(SpatialContractId[0] >> 11) + Tariff*10` → EttCode).

Transcribed all three 1:1 into a new Python harness (`scratchpad/decode_full_verify.py`) and ran it against this engagement's own real captured bytes (the charged test card's Environment/Counters/Contract records, and the exact `CalypsoSendRecv` APDU log in `pentest/poc/evidence/charged_card_read_2026-08-14.txt`) — compared field-by-field against the app's own live `RKParser` logcat output (ground truth, not a guess).

**Result:** Environment and Counters decoded **byte-perfect**, every field. Contract decoded correctly for 10 of 12 headline fields, including the two that matter most (`Predefined=200`, `EttCode=66`, matching real output exactly — confirms the TLV walk and tag-9 derivation is correct). **Two fields did not match direct bitfield decode:** `ContractValidityEndDate` and `Contract_Ticket_Type`. Root-caused by re-reading `a.java`'s post-loop logic: for this record, the 9-bit optional-field bitmap (`iA=68`) does **not** include the bit for a directly-encoded end date or a tariff-counter value that resolves to ticket-type 3 through the raw switch — meaning the app must be filling these two fields from a **local, offline product/catalog table** (e.g. "Predefined #200 = an annual pass, valid until issuing-date + N years"), not from bits physically stored on the card. This is a real, useful finding for the report/documentation, not a bug in the transcription — confirmed by the fact every *other* field (including the two hardest-to-derive ones, Predefined/EttCode) matched exactly.

**Decision:** the Android app will display `ContractValidityEndDate`/`Contract_Ticket_Type` **only when actually bit-encoded on the card** (with a clear "not encoded on card / resolved via HopOn's product catalog" label otherwise) rather than silently showing a wrong computed value. Everything else (Environment, Counters, and the rest of the Contract fields including Predefined/EttCode) is fully verified against real card data and safe to trust in the app's output.

### Step 2 — exact APDU sequence for the app (taken verbatim from this engagement's own capture, not guessed)
From `charged_card_read_2026-08-14.txt`, `CLA=0x94` (Calypso-proprietary class) throughout:
- `SELECT`: `94A4040008315449432E494341` (AID = ASCII `"1TIC.ICA"`)
- `READ RECORD` (`INS=B2`), `P2 = (SFI<<3)|4`:
  - Environment — SFI 7 (`P2=0x3C`), 1 record: `94B2013C1D`
  - Counters — SFI 25/0x19 (`P2=0xCC`), 1 record: `94B201CC1D`
  - Contracts — SFI 9 (`P2=0x4C`), 8 records: `94B201..08 4C1D`
  - Events — SFI 8 (`P2=0x44`), 6 records: `94B201..06 441D`
  - Special Events — SFI 29/0x1D (`P2=0xEC`), 4 records: `94B201..04 EC1D`
- `Le=0x1D` (29) on every read — matches the fixed 29-byte record size used throughout the decoders.

### Step 3 — building the Android app
Platform decision (tester confirmed): native Android app using the Galaxy A31's own NFC radio (`NfcAdapter.enableReaderMode` + `IsoDep`), no external USB reader available/needed. New standalone package `com.unicapitalgroup.ravkavreader` (not a HopOn patch, no HopOn code/branding, separate signing identity from every HopOn PoC build).

**Toolchain (no Gradle/Android Studio project used, same command-line philosophy as the rest of this engagement):** `aapt2 link` (manifest+resources -> base APK) -> `javac` (against `platforms/android-34/android.jar`) -> `d8` (dex) -> merge dex into the base APK via `jar uf` -> `zipalign` -> `apksigner`, with a dedicated new keystore (`pentest/reader_app/readerapp.keystore`, alias `ravkavreader`) separate from the HopOn-PoC keystore. Deliverable: `pentest/reader_app/RavKavCardReader_AUTH-2026-001.apk` + full Java source + `decode_full_verify.py`.

**Live test 1 — blank test card:** installed + launched on the Galaxy A31, tapped a blank spare test card. Worked on the first attempt: Environment decoded correctly, Counters all-zero, "(all 8 contract slots are empty)" correctly shown.

**Live test 2 — the real charged reference card, bug found and fixed:** tapped the legitimately-charged reference card (10 NIS balance, per tester's direct confirmation). Result matched ground truth on every field **except `ContractSaleDate`**, which showed `29.3.2012` instead of the correct `14.8.2026`. Root cause: a transcription slip made only in the Java port (not present in the verified Python harness) — `ContractSaleDate` was accidentally passed through the same `(~x)&0x3FFF` complement step that only applies to `ContractValidityStartDate`/`ContractValidityEndDate`. Fixed (`dateStr(saleDateRaw, false)`), rebuilt, redeployed. Re-tested: **all fields now match exactly**, including the corrected `ContractSaleDate=14.8.2026`.

**Balance-unit finding:** tester independently confirmed the reference card's real balance is **10 NIS**. `counter[0]` decoded as `1000` — confirms `counter[0]` is the stored-cash-balance counter in **agorot** (1/100 NIS), i.e. `counter[0]/100` = balance in NIS. Added this as a formatted `(balance = X.XX NIS)` line in the app's Counters output (verified: 1000 -> "10.00 NIS", correct).

Per tester's request, the app also dumps its full output to Logcat (tag `RavKavReaderPoC`), not just the on-screen scrollview, so long output isn't limited by what fits on one screenshot.

### Reused for a second, independent confirmation of Finding 1's write boundary (tester's request, clarified scope)
Tester initially asked to make "our app" perform the same card-write test with different data. Clarified via direct question that this meant: re-run the **existing, already-authorized** `HopOn_Finding1_KioskPatch10_PoC.apk` (already installed on the device, last-updated 15:20 GMT matching the KioskPatch10 build) — not building new write capability into the standalone reader tool — against a **new, previously-unused blank test card** (tester explicitly chose the fresh-blank-card option over reusing the 10 NIS reference card, to keep the reference card intact for comparison).

**Result (2026-08-14, ~16:43 GMT):** identical outcome to every prior KioskPatch9/10 attempt —
```
sending 94DC014C1D0ADF01AC6A907F4A1091030270C8F000000000000000000000000000BF
Received: 6982                              <- card itself rejects instantly, before SAM is even contacted
RavKavCard: sending RunTransaction to WS
[~20s pause] SocketTimeoutException: timeout  <- SAM call itself timed out this run (network-level; every
doCardWrite fail resultCode: -3                 prior run got a real fast/slow SAM response, just also rejecting)
```
The **security-relevant result is unchanged and reconfirmed on a brand-new card**: the physical card's own secure element rejects the write at the protocol level (`6982`) regardless of which specific card, independent of the SAM round-trip's outcome. The 20-second SAM timeout on this specific attempt is a network-layer detail, not a new security finding.

**Card-integrity re-verification (this engagement's new standalone reader app, used specifically for this purpose):** re-read the same blank card immediately after the write attempt. Environment identical, all 9 counters still zero (`balance = 0.00 NIS`), all 8 contract slots still empty, Events file unchanged. **Zero bytes changed** — consistent with every prior write-attempt integrity check in this engagement.

---

## Legitimate reference transaction: "Rav-Kav Online" app (com.pcentra.ravkavonlinemobile) — full purchase + real card write, fully instrumented (2026-08-14, ~17:50-17:57 GMT)

**Purpose (tester-directed):** the authorizing company separately provided access to purchase a real contract via a **different, official app** — "Rav-Kav Online" (`com.pcentra.ravkavonlinemobile` v1.17.1, vendor Pcentra) — using a company-issued test Visa card, specifically so this engagement could observe and document **how a legitimate contract write and its cryptographic protection actually work end-to-end**, as a reference/ground-truth counterpart to the (rejected) unauthorized-write attempts against the HopOn app documented above. This is a distinct app/vendor from `co.hopon.android.rkpos` but operates against the same underlying Rav-Kav/Calypso card ecosystem, using the official `ravkav-sdk-android` SDK (version string captured live, see below) — i.e. this is very likely the same SDK family HopOn's app white-labels (`com.tuscans.calypso.*`), now observed from its primary vendor's own app during a fully legitimate transaction.

### Instrumentation set up beforehand
- **Network:** `adb reverse tcp:8080 tcp:8080` + device `settings put global http_proxy localhost:8080`, `mitmdump -w pentest/captures/ravkav_online_capture1.flow` (a stale mitmdump from the earlier HopOn session, still bound to port 8080 since 06:23 that morning, had to be killed first).
- **TLS unpinning:** `pentest/scripts/unpinning.js` attached via Frida to the already-running app process (`frida -U -p <pid> -l ...`). Hit the same Hebrew-path parser bug as prior sessions (`could not parse '...טעינה בקיוסקים...'`) — **but this time the real root cause (re-confirmed) was that the script uses `import ... from 'frida-java-bridge'`, which the bare frida CLI's `-l` cannot resolve at all, regardless of path**; fixed by installing `frida-java-bridge` + `frida-compile` locally (`npm install` in scratchpad, no internet-facing risk) and pre-bundling with `frida-compile unpinning.js -o unpinning_bundle.js`, then loading the bundle. Worth updating `unpinning.js`'s own header comment or a README with this exact fix, since it will otherwise be rediscovered from scratch every session.
- **NFC APDU tracer (new, purpose-built for this phase):** a second Frida script, `nfc_trace.js` (plain script, no ES imports, loads directly), hooking `android.nfc.tech.IsoDep.connect()/close()/transceive(byte[])` and `NfcA.transceive(byte[])` at the **Android OS API level** — logs every raw APDU command/response in hex with a timestamp, independent of anything the app itself chooses to log. This was specifically to answer the tester's ask to "trace the NFC path, whatever flows through it" without depending on app-specific log tags (which, per the KioskPatch1-10 work above, proved unreliable/misleading across differently-named fragment classes). Loaded together with the compiled unpinning bundle in one `frida -U -p <pid> -l unpinning_bundle.js -l nfc_trace.js` invocation.
- **Logcat:** full unfiltered `adb logcat -v time` to `pentest/captures/ravkav_online_logcat1.txt`, running for the whole session.
- Confirmed device already rooted (Magisk) and `frida-server` already running as root from earlier in the day; only needed to attach, not redeploy.

### Transaction performed (tester, through the app's real UI, real test Visa)
Full HTTPS flow (decrypted via the unpinning above), captured in `ravkav_online_capture1.flow`:
1. `POST https://ravkavonline.co.il/api/identify/records/` — app uploads the **current** card record dump (base64 of each file, all-zero/empty at this point) to identify the card server-side. First attempt got `403 {"detail":"expired_token"}`; app transparently refreshed via `POST /api/o/refresh/` and retried successfully.
2. `GET https://ravkavonline.co.il/api/ravkav/contract/5856/` — fetches the catalog entry for `predefined_contract.id=200` ("Stored value" — the same catalog product `Predefined=200` already established from earlier passive card reads in this engagement), `ett=66` (matches `EttCode` field decoded from cards throughout this engagement), `price=1000` (1000 agorot = 10.00 NIS).
3. `POST https://ravkavonline.co.il/api/transaction/transaction/` — opens a transaction (`contract:19688`, `price:1000`, `counter_value:1000`) → server returns a `uid` and a short validity window (`active_until`, 15 minutes).
4. `POST .../transaction/<uid>/payment/` → hands off to payment processor **buya.co.il** (`{"type":"buya","session_id":...}`).
5. `POST https://api.buya.co.il/api/merchant/session/<id>/update/` — **the app sends the raw card details directly in a JSON body**: `{"pan":"4743621112038390","exp_mm":7,"exp_yy":29,"cvv":"052","personal_id":"237667852"}` (test Visa PAN, tester-provided test national ID). Noting for the record since it's a real card-data transmission pattern worth being aware of (not evaluated further here — out of scope for this specific NFC/write investigation, and the PAN used is a company-issued test card, not a real cardholder's).
6. `POST .../buya/payment-process/<id>/finalize-payment/` → `{"status":"success"}`.
7. `POST .../transaction/<uid>/reloading/` — **this is the step that actually kicks off the card write.** Response: `{"reloading_url":"wss://ravkavonline3.rmtkts.co/api/transaction/<uuid>", "counter_value":1000, ...}` — a **WebSocket URL**, not a SOAP endpoint like HopOn's `RemoteSam.asmx`. `ravkavonline3.rmtkts.co` is Pcentra's own real-time card-write backend (presumably the actual SAM-fronting service; "rmtkts" likely short for "remote tickets"). This is architecturally distinct from HopOn's polling-style `OpenTransactionHopon`/`RunTransaction` SOAP pair, but — as shown below — functionally identical in substance: a remote, key-holding backend drives the card step by step, and the client is a dumb relay.

### The WebSocket protocol — captured in full, and independently cross-checked byte-for-byte against the OS-level NFC trace

mitmproxy captured the full WebSocket session as a `WebSocketFlow` (readable via `flow.websocket.messages` in `mitmproxy.io.FlowReader` — not printed by the existing `dump_flows.py`, which only handles plain HTTP; used a small ad-hoc script instead, `scratchpad/dump_ws.py`, kept out of `pentest/` since it's a one-off).

**Message shape:** `{"id": <int>, "action": <int>, "arguments": [...]}` — `action:5` is an initial device/SDK handshake (client reports `sdk_name:"ravkav-sdk-android"`, `sdk_version:"v0.1712.2-14-g7a4e016e22"`, device model, `network_type`, `unique_id` — this is the official Pcentra Rav-Kav SDK identifying itself, strong circumstantial confirmation that HopOn's `com.tuscans.calypso.*` classes are this same SDK, white-labeled). `action:1` is "execute these card commands and give me the results" — server sends an `arguments` array of **base64-encoded raw APDU command bytes**, client replies on the same `id` with an `arguments` array of **base64-encoded raw APDU response bytes**, in the same order. `action:3` is the final result (see below).

**Decoded (`base64 -> hex`) and diffed directly against the simultaneous `nfc_trace.js` capture in `frida_unpinning_stdout.log` — 100% byte-identical, every single command and response:**

| WS `id` | Server → Client (via WS, base64→hex) | Client → Card (via NFC, from IsoDep hook) | Meaning |
|---|---|---|---|
| 973375741 | `00A4040008315449432E494341` | *(same)* | SELECT AID "1TIC.ICA" |
| 973375741 | `948A8A3804FF758A3F` | *(same)* | **Open Secure Session** (CLA=94 INS=8A, P1=key ref `8A`, P2=`38`, 4-byte terminal challenge `FF758A3F`) — card responds `60030D31EF06EC24...9000`, embedding the card's session data/first-record read |
| 973375741 | 20× `94B2xx..1D` | *(same)* | READ RECORD sweep across Counters/Events/Contracts/Special-Events (reading current state inside the now-open secure session) |
| 973375742 | `94DC014C1D` + 29 bytes | *(same)* | **UPDATE RECORD**, SFI 9 (Contracts) record 1 — the actual contract write. Card: `9000` (success) |
| 973375742 | `94E200401D` + 29 bytes | *(same)* | **APPEND RECORD**, Events file — logs the transaction as an on-card event. Card: `9000` |
| 973375742 | `943201C8030003E8` | *(same)* | **INCREASE**, counter file, P2=`C8`, amount = `0x0003E8` = **1000** (agorot = 10.00 NIS, matches the transaction's `counter_value:1000`). Card: `0003E8 9000` (new counter value echoed back) |
| 973375743 | `948E000004A5D2D486` | *(same)* | **Close Secure Session**, 4-byte session MAC `A5D2D486` — this is the cryptographic proof-of-session computed **server-side by the SAM**, sent to the client purely to relay to the card. Card: `3FDB0949 9000` — the card's own ratification signature, proving *it* independently validated the session MAC using its own diversified issuer key |
| 973375744 | `0084000009` | *(same)* | trailing `GET CHALLENGE` (Le=09) — card replied `6C08` ("wrong Le, retry with 08"), connection closed before a retry (card lifted / app done) — benign, not an error in the write itself |

Then, out-of-band from the per-command messages, the server sent a final `action:3` result: `{"operations":[{"type":"load-contract","new_contract_number":1,"new_counter_value":1000}], "card_serial_number":2046671755, "card_records":{...}}` — confirming success and handing back a fresh full digest of the card's file contents (same base64-per-file shape as step 1's identify call).

**Conclusion — this independently reconfirms Finding 1's core architectural claim, now from the *legitimate* side and a *different vendor's own app*:** the base64 blobs sent over the WebSocket are **exactly, byte-for-byte, the raw ISO 7816-4/Calypso APDU stream** — no additional client-visible encryption layer wraps them, and the client (this app, the official one) performs **zero cryptographic computation** of its own. It is a pure bidirectional relay: SAM → WebSocket → NFC → card, and card → NFC → WebSocket → SAM. The *only* place any key-derived cryptographic material appears is inside the APDU payloads themselves (the Open-Session challenge/response and the Close-Session MAC/ratification pair), and those are computed by the SAM and the card's own secure element respectively — never by the phone. This matches, byte-for-byte in structure, what was inferred (but never seen with a fully successful `9000` outcome) from the unauthorized HopOn-side attempts above.

### Decoded contract — cross-validated two independent ways

Ran the already-verified `pentest/reader_app/decode_full_verify.py` decoder against the raw 29-byte record from the captured `UPDATE RECORD` (`0ADF07AC6A907F4A9C91030270C8F000000000000000000000000000BA`):

```
Predefined = 200          <- matches server's predefined_contract.id = 200 exactly
EttCode = 66               <- matches server's ett = 66 exactly
ContractProvider = 15
ContractTariffUsage = 1, ContractTariffCounter = 3, ContractTariff = 6
ContractSaleDevice = 4050
ContractSaleDate = 14.8.2026   ContractValidityStartDate = 14.8.2026
Contract_Ticket_Type = 2
```
Both the on-card bytes (decoded independently via the offline bit-layout transcription) and the server's own transaction JSON (`predefined_contract.id:200`, `ett:66`) agree exactly — strong two-source confirmation that both the decoder and the NFC capture are correct, and that the write genuinely landed the intended product.

**Not yet done (recommended next step):** read the card back with `pentest/reader_app/RavKavCardReader_AUTH-2026-001.apk` (passive NFC read, zero risk) to get a clean on-screen/logcat confirmation shot for the report, the same way the earlier "charged card" reference read was documented — this would be pure verification (we already have the write APDUs and the `9000` success codes, so functionally this is redundant, but useful as a clean screenshot-able deliverable).

### Artifacts from this phase
- `pentest/captures/ravkav_online_capture1.flow` — full decrypted HTTPS + WebSocket capture (mitmproxy flow format)
- `pentest/captures/ravkav_online_logcat1.txt` — full unfiltered device logcat for the session
- `pentest/captures/frida_unpinning_stdout.log` — Frida console output, including every `[NFC] >>`/`[NFC] <<` line from the OS-level APDU tracer
- `pentest/scripts/nfc_trace.js` — new reusable IsoDep/NfcA APDU tracer (generic, not app-specific; safe to reuse on any future NFC-touching app in this engagement)
- Card used: test card serial `2046671755` (same physical test card reused throughout this engagement)
