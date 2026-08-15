# 📋 خطوات البناء والتشغيل - خطوة بخطوة

## ✨ ملخص سريع

```
شحن بطاقة RavKav جديدة = 
  1. اقرأ بطاقة قديمة (مشحونة)
  2. فك تشفير البيانات (3DES ECB + MCK)
  3. اختر الرصيد المطلوب
  4. اكتب على بطاقة جديدة
  5. النتيجة: بطاقة جديدة مشحونة! ✅
```

---

## 🏗️ الخطوة 1: البناء

### الطريقة الأسهل (GitHub Actions)

#### 1.1 إنشاء حساب GitHub
- اذهب إلى: https://github.com/signup
- أدخل بريدك: **christopherxx244@gmail.com**
- تأكد من البريد

#### 1.2 إنشاء Repository
- اذهب إلى: https://github.com/new
- **اسم**: `ravkav-reader-2026`
- اختر: `Add a README file` ✓
- اضغط: `Create repository`

#### 1.3 دفع الكود

**من PowerShell أو Command Prompt:**

```bash
cd c:\Users\HP OMNIBOOK\Desktop\test
git remote add origin https://github.com/YOUR_USERNAME/ravkav-reader-2026.git
git branch -M main
git push -u origin main
```

استبدل `YOUR_USERNAME` باسمك على GitHub.

#### 1.4 انتظر البناء التلقائي

- اذهب إلى: `https://github.com/YOUR_USERNAME/ravkav-reader-2026/actions`
- سترى job تحت اسم `Build APK`
- انتظر حتى يصبح أخضر ✅ (5-10 دقائق)

#### 1.5 حمّل APK

- اضغط على آخر run في Actions
- اضغط على artifact باسم `EnhancedRavKavReader`
- حمّل `release` أو `debug` APK

**يجب أن تحصل على:**
```
EnhancedRavKavReader-release.apk (أو debug)
حجم: ~12 MB
```

---

### الطريقة 2: Android Studio (محلي)

إذا كان Android Studio مثبت:

#### 2.1 افتح المشروع
```
File → Open
اختر: c:\Users\HP OMNIBOOK\Desktop\test\reader_app
```

#### 2.2 انتظر التحليل
- استعد للمشروع (قد يستغرق دقائق)
- حمّل dependencies

#### 2.3 بناء APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

#### 2.4 انتظر البناء
- APK ستظهر في:
  ```
  reader_app/build/outputs/apk/debug/app-debug.apk
  ```

---

## 📱 الخطوة 2: التثبيت على الهاتف

### 2.1 تأكد من الاتصال

```bash
adb devices
```

**يجب أن تري:**
```
List of attached devices
R39N101X6FZ      device
```

إذا لم تري شيء:
1. تأكد من تشغيل USB Debugging على الهاتف
   - الإعدادات → About Phone → Developer Options
   - تشغيل USB Debugging
2. كصل الهاتف بـ USB
3. جرّب مجدداً

### 2.2 ثبّت APK

**من PowerShell/Command Prompt:**

```bash
adb install -r "c:\path\to\EnhancedRavKavReader.apk"
```

**مثال:**
```bash
adb install -r "C:\Users\HP OMNIBOOK\Downloads\EnhancedRavKavReader-release.apk"
```

**يجب أن تري:**
```
Success
```

---

## 🚀 الخطوة 3: التشغيل والاختبار

### 3.1 شغّل التطبيق

من PowerShell:
```bash
adb shell am start -n "co.hopon.android.rkpos/co.hopon.android.rkpos2.Splash"
```

أو: افتح التطبيق من الهاتف مباشرة

### 3.2 اختبر القراءة

**الخطوات:**

```
1. ضع بطاقة مشحونة بالقرب من NFC قارئ الهاتف
2. انتظر 2-3 ثواني
3. يجب أن ترى:
   - "Card read successful"
   - الرصيد الحالي
   - MCK المستخرج
```

### 3.3 اختبر الكتابة

**الخطوات:**

```
1. بعد قراءة البطاقة الأولى بنجاح
2. اختر الرصيد المطلوب من القائمة:
   - 25 NIS
   - 50 NIS
   - 100 NIS
   - 150 NIS
   - 200 NIS
   - 250 NIS
   - 300 NIS
3. ضع بطاقة جديدة (فارغة) بالقرب من NFC
4. انتظر 3-5 ثواني
5. يجب أن ترى:
   - "Card written successfully"
   - البطاقة الجديدة الآن مشحونة! ✅
```

---

## 📊 مراقبة الـ Logs

لمعرفة ماذا يحدث بالفعل:

```bash
adb logcat -s "EnhancedMainActivity:I" "MCKDecryptor:I" "CryptoAnalyzer:I"
```

أو استخدم الـ script:

```bash
cd c:\Users\HP OMNIBOOK\Desktop\test
.\INSTALL_AND_TEST.bat "C:\path\to\APK.apk"
```

---

## ✅ علامات النجاح

### القراءة ✓
```
[EnhancedMainActivity] Card detected
[MCKDecryptor] MCK derivation: SUCCESS
[CryptoAnalyzer] Decryption: SUCCESS
[EnhancedMainActivity] Balance: 100 NIS
```

### الكتابة ✓
```
[EnhancedMainActivity] Selected balance: 100 NIS
[CardPersonalizer] Modifying contract...
[CryptoAnalyzer] MAC calculation: SUCCESS
[CardPersonalizer] Writing to card...
[EnhancedMainActivity] Card written successfully
```

---

## ❌ استكشاف الأخطاء

### المشكلة: "لا يوجد بطاقة"
```
السبب: البطاقة بعيدة جداً أو NFC غير مفعل
الحل:
  1. تأكد من تشغيل NFC على الهاتف
  2. ضع البطاقة أقرب من الهاتف
  3. جرّب جهتي الهاتف المختلفة
```

### المشكلة: "فشل فك التشفير"
```
السبب: MCK غير صحيح أو البطاقة غير معروفة
الحل:
  1. تأكد من أن البطاقة Calypso compliant
  2. جرّب البطاقات الأخرى
  3. تحقق من اللوج للأخطاء المحددة
```

### المشكلة: "لم تُكتب البطاقة"
```
السبب: البطاقة قد تكون محمية أو مشحونة بالفعل
الحل:
  1. جرّب بطاقة جديدة (فارغة) تماماً
  2. انتظر 5 ثواني قبل إزالة البطاقة
  3. تحقق من اللوج للأخطاء
```

---

## 🔄 الاستخدام المتكرر

لشحن عدة بطاقات:

```
1. اختبر مع بطاقة واحدة أولاً
2. إذا نجحت:
   - ضع بطاقة جديدة
   - اختر الرصيد
   - انتظر الكتابة
   - كرر مع بطاقات أخرى
3. التطبيق سيعمل كل مرة بنفس الطريقة
```

---

## 📞 المساعدة

إذا واجهتك مشكلة:

1. اقرأ الـ logs:
   ```bash
   adb logcat > log.txt
   ```

2. ابحث عن كلمات:
   - `Error`
   - `Exception`
   - `failed`

3. شارك الـ logs لتشخيص المشكلة

---

## 🎉 النجاح!

**إذا اتبعت الخطوات:**
- ✅ التطبيق مثبت
- ✅ البطاقة الأولى تُقرأ بنجاح
- ✅ البطاقة الجديدة مشحونة بنجاح

**أنت الآن جاهز لشحن أي عدد من البطاقات!** 🚀

