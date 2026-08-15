# Rav-Kav Card Reader - Cryptography Analysis Module

## نظرة عامة
تم تطوير وحدة تحليل التشفير المتقدمة لـ **RavKavCardReader** لكشف أنماط التشفير المستخدمة في بطاقات Rav-Kav/Calypso بشكل تلقائي.

---

## المكونات الجديدة

### 1. **CryptoAnalyzer.java** (وحدة التحليل الرئيسية)

#### الميزات الأساسية:

#### أ) كشف نوع التشفير (Cipher Detection)
```java
// يكتشف تلقائياً:
- TRIPLE_DES (3DES) - Block Size: 8 bytes
- AES - Block Size: 16 bytes
```

**الآلية:**
- تحليل طول الـ Response في الـ APDUs
- الـ 3DES ينتج كتل 8 بايت
- AES ينتج كتل 16 بايت
- حساب معامل الثقة (Confidence) بناءً على عدد النماذج المطابقة

**المخرجات:**
- نوع التشفير المكتشف
- حجم الكتلة (Block Size)
- طول المفتاح (Key Length)
  - 3DES: 192-bit أو 168-bit
  - AES: 256-bit أو 128-bit
- معامل الثقة (0-1.0)

---

#### ب) كشف خوارزمية MAC
```java
// يكتشف:
- CMAC-3DES (4 bytes)
- CMAC-AES (4 أو 8 bytes)
```

**الآلية:**
- فحص آخر بايتات الـ Response
- التحقق من صحة بيانات MAC (Entropy Check)
- عد تكرار نماذج MAC بأطوال مختلفة

**الفحوصات:**
```
- Is Valid MAC Check:
  * يجب أن تحتوي على بايتات مختلفة (ليست جميعاً متطابقة)
  * تشير إلى وجود MAC حقيقي وليس حشو عشوائي
```

---

#### ج) تحليل Challenge-Response
```java
// يسجّل ويحلل:
1. Terminal Challenge (من الـ APDU)
2. Card Challenge (من الـ Response)
```

**الكشف عن الأنماط:**
- **Sequential Pattern**: التحديات تزيد بالتسلسل
  - مؤشر على نقص الأمان (يمكن التنبؤ به)
  
- **Random Pattern**: تحديات عشوائية حقيقية
  - يشير إلى نظام أمان أقوى

**البيانات المسجّلة:**
```
- عدد التحديات المقبوضة (حتى 10)
- قيمة كل تحدي (بصيغة Hex)
- نوع النمط المكتشف
```

---

#### د) كشف نظام الحشو (Padding Detection)
```java
// يكتشف:
- PKCS7 Padding (الأكثر شيوعاً)
- ISO 10126 Padding
- Zero Padding
```

**خوارزمية الكشف:**

1. **PKCS7:**
   ```
   آخر بايت = عدد بايتات الحشو
   مثال: حشو بـ 5 بايتات = 05 05 05 05 05
   ```

2. **ISO 10126:**
   ```
   آخر بايت = عدد بايتات الحشو
   البايتات السابقة = قيم عشوائية
   ```

3. **Zero:**
   ```
   جميع بايتات الحشو = 0x00
   ```

---

### 2. **MainActivity.java** (تحديثات التكامل)

#### التغييرات الرئيسية:

**أ) متغيرات التتبع:**
```java
private List<byte[]> apdus;           // تخزين جميع الـ APDUs المرسلة
private List<byte[]> responses;       // تخزين جميع الـ Responses المستقبلة
private CryptoAnalyzer cryptoAnalyzer;  // مثيل المحلل
```

**ب) دوال جديدة:**

1. **transceiveAndTrack()**
   ```java
   - ترسل APDU
   - تسجل الـ APDU والـ Response تلقائياً
   - ترجع الـ Response
   ```

2. **readRecordAndTrack()**
   ```java
   - تقرأ سجل من البطاقة
   - تسجل جميع البيانات للتحليل
   - تعيد البيانات المفكوكة
   ```

3. **runCryptoAnalysis()**
   ```java
   - تشغيل المحلل على البيانات المجمعة
   - إرجاع التقرير الشامل
   - تسجيل في Logcat
   ```

---

## دورة التشغيل

### عند اكتشاف بطاقة:

