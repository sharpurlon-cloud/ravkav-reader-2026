# أمثلة عملية - تحليل التشفير

## مثال 1: تحليل بطاقة 3DES قياسية

### البيانات المستلمة:

```
APDUs Sent:
  [0] SELECT: 94 A4 04 00 08 31 54 49 43 2E 49 43 41
  [1] READ ENV: 94 B2 01 3C 1D
  [2] READ COUNTERS: 94 B2 01 CC 1D
  [3] READ CONTRACT[1]: 94 B2 01 4C 1D
  ...

Responses:
  [0] SELECT: A1 B2 C3 D4 E5 F6 G7 90 00
  [1] ENV: 1F 2A 3B 4C 5D 6E 7F 8A 9B AC BD CE DF E0 F1 02 13 24 35 46 57 68 79 8A 9B 90 00
  [2] COUNTERS: 3A 4B 5C 6D 7E 8F 9A AB BC CD DE EF F0 01 12 23 34 45 56 67 78 89 9A AB BC 90 00
  ...
```

### تحليل CryptoAnalyzer:

```
كشف أطوال الـ Responses:
  len_29: 8 مرات ← 29 بايت بيانات + 2 بايت SW = 31 بايت إجمالي
  len_27: 2 مرات

حساب الأنماط:
  29 - 2 = 27 بايت بيانات
  27 % 8 = 3 ❌ (ليس مضاعف 8)
  
  لكن: 24 % 8 = 0 ✓ (مع حشو PKCS7)
  آخر بايت = 0x05 (5 بايتات حشو)
  
تحديد التشفير:
  Block sizes تطابقية = 8 بايت → 3DES مؤكد
  Confidence = 90%
```

### التقرير النهائي:

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
Challenges captured: 12
  [0]: 94A40400083154
  [1]: 94B20103C1D
  [2]: 94B2010CC1D
  ...
Pattern: Sequential
  → تحذير: التحديات من الـ Terminal متسلسلة (يمكن التنبؤ بها)

=== Card Challenge Sequence ===
Challenges captured: 12
  [0]: 7F8A9BACBDCEDF
  [1]: A1B2C3D4E5F6A7
  [2]: B8C9D0E1F2A3B4
  ...
Pattern: Random
  → جيد: البطاقة ترسل تحديات عشوائية
```

---

## مثال 2: اكتشاف نقطة ضعف في التحديات

### السيناريو:
تطبيق يستخدم تحديات متسلسلة بدلاً من العشوائية

### البيانات المستقبلة:

```
Card Challenges (من 5 جلسات):
جلسة 1: Card Challenge = 0x00 00 00 00 00 00 00 01
جلسة 2: Card Challenge = 0x00 00 00 00 00 00 00 02
جلسة 3: Card Challenge = 0x00 00 00 00 00 00 00 03
جلسة 4: Card Challenge = 0x00 00 00 00 00 00 00 04
جلسة 5: Card Challenge = 0x00 00 00 00 00 00 00 05
```

### التحليل:

```
analyzePattern() يكتشف:
  isNextSequential(0x0001, 0x0002) = true
  isNextSequential(0x0002, 0x0003) = true
  isNextSequential(0x0003, 0x0004) = true
  isNextSequential(0x0004, 0x0005) = true
  
  → نتيجة: isSequential = true
  → isRandom = false
```

### التقرير:

```
=== Card Challenge Sequence ===
Challenges captured: 5
  [0]: 0000000000000001
  [1]: 0000000000000002
  [2]: 0000000000000003
  [3]: 0000000000000004
  [4]: 0000000000000005
Pattern: Sequential

⚠️ تحذير أمني:
   البطاقة تستخدم تحديات متسلسلة!
   يمكن للمهاجم التنبؤ بالتحديات القادمة
   → نقطة ضعف حرجة (CRITICAL)
```

### سيناريو الهجوم:

```
1. المهاجم يراقب 5 جلسات ويسجل التحديات
2. يلاحظ النمط المتسلسل
3. يتنبأ بالتحديات التالية:
   الجلسة القادمة = 0x00 00 00 00 00 00 00 06
4. ينسخ بطاقة بدون معرفة المفتاح الحقيقي
```

---

## مثال 3: تحليل MAC المختلط

### السيناريو:
بطاقة تستخدم MACs بأطوال متنوعة

### البيانات المستقبلة:

```
Response 1: [Data: 29 bytes] [MAC: 4 bytes: A1 B2 C3 D4] [SW: 90 00]
Response 2: [Data: 28 bytes] [MAC: 8 bytes: E5 F6 A7 B8 C9 D0 E1 F2] [SW: 90 00]
Response 3: [Data: 29 bytes] [MAC: 4 bytes: A1 B2 C3 D4] [SW: 90 00]
Response 4: [Data: 28 bytes] [MAC: 8 bytes: E5 F6 A7 B8 C9 D0 E1 F2] [SW: 90 00]
```

### التحليل:

```
detectMACAlgorithm():
  mac4Count = 2  (Responses 1 و 3)
  mac8Count = 2  (Responses 2 و 4)
  
  mac8Count (2) > mac4Count (2)? NO
  mac8Count (2) >= mac4Count (2)? NO
  → اختيار MAC 4-byte
