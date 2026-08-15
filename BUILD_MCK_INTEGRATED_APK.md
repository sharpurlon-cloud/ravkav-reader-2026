# بناء التطبيق مع MCK Integration
## HopOn_KioskPatch11_MCK_Integrated Build Process

---

## 📋 المتطلبات الأساسية

### الأدوات المطلوبة:
```
✅ Java JDK 8+ (لديك: JDK 25)
✅ apktool 2.x
✅ Android SDK build-tools (zipalign, apksigner)
✅ Python 3.7+ (للـ MCK generation)
```

### الملفات المطلوبة:
```
✅ HopOn_Finding1_KioskPatch11_ReplayPoC.apk (الأساس)
✅ CalypsoApp_MCKIntegration.smali (التعديلات)
✅ mck_analysis_logcat.json (البيانات)
✅ poc_debug.keystore (المفتاح)
```

---

## 🔧 خطوات البناء

### Step 1: Decompile الـ APK

```bash
cd /c/Users/HP\ OMNIBOOK/Desktop/test

# Download apktool if not available
wget https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.11.1.jar -O apktool.jar

# Decompile
java -jar apktool.jar d poc/HopOn_Finding1_KioskPatch11_ReplayPoC.apk -o hopon_kp11_working
```

### Step 2: دمج MCK في CalypsoApp.smali

```bash
# Copy the MCK integration file
cp poc/CalypsoApp_MCKIntegration.smali hopon_kp11_working/smali/co/tuscans/calypso/hopon/CalypsoApp.smali

# أو يدويًا: أضف methods من CalypsoApp_MCKIntegration.smali
# إلى الملف الموجود
```

### Step 3: Patch RavKavWriter.smali

في الملف: `hopon_kp11_working/smali/co/hopon/sdk/writer/RavKavWriter.smali`

ابحث عن method OpenSecureSession واستبدل:

**قديم:**
```smali
const-string v0, "placeholder_apdu"
```

**جديد:**
```smali
invoke-static {}, Lco/tuscans/calypso/hopon/CalypsoApp;->getInstance()Lco/tuscans/calypso/hopon/CalypsoApp;
move-result-object v0
invoke-virtual {v0}, Lco/tuscans/calypso/hopon/CalypsoApp;->getNextChallenge()Ljava/lang/String;
move-result-object v1
invoke-virtual {v0, v1}, Lco/tuscans/calypso/hopon/CalypsoApp;->buildOpenSecureSessionAPDU(Ljava/lang/String;)[B
move-result-object v2
```

### Step 4: Build APK

```bash
java -jar apktool.jar b hopon_kp11_working -o hopon_mck_unsigned.apk
```

### Step 5: Align

```bash
# Download zipalign from Android SDK or use:
zipalign -p -f 4 hopon_mck_unsigned.apk hopon_mck_aligned.apk
```

### Step 6: Sign

```bash
# استخدم المفتاح الموجود
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore poc/poc_debug.keystore \
  -storepass hopon-poc-2026 \
  -keypass hopon-poc-2026 \
  hopon_mck_aligned.apk hoponpoc

# أو استخدم apksigner
apksigner sign \
  --ks poc/poc_debug.keystore \
  --ks-pass pass:hopon-poc-2026 \
  --key-pass pass:hopon-poc-2026 \
  --ks-key-alias hoponpoc \
  --out HopOn_KioskPatch11_MCK_Integrated.apk \
  hopon_mck_aligned.apk
```

### Step 7: Verify

```bash
apksigner verify -verbose HopOn_KioskPatch11_MCK_Integrated.apk
```

---

## 🚀 البناء السريع (Bash Script)

```bash
#!/bin/bash

cd /c/Users/HP\ OMNIBOOK/Desktop/test

# 1. Decompile
echo "[1/6] Decompiling..."
java -jar apktool.jar d poc/HopOn_Finding1_KioskPatch11_ReplayPoC.apk -o hopon_kp11_working

# 2. Patch
echo "[2/6] Patching CalypsoApp..."
cp poc/CalypsoApp_MCKIntegration.smali hopon_kp11_working/smali/co/tuscans/calypso/hopon/CalypsoApp.smali

# 3. Build
echo "[3/6] Building APK..."
java -jar apktool.jar b hopon_kp11_working -o hopon_mck_unsigned.apk

# 4. Align
echo "[4/6] Aligning..."
zipalign -p -f 4 hopon_mck_unsigned.apk hopon_mck_aligned.apk

# 5. Sign
echo "[5/6] Signing..."
apksigner sign \
  --ks poc/poc_debug.keystore \
  --ks-pass pass:hopon-poc-2026 \
  --key-pass pass:hopon-poc-2026 \
  --ks-key-alias hoponpoc \
  --out poc/HopOn_KioskPatch11_MCK_Integrated.apk \
  hopon_mck_aligned.apk

# 6. Verify
echo "[6/6] Verifying..."
apksigner verify -verbose poc/HopOn_KioskPatch11_MCK_Integrated.apk

echo "✅ Build complete! APK: poc/HopOn_KioskPatch11_MCK_Integrated.apk"
```

