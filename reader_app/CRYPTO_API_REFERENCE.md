# CryptoAnalyzer API Reference

## نظرة عامة على الـ API

```
CryptoAnalyzer
├── analyzeCard()            ← الدالة الرئيسية
├── detectCipherType()       ← كشف التشفير
├── detectMACAlgorithm()     ← كشف MAC
├── extractChallenges()      ← استخراج التحديات
├── detectPaddingScheme()    ← كشف الحشو
└── getReport()              ← الحصول على التقرير

AnalysisReport
├── CipherInfo
├── MACInfo
├── ChallengeSequence
└── PaddingScheme
```

---

## Enums

### CipherType
```java
enum CipherType {
    TRIPLE_DES("3DES", 8),      // حجم الكتلة = 8
    AES("AES", 16),             // حجم الكتلة = 16
    UNKNOWN("UNKNOWN", 0)       // غير معروف
}
```

**الحقول:**
- `name` (String): اسم التشفير
- `blockSize` (int): حجم الكتلة بالبايت

**الاستخدام:**
```java
if (cipherInfo.type == CipherType.TRIPLE_DES) {
    // التعامل مع 3DES
}
```

---

### MACType
```java
enum MACType {
    CMAC_3DES_4BYTE("CMAC-3DES (4 bytes)", 4),
    CMAC_AES_8BYTE("CMAC-AES (8 bytes)", 8),
    CMAC_AES_4BYTE("CMAC-AES (4 bytes)", 4),
    UNKNOWN("UNKNOWN", 0)
}
```

**الحقول:**
- `name` (String): اسم الـ MAC
- `size` (int): حجم MAC بالبايت

---

### PaddingType
```java
enum PaddingType {
    PKCS7("PKCS7"),         // المعياري
    ISO10126("ISO 10126"),  // بديل
    ZERO("Zero Padding"),   // بسيط
    NONE("None"),           // بدون حشو
    UNKNOWN("Unknown")      // غير معروف
}
```

---

## Classes

### CryptoAnalyzer (الفئة الرئيسية)

#### البناء
```java
public CryptoAnalyzer()
```

**الوصف:** ينشئ مثيل جديد من المحلل

**المثال:**
```java
CryptoAnalyzer analyzer = new CryptoAnalyzer();
```

---

#### analyzeCard()
```java
public AnalysisReport analyzeCard(byte[][] apdus, byte[][] responses)
```

**الوصف:** تحليل شامل لبطاقة بناءً على APDUs والـ Responses

**المعاملات:**
- `apdus` (byte[][]): مصفوفة من كل الـ APDUs المرسلة
- `responses` (byte[][]): مصفوفة من كل الـ Responses المستقبلة

**القيمة المرجعة:** `AnalysisReport` - التقرير الشامل

**الاستثناءات:** لا توجد (جميع العمليات آمنة)

**المثال:**
```java
byte[][] apdus = {
    {0x94, 0xA4, 0x04, 0x00, 0x08, ...},
    {0x94, 0xB2, 0x01, 0x3C, 0x1D}
};
byte[][] responses = {
    {0xA1, 0xB2, 0xC3, 0xD4, ...},
    {0x1F, 0x2A, 0x3B, 0x4C, ...}
};

AnalysisReport report = analyzer.analyzeCard(apdus, responses);
System.out.println(report);
```

---

#### getReport()
```java
public AnalysisReport getReport(byte[][] apdus, byte[][] responses)
```

**الوصف:** (Alias) نفس وظيفة analyzeCard()

**المثال:**
```java
AnalysisReport report = analyzer.getReport(apdus, responses);
```

---

### CipherInfo

#### الحقول
```java
public CipherType type;         // نوع التشفير
public int blockSize;           // حجم الكتلة
public int keyLength;           // طول المفتاح الأساسي
public int keyLengthAlt;        // طول بديل
public double confidence;       // معامل الثقة (0-1)
```

#### toString()
```java
@Override
public String toString()
```

**المثال:**
```
Cipher: 3DES
  Block Size: 8 bytes
  Key Length: 192 bits (alt: 168 bits)
  Confidence: 90.0%
```

#### الاستخدام
```java
CipherInfo cipher = report.getCipherInfo();
if (cipher.confidence > 0.8) {
    System.out.println("High confidence: " + cipher.type.name);
}
```

---

### MACInfo

#### الحقول
```java
public MACType type;            // نوع MAC
public String algorithm;        // اسم الخوارزمية
public int macSize;             // حجم بالبايت
public double confidence;       // معامل الثقة
```

#### toString()
```java
@Override
public String toString()
```

**المثال:**
```
MAC: CMAC-3DES (4 bytes)
  Algorithm: CMAC-3DES
  Size: 4 bytes
  Confidence: 80.0%
```

#### الحصول على المعلومات
```java
MACInfo mac = report.getMacInfo();
if (mac.macSize < 8) {
    System.out.println("WARNING: Small MAC size");
}
```

---

### PaddingScheme

#### الحقول
```java
public PaddingType type;        // نوع الحشو
public double confidence;       // معامل الثقة
```

#### toString()
```java
@Override
public String toString()
```