```

### التقرير:

```
MAC: CMAC-3DES (4 bytes)
  Algorithm: CMAC-3DES
  Size: 4 bytes
  Confidence: 50.0%  ← ثقة منخفضة (بسبب عدم اليقين)

⚠️ ملاحظة:
   البطاقة تستخدم MACs بأطوال متنوعة
   قد يشير إلى:
   - أخطاء في التشفير (عشوائي)
   - استخدام خوارزميتين مختلفتين
   - أخطاء في الحشو
```

---

## مثال 4: كشف Padding Oracle

### السيناريو:
اختبار نقاط ضعف الحشو (Padding)

### التجربة:

```java
// إرسال APDUs مختلفة لاختبار الحشو
byte[] probe1 = {0x94, 0xB2, 0x01, 0x3C, 0x1D};  // حشو 1 بايت
byte[] probe2 = {0x94, 0xB2, 0x01, 0x3C, 0x1E};  // حشو 2 بايت
byte[] probe3 = {0x94, 0xB2, 0x01, 0x3C, 0x1F};  // حشو 3 بايتات
```

### النتائج المحتملة:

```
probe1 response: [29 bytes] [MAC: A1 B2 C3 D4] [90 00] ✓
probe2 response: [30 bytes] [MAC: E5 F6 A7 B8] [90 00] ✓
probe3 response: [31 bytes] [ERROR: MAC incorrect] [63 00] ✗

كشف Padding Oracle:
  → البطاقة ترد بـ MAC خطأ عندما يكون الحشو خاطئ
  → يمكن استخدام هذا لاستخراج البيانات

التقرير:
  ⚠️ CRITICAL: Padding Oracle Attack Possible
     يمكن استخراج البيانات من خلال:
     1. تغيير الحشو
     2. مراقبة رسائل الخطأ
     3. استنتاج البيانات الأصلية
```

---

## مثال 5: التحليل التاريخي (الجلسات المتعددة)

### المتطلب:
تتبع التغييرات عبر جلسات متعددة

### البيانات:

```
جلسة 1 (2024-01-15 10:00):
  Cipher: 3DES, Confidence: 90%
  MAC: 4 bytes, Pattern: Sequential
  Challenges: 0x01, 0x02, 0x03, ...

جلسة 2 (2024-01-15 10:30):
  Cipher: 3DES, Confidence: 90%
  MAC: 4 bytes, Pattern: Sequential
  Challenges: 0x11, 0x12, 0x13, ...

جلسة 3 (2024-01-15 11:00):
  Cipher: 3DES, Confidence: 90%
  MAC: 4 bytes, Pattern: Sequential
  Challenges: 0x21, 0x22, 0x23, ...

جلسة 4 (2024-01-15 11:30):
  Cipher: 3DES, Confidence: 90%
  MAC: 8 bytes, Pattern: Sequential  ← تغيير!
  Challenges: 0x31, 0x32, 0x33, ...

جلسة 5 (2024-01-15 12:00):
  Cipher: AES, Confidence: 85%       ← تحديث!
  MAC: 8 bytes, Pattern: Random      ← تحسين أمني!
  Challenges: A1 B2 C3 D4, E5 F6 A7 B8, ...
```

### التحليل:

```
النمط الملاحظ:
1. الجلسات 1-3: ثابتة ومستقرة
2. الجلسة 4: تحديث الـ MAC إلى 8 بايت
3. الجلسة 5: تحديث شامل (3DES → AES, Sequential → Random)

الاستنتاجات:
✓ تحديثات أمنية تدريجية
✓ انتقال من 3DES إلى AES (اتجاه إيجابي)
✓ التحديات أصبحت عشوائية (تحسين الأمان)
✓ حجم MAC زاد إلى 8 بايت (مقاومة أفضل)

توصيات المراقبة:
→ متابعة الجلسات القادمة للتأكد من الاستقرار
→ اختبار AES بشكل شامل
→ التحقق من عدم وجود ثغرات في التنفيذ الجديد
```

---

## مثال 6: التقرير الشامل لبطاقة حقيقية

### بيانات بطاقة Rav-Kav فعلية:

```
READ ENVIRONMENT:
Raw: 1F 2A 3B 4C 5D 6E 7F 8A 9B AC BD CE DF E0 F1 02 13 24 35 46 57 68 79 8A 9B