---

## 📋 متطلبات إضافية

### تحميل apktool (إذا لم يكن موجوداً):
```bash
curl https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.11.1.jar -o apktool.jar
```

### تحميل Android build-tools:
```bash
# إذا لم يكن موجوداً
# اذهب إلى: https://developer.android.com/tools/releases/build-tools
# أو استخدم Android Studio
```

---

## ✅ التحقق من النجاح

### بعد البناء:
```bash
# تحقق من وجود الملف
ls -lh poc/HopOn_KioskPatch11_MCK_Integrated.apk

# تحقق من التوقيع
apksigner verify -verbose poc/HopOn_KioskPatch11_MCK_Integrated.apk

# تحقق من محتوى
aapt dump badging poc/HopOn_KioskPatch11_MCK_Integrated.apk | grep package
```

### التثبيت والاختبار:
```bash
# التثبيت
adb install -r poc/HopOn_KioskPatch11_MCK_Integrated.apk

# التحقق من التثبيت
adb shell pm list packages | grep hopon

# الاختبار
adb logcat | grep -i "MCKIntegration\|6982\|9000"
```

---

## 🐛 استكشاف الأخطاء

### خطأ: "apktool not found"
```bash
java -jar apktool.jar ...  # استخدم الأمر الكامل
```

### خطأ: "aapt not found"
```bash
# apktool لا يحتاج aapt عادة، لكن للتحقق:
aapt dump badging file.apk
```

### خطأ: "build failed"
```bash
# تحقق من smali syntax
# استخدم: apktool list hopon_kp11_working
# للتحقق من البنية
```

### خطأ: "sign failed"
```bash
# تحقق من المفتاح
keytool -list -v -keystore poc/poc_debug.keystore -storepass hopon-poc-2026

# أعد إنشاء المفتاح إذا لزم الأمر
keytool -genkey -v -keystore poc/poc_debug.keystore -alias hoponpoc ...
```

---

## 📊 الملفات الناتجة

| الملف | الحجم (تقريبي) | الغرض |
|------|---------------|-------|
| hopon_mck_unsigned.apk | 12 MB | APK بدون توقيع |
| hopon_mck_aligned.apk | 12 MB | APK محاذى |
| HopOn_KioskPatch11_MCK_Integrated.apk | 12 MB | **النتيجة النهائية** ✅ |

---

## 🎯 الخطوات السريعة

```bash
# 1. توليد MCK data
cd /c/Users/HP\ OMNIBOOK/Desktop/test
python3 scripts/mck_from_logcat.py

# 2. Decompile
java -jar apktool.jar d poc/HopOn_Finding1_KioskPatch11_ReplayPoC.apk -o work

# 3. Patch
cp poc/CalypsoApp_MCKIntegration.smali work/smali/co/tuscans/calypso/hopon/CalypsoApp.smali

# 4. Build & Sign & Deploy
java -jar apktool.jar b work -o out.apk && \
zipalign -p -f 4 out.apk out_aligned.apk && \
apksigner sign --ks poc/poc_debug.keystore --ks-pass pass:hopon-poc-2026 \
  --key-pass pass:hopon-poc-2026 --ks-key-alias hoponpoc \
  --out poc/HopOn_KioskPatch11_MCK_Integrated.apk out_aligned.apk

# 5. Install
adb install -r poc/HopOn_KioskPatch11_MCK_Integrated.apk

# 6. Test
adb logcat | grep MCK
```

---

## ⏱️ الوقت المتوقع

```
Decompile:    2-3 دقائق
Patch:        1 دقيقة
Build:        1-2 دقيقة
Align:        < 1 دقيقة
Sign:         < 1 دقيقة
Verify:       < 1 دقيقة
Install:      1-2 دقيقة
─────────────────────
Total:        ~8-12 دقيقة
```

---

## ✨ النتيجة المتوقعة

```
✅ APK بنجاح
✅ موقّع رسمياً
✅ قابل للتثبيت
✅ يحتوي على MCK integration
✅ جاهز للاختبار
✅ يجب أن يعطي SW=9000 بدلاً من 6982
```

---

**الملف الناتج:** `poc/HopOn_KioskPatch11_MCK_Integrated.apk`

**Status:** Ready to build! 🚀
