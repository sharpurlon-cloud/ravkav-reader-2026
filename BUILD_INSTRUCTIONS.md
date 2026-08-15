# 🔨 تعليمات بناء التطبيق

## المتطلبات

```
✓ Java 11+ (لديك: OpenJDK 25.0.2) ✅
✗ Gradle (مطلوب)
✗ Android SDK (مطلوب)
✗ Android Build Tools (مطلوب)
```

---

## 3 طرق للبناء

---

## 🔴 الطريقة 1: Gradle (الأفضل والأسهل)

### الخطوة 1: تثبيت Gradle

#### على Windows:
```powershell
# خيار A: استخدام Chocolatey
choco install gradle

# خيار B: تحميل يدوي
# 1. اذهب إلى: https://gradle.org/releases/
# 2. حمّل gradle-8.4-bin.zip
# 3. استخرج في: c:\Program Files\gradle-8.4
# 4. أضف PATH: c:\Program Files\gradle-8.4\bin
```

#### على Mac/Linux:
```bash
brew install gradle
# أو
sudo apt-get install gradle
```

### الخطوة 2: البناء
```bash
cd c:\Users\HP OMNIBOOK\Desktop\test\reader_app
gradle assemble
```

### النتيجة:
```
✅ APK سيكون في: build/outputs/apk/debug/reader_app-debug.apk
```

---

## 🟠 الطريقة 2: Android Studio (الأكثر استقراراً)

### الخطوة 1: تثبيت Android Studio
```
اذهب إلى: https://developer.android.com/studio
حمّل واثبت Android Studio
```

### الخطوة 2: فتح المشروع
```
1. فتح Android Studio
2. File → Open
3. اختر: c:\Users\HP OMNIBOOK\Desktop\test\reader_app
4. انتظر البناء التلقائي
```

### الخطوة 3: البناء
```
1. Build → Build APK(s)
2. أو: Build → Build Bundle(s)
3. انتظر حتى ينتهي
```

### النتيجة:
```
✅ APK سيكون في: build/outputs/apk/debug/
```

---

## 🟢 الطريقة 3: Command Line (بدون IDE)

### المتطلبات:
```
✓ Java 11+
✓ Gradle أو Maven
✓ Android SDK Command Line Tools
```

### الخطوات:
```bash
# 1. تحميل Android SDK
wget https://dl.google.com/android/repository/commandlinetools-win-xxxxx_latest.zip

# 2. استخراج
unzip -d c:\Android commandlinetools-win-xxxxx_latest.zip

# 3. تثبيت SDK
sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 4. البناء
cd c:\Users\HP OMNIBOOK\Desktop\test\reader_app
gradle assemble
```

---

## 📋 الخطوات الموصى بها

### الخيار الأول (الأسهل):
```
1. ثبّت Android Studio
2. افتح المشروع
3. اضغط Build → Build APK(s)
4. خذ APK من build/outputs/apk/
```

### الخيار الثاني:
```
1. ثبّت Gradle (عبر Chocolatey أو يدوياً)
2. شغّل: gradle assemble
3. خذ APK من build/outputs/apk/
```

---

## ⚙️ المشاكل الشائعة

### المشكلة 1: "gradle: command not found"
```
السبب: Gradle غير متثبت أو ليس في PATH
الحل:
  1. ثبّت Gradle
  2. أضف المسار للـ PATH
  3. أعد فتح الـ terminal
```

### المشكلة 2: "Android SDK not found"
```
السبب: Android SDK غير متثبت
الحل:
  1. ثبّت Android Studio (يثبّت SDK تلقائياً)
  أو
  2. ثبّت Android SDK يدوياً
  3. عيّن ANDROID_HOME في المتغيرات
```

### المشكلة 3: "Java version mismatch"
```
السبب: إصدار Java خاطئ
الحل:
  1. تحقق: java -version
  2. يجب أن يكون Java 11+
  3. لديك: OpenJDK 25.0.2 ✅
```

---

## 🎯 الخطوة الأولى

**اختر الطريقة الأسهل بالنسبة لك:**

### أسهل: Android Studio
1. ثبّت من: https://developer.android.com/studio
2. افتح المشروع
3. اضغط Build

### أسرع: Gradle
1. `choco install gradle`
2. `gradle assemble`
3. خذ APK من build/outputs/apk/

### بدون IDE: Command Line
1. ثبّت Gradle
2. `gradle assemble`
3. تم!

---

## 📦 بعد البناء

### التثبيت على الهاتف:
```bash
adb install -r build/outputs/apk/debug/reader_app-debug.apk
```

### إطلاق التطبيق:
```bash
adb shell am start -n "com.unicapitalgroup.ravkavreader/.EnhancedMainActivity"
```

---

## ✅ التحقق من البناء

```bash
# تحقق من وجود APK
ls -la build/outputs/apk/debug/

# اختبر APK
adb install -t build/outputs/apk/debug/reader_app-debug.apk
adb logcat | grep RavKav
```

---

## 📝 ملخص

```
الخيار الموصى به:
1. ثبّت Android Studio
2. افتح المشروع
3. Build → Build APK(s)
4. انتظر النجاح ✅

أو

1. ثبّت Gradle
2. gradle assemble
3. انتظر النجاح ✅
```

---

## 🆘 إذا واجهت مشاكل

```
1. تحقق: Java متثبت ✅
2. تحقق: Gradle متثبت ✗
3. تحقق: Android SDK متثبت ✗

إذا فشل شيء:
→ استخدم Android Studio (أسهل وأكثر استقراراً)
→ يثبّت كل المتطلبات تلقائياً
```

---

## 🚀 الخطوة التالية

**بعد بناء APK بنجاح:**

1. ثبّت على جهازك:
   ```bash
   adb install -r app.apk
   ```

2. افتح التطبيق وابدأ بـ:
   - ضع بطاقة مشحونة
   - اختر الرصيد
   - ضع بطاقة فارغة
   - اكتب! ✅