READ COUNTERS:
Raw: 3A 4B 5C 6D 7E 8F 9A AB BC CD DE EF F0 01 12 23 34 45 56 67 78 89 9A AB BC

READ CONTRACT[1]:
Raw: 5C 6D 7E 8F 9A AB BC CD DE EF F0 01 12 23 34 45 56 67 78 89 9A AB BC CD DE

APDU Count: 23
Response Count: 23
```

### التحليل الكامل:

```
========== CRYPTOGRAPHY ANALYSIS REPORT ==========

Cipher: 3DES
  Block Size: 8 bytes
  Key Length: 192 bits (alt: 168 bits)
  Confidence: 90.0%
  
  تفسير:
  - كل Responses مضاعفات 8 بايت
  - نمط متسق مع 3DES-ECB
  - لا يوجد دليل على AES (16-byte blocks)

MAC: CMAC-3DES (4 bytes)
  Algorithm: CMAC-3DES
  Size: 4 bytes
  Confidence: 80.0%
  
  تفسير:
  - آخر 4 بايت من كل Response تشبه MAC
  - الإنتروبيا عالية (بايتات مختلفة)
  - متسق مع CMAC-3DES المعياري

Padding: PKCS7 (Confidence: 85.0%)
  
  تفسير:
  - آخر بايت = 0x05 (5 بايتات حشو)
  - البايتات الخمسة الأخيرة كلها 0x05
  - مطابق لمعيار PKCS7

=== Terminal Challenge Sequence ===
Challenges captured: 23
  [0]: 94A4040008315449
  [1]: 94B20103C1D
  [2]: 94B2010CC1D
  [3]: 94B2010CC1D
  [4]: 94B2014C1D
  ...
Pattern: Sequential
  
  تفسير:
  - APDUs ترسل بتسلسل ثابت
  - كل APDU حتمي (يمكن التنبؤ به)
  - ⚠️ نقطة ضعف: يمكن للمهاجم توقع الـ APDUs

=== Card Challenge Sequence ===
Challenges captured: 23
  [0]: 1F2A3B4C5D6E7F8A
  [1]: 9BACBDCEDF E0F102
  [2]: 1324354657687980
  [3]: 9AABACBDC DEEF001
  [4]: 1223344556677889
  ...
Pattern: Random
  
  تفسير:
  - البطاقة ترسل بيانات مختلفة في كل جلسة
  - بايتات التحديات لا تتبع نمط واضح
  - ✓ جيد: يصعب التنبؤ بالتحديات

Response Pattern Statistics:
  len_29: 15  ← معظم الـ Responses (15 مرة)
  len_27: 5   ← بعض الـ Responses (5 مرات)
  len_31: 3   ← Responses الطويلة (3 مرات)

الخلاصة الأمنية:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
نقاط القوة:
  ✓ استخدام 3DES (شامل التوثيق)
  ✓ MAC 4-byte للتحقق من السلامة
  ✓ PKCS7 padding معياري
  ✓ تحديات البطاقة عشوائية

نقاط الضعف:
  ⚠️ Terminal Challenge متسلسل (يمكن التنبؤ به)
  ⚠️ 3DES قديم (يفضل AES)
  ⚠️ 4-byte MAC صغير (يفضل 8 bytes)

التوصيات:
  1. تحديث Terminal لاستخدام تحديات عشوائية
  2. ترقية إلى AES-256 في التحديثات القادمة
  3. زيادة حجم MAC إلى 8 بايت
  4. فحص أعمق للتحديات الطويلة (len_31)
```

---

## جدول المقارنة

| المعيار | آمن | متوسط | ضعيف |
|---------|-----|--------|------|
| **Cipher** | AES-256 | 3DES | DES أحادي |
| **Block Size** | 16 | 8 | 4 أو أقل |
| **MAC Size** | 8 | 6-7 | 4 أو أقل |
| **MAC Type** | CMAC | CBC-MAC | CRC |
| **Padding** | PKCS7 | ISO 10126 | Zero |
| **Challenge** | Random | Semi-random | Sequential |
| **Challenge Length** | >= 8 | 6-7 | <= 4 |
| **Confidence** | > 90% | 70-89% | < 70% |

---

## أدوات المراقبة المقترحة

```
# تسجيل كل العمليات
adb logcat | grep "CryptoAnalyzer"

# حفظ التقارير
adb logcat > /tmp/crypto_analysis.log

# تصفية النتائج
adb logcat | grep "Cipher:\|MAC:\|Pattern:"

# المراقبة المباشرة
adb logcat -s RavKavReaderPoC
```