**المثال:**
```
Padding: PKCS7 (Confidence: 85.0%)
```

---

### ChallengeSequence

#### الحقول (Private)
```java
private byte[][] challenges;    // كل التحديات
private int count;              // العدد الفعلي
private boolean isRandom;       // هل عشوائي؟
private boolean isSequential;   // هل متسلسل؟
```

#### addChallenge()
```java
public void addChallenge(byte[] challenge)
```

**الوصف:** إضافة تحدي جديد للتسلسل

**المعاملات:**
- `challenge` (byte[]): بيانات التحدي

**المثال:**
```java
ChallengeSequence seq = new ChallengeSequence();
seq.addChallenge(new byte[]{0x01, 0x02, 0x03});
```

#### analyzePattern()
```java
public void analyzePattern()
```

**الوصف:** تحليل النمط بعد إضافة كل التحديات

**المثال:**
```java
seq.analyzePattern();
if (seq.isRandom) {
    System.out.println("Random challenges - Good");
}
```

#### toString()
```java
@Override
public String toString()
```

**المثال:**
```
Challenges captured: 5
  [0]: 01020304
  [1]: 05060708
Pattern: Sequential
```

---

### AnalysisReport

#### الحقول (Private)
```java
private CipherInfo cipherInfo;
private MACInfo macInfo;
private ChallengeSequence terminalChallenge;
private ChallengeSequence cardChallenge;
private PaddingScheme paddingScheme;
private String responseStats;
```

#### setters
```java
public void setCipherInfo(CipherInfo info)
public void setMacInfo(MACInfo info)
public void setTerminalChallenge(ChallengeSequence ch)
public void setCardChallenge(ChallengeSequence ch)
public void setPaddingScheme(PaddingScheme ps)
public void setResponseStats(String stats)
```

**المثال:**
```java
AnalysisReport report = new AnalysisReport();
report.setCipherInfo(cipherInfo);
report.setMacInfo(macInfo);
```

#### toString()
```java
@Override
public String toString()
```

**المثال:**
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
  [1]: 94B20103C1D
Pattern: Sequential

=== Card Challenge Sequence ===
Challenges captured: 5
  [0]: 1F2A3B4C5D6E7F8A
  [1]: 9BACBDCEDF E0F102
Pattern: Random

Response Pattern Statistics:
  len_29: 8
  len_27: 2
```

---

## Private Methods (للمرجعية)

### detectCipherType()
```java
private void detectCipherType(byte[][] apdus, byte[][] responses)
```

**الخوارزمية:**
1. فحص P1/P2 في APDUs للبحث عن علامات التشفير
2. حساب طول الـ Response
3. التحقق إذا كانت مضاعفات 8 (3DES) أو 16 (AES)
4. تعيين نوع التشفير ومعامل الثقة

**الناتج:**
- `detectedCipher.type`
- `detectedCipher.blockSize`
- `detectedCipher.keyLength`
- `detectedCipher.confidence`

---

### detectMACAlgorithm()
```java
private void detectMACAlgorithm(byte[][] apdus, byte[][] responses)
```

**الخوارزمية:**
1. فحص آخر 4-8 بايت من كل Response
2. حساب entropy (التنوع)
3. عد نماذج MAC بأطوال مختلفة
4. اختيار الطول الأكثر شيوعاً

**الناتج:**
- `detectedMAC.type`
- `detectedMAC.algorithm`
- `detectedMAC.macSize`
- `detectedMAC.confidence`

---

### extractChallenges()
```java
private void extractChallenges(byte[][] apdus, byte[][] responses)
```

**الخوارزمية:**
1. البحث عن بيانات التحديات في APDUs (Terminal Challenges)
2. البحث عن بيانات التحديات في Responses (Card Challenges)
3. استدعاء `analyzePattern()` على كل تسلسل

**الناتج:**
- `terminalChallenge` مع نمط (sequential/random)
- `cardChallenge` مع نمط

---

### detectPaddingScheme()
```java
private void detectPaddingScheme(byte[][] responses)
```

**الخوارزمية:**
1. فحص آخر بايت من كل Response
2. اختبار PKCS7 (آخر بايت = عدد بايتات الحشو)
3. اختبار ISO 10126
4. اختبار Zero Padding
5. عد النجاحات لكل نوع

**الناتج:**
- `detectedPadding.type`
- `detectedPadding.confidence`

---

### Helper Methods

#### isValidMAC()
```java
private boolean isValidMAC(byte[] macBytes)
```

**الوصف:** التحقق من أن بيانات MAC لديها entropy كافي

**القيمة المرجعة:** true إذا كانت valid

---

#### checkPKCS7Padding()
```java
private boolean checkPKCS7Padding(byte[] data)
```

**الوصف:** التحقق من امتثال PKCS7

**القيمة المرجعة:** true إذا كانت صحيحة

---

#### checkISO10126Padding()
```java
private boolean checkISO10126Padding(byte[] data)
```

**الوصف:** التحقق من امتثال ISO 10126

**القيمة المرجعة:** true إذا كانت صحيحة

---

#### isLikelyChallengeData()
```java
private boolean isLikelyChallengeData(byte[] data)
```

**الوصف:** التحقق من احتمالية كون البيانات تحديات

**القيمة المرجعة:** true إذا بدت كتحديات

---

## Integration with MainActivity

### Initialization
```java
private CryptoAnalyzer cryptoAnalyzer;
private List<byte[]> apdus;
private List<byte[]> responses;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    apdus = new ArrayList<>();
    responses = new ArrayList<>();
    cryptoAnalyzer = new CryptoAnalyzer();
}
```

### Tracking APDUs
```java
private byte[] transceiveAndTrack(IsoDep isoDep, byte[] cmd) 
    throws IOException {
    apdus.add(cmd.clone());              // حفظ APDU
    byte[] resp = transceive(isoDep, cmd);
    responses.add(resp.clone());         // حفظ Response
    return resp;
}
```

### Running Analysis
```java
private String runCryptoAnalysis() {
    if (apdus.isEmpty() || responses.isEmpty()) {
        return "(Insufficient data for crypto analysis)\n";
    }

    byte[][] apduArray = apdus.toArray(new byte[0][]);
    byte[][] respArray = responses.toArray(new byte[0][]);

    CryptoAnalyzer.AnalysisReport report = 
        cryptoAnalyzer.getReport(apduArray, respArray);
    
    Log.i(TAG, "Crypto Analysis: " + report.toString());
    return report.toString();
}
```

---

## الثوابت والتكوينات

### Block Sizes
```java
3DES Block Size: 8 bytes
AES Block Size: 16 bytes
```

### Key Lengths
```java
3DES: 192-bit (3x64) أو 168-bit (3x56)
AES: 256-bit أو 128-bit
```

### MAC Sizes
```java
Default: 4 bytes
Extended: 8 bytes
```

### Confidence Thresholds
```java
High (> 80%): موثوقة
Medium (60-80%): معقولة
Low (< 60%): مشكوك فيها
```

---

## معالجة الأخطاء

### جميع العمليات آمنة من الأخطاء

```java
// لا توجد استثناءات في CryptoAnalyzer
// جميع الحالات محملة:

null apdus → (Insufficient data)
null responses → (Insufficient data)
empty arrays → (Insufficient data)
malformed data → (tries to parse as-is)
```

---

## Performance

### التعقيد الزمني
```
analyzeCard(n APDUs, m Responses):
  - detectCipherType: O(n+m)
  - detectMACAlgorithm: O(m)
  - extractChallenges: O(n+m)
  - detectPaddingScheme: O(m)
  
  الإجمالي: O(n+m) خطي
```

### استخدام الذاكرة
```
- apdus list: ~O(n * L) حيث L = متوسط طول APDU
- responses list: ~O(m * R) حيث R = متوسط طول Response
- CryptoAnalyzer: ~O(1) ثابت
```

---

## أفضل الممارسات

### 1. تجميع البيانات الكافية
```java
// غير كافي
if (apdus.size() < 3) {
    return "(Need more samples)\n";
}

// كافي
if (apdus.size() >= 10) {
    // Run analysis
}
```

### 2. مراقبة معاملات الثقة
```java
if (report.getCipherInfo().confidence < 0.7) {
    Log.w(TAG, "Low confidence - more sampling needed");
}
```

### 3. التحقق من الأنماط المريبة
```java
if (report.getCardChallenge().isSequential) {
    Log.e(TAG, "CRITICAL: Sequential challenges detected");
}
```

### 4. تسجيل النتائج
```java
Log.i(TAG, "Crypto Analysis: " + report.toString());
// أو
adb logcat | grep "CryptoAnalyzer"
```

---

## أمثلة عملية

### مثال 1: تحليل بسيط
```java
CryptoAnalyzer analyzer = new CryptoAnalyzer();
AnalysisReport report = analyzer.analyzeCard(apdus, responses);
System.out.println(report);
```

### مثال 2: اختبار معين
```java
CipherInfo cipher = report.getCipherInfo();
if (cipher.type == CipherType.AES) {
    if (cipher.keyLength == 256) {
        System.out.println("Strong encryption");
    }
}
```

### مثال 3: المراقبة المستمرة
```java
while (running) {
    AnalysisReport report = analyzer.analyzeCard(apdus, responses);
    
    // تحليل الأمان
    if (report.getCardChallenge().isSequential) {
        alert("Security issue detected");
    }
    
    // تسجيل
    saveReport(report);
}
```

---

## Troubleshooting

### مشكلة: "Insufficient data"
**الحل:** تأكد من وجود APDUs و Responses

```java
if (!apdus.isEmpty() && !responses.isEmpty()) {
    // Run analysis
}
```

### مشكلة: "Confidence too low"
**الحل:** جمع عينات أكثر

```java
if (report.getCipherInfo().confidence < 0.8) {
    // Need more samples
    readMoreRecords();
}
```

### مشكلة: "Pattern not detected"
**الحل:** البيانات قد تكون غير كافية

```java
if (!report.getTerminalChallenge().isSequential 
    && !report.getTerminalChallenge().isRandom) {
    // Might need different APDU pattern
}
```