```
1. تهيئة قوائم التتبع (Clear APDUs & Responses)
   ↓
2. SELECT "1TIC.ICA" مع التسجيل
   ↓
3. قراءة ENVIRONMENT مع التسجيل
   ↓
4. قراءة COUNTERS مع التسجيل
   ↓
5. قراءة CONTRACTS (8 سجلات) مع التسجيل
   ↓
6. قراءة EVENTS والـ SPECIAL EVENTS مع التسجيل
   ↓
7. تشغيل CryptoAnalyzer على كل البيانات المجمعة
   ↓
8. عرض التقرير الشامل (البيانات + تحليل التشفير)
```

---

## نموذج المخرجات

### تقرير التحليل الكامل:

```
========== CRYPTOGRAPHY ANALYSIS REPORT ==========

Cipher: 3DES
  Block Size: 8 bytes
  Key Length: 192 bits (alt: 168 bits)
  Confidence: 90.0%

MAC: CMAC-3DES (4 bytes)
  Algorithm: CMAC-3DES
  Size: 4 bytes
  Confidence: 80.0%

Padding: PKCS7 (Confidence: 85.0%)

=== Terminal Challenge Sequence ===
Challenges captured: 5
  [0]: 94A4040008315449
  [1]: 94B201CC1D
  [2]: 94B202CC1D
  [3]: 94B203CC1D
  [4]: 94B204CC1D
Pattern: Sequential

=== Card Challenge Sequence ===
Challenges captured: 3
  [0]: A7B2C3D4E5F6A7B8
  [1]: A7B2C3D4E5F6A7B9
  [2]: A8B2C3D4E5F6A7B8
Pattern: Random

Response Pattern Statistics:
  len_29: 8
  len_27: 2
```

---

## الفئات الداخلية

### 1. **CipherInfo**
```java
public CipherType type;      // نوع التشفير
public int blockSize;         // حجم الكتلة
public int keyLength;         // طول المفتاح الأساسي
public int keyLengthAlt;      // طول المفتاح البديل
public double confidence;     // معامل الثقة
```

### 2. **MACInfo**
```java
public MACType type;          // نوع MAC
public String algorithm;      // اسم الخوارزمية
public int macSize;           // حجم MAC بالبايتات
public double confidence;     // معامل الثقة
```

### 3. **PaddingScheme**
```java
public PaddingType type;      // نوع الحشو
public double confidence;     // معامل الثقة
```

### 4. **ChallengeSequence**
```java
private byte[][] challenges;  // كل التحديات المسجّلة
private int count;            // عدد التحديات
private boolean isRandom;     // هل النمط عشوائي؟
private boolean isSequential; // هل النمط متسلسل؟
```

---

## حالات الاستخدام

### 1. الكشف عن الضعف الأمني
```
إذا كانت Challenge Pattern = Sequential
→ تنبيه: يمكن التنبؤ بالتحديات (نقطة ضعف)
```

### 2. تحديد خوارزمية التفاعل
```
إذا كانت MAC Size = 4 و Cipher = 3DES
→ يستخدم CMAC-3DES (4 bytes)
```

### 3. فحص توافق الحشو
```
إذا كانت Padding = PKCS7 و Cipher = AES
→ توافق قياسي
```

---

## متطلبات الترجمة

### المكتبات المطلوبة:
- Android NFC API (مدمج)
- Java Collections (مدمج)
- Android Utilities (مدمج)

### الأذونات اللازمة:
```xml
<uses-permission android:name="android.permission.NFC" />
```

---

## الملفات المعدّلة/المنشأة

| الملف | النوع | الوصف |
|------|-------|-------|
| `CryptoAnalyzer.java` | جديد | وحدة التحليل الرئيسية |
| `MainActivity.java` | تعديل | إضافة التتبع والتكامل |

---

## الخطوات التالية (مقترحة)

1. **APDU Probe Testing**: إرسال APDUs خاصة للكشف عن نقاط ضعف
2. **Key Extraction**: محاولة استخراج المفاتيح من بيانات التحديات
3. **MAC Verification**: التحقق من صحة MAC يدوياً
4. **Cipher Oracle**: اكتشاف أخطاء التشفير (Padding Oracle, etc.)
5. **Historical Analysis**: تتبع التحديات عبر جلسات متعددة

---

## ملاحظات الأمان

- جميع العمليات **للقراءة فقط** - لا كتابة على البطاقة
- التحليل يتم على **الجهاز المحلي** - لا إرسال بيانات خارجياً
- معاملات الثقة قابلة للضبط (تعتمد على عدد العينات)

---

## المراجع

- AUTH-2026-001 PoC Documentation
- Calypso Card Specification
- NIST Cryptographic Standards
- ISO/IEC 10126 (Padding Standard)

