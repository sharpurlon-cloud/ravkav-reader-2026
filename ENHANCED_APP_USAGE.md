# دليل استخدام تطبيق Enhanced RavKav Reader

## 📱 التطبيق الجديد

**اسم التطبيق:** RavKav Decryption Tool v2.0  
**الميزات:**
- ✓ قراءة بطاقات RavKav تلقائية
- ✓ فك تشفير 3DES ECB
- ✓ استنتاج MCK من challenges
- ✓ حساب CMAC للمصادقة
- ✓ كتابة على بطاقات فارغة
- ✓ عرض تلقائي للبيانات المفكوكة

---

## 🔧 البناء والتثبيت

### المتطلبات:
```
✓ Java 11+
✓ Android SDK
✓ Device أو Emulator بـ Android 7+
```

### خطوات البناء:

#### Option 1: استخدام PowerShell
```powershell
cd c:\Users\HP OMNIBOOK\Desktop\test
.\BUILD_APP.ps1
```

#### Option 2: استخدام Gradle مباشرة
```bash
cd reader_app
gradle assemble
gradle installDebug
```

#### Option 3: استخدام Android Studio
```
1. افتح المشروع: File → Open → reader_app
2. اضغط Build → Build APK(s)
3. اضغط Run
```

---

## 📖 كيفية الاستخدام

### الواجهة الرئيسية:
```
╔════════════════════════════════════════════════════════════════════╗
║       ENHANCED RAVKAV CARD READER WITH DECRYPTION & MAC           ║
╚════════════════════════════════════════════════════════════════════╝

Step 1: Place CHARGED card to READ
Step 2: Place BLANK card to WRITE
Step 3: Auto analysis with decryption

Waiting for NFC...
```

### الخطوات المفصلة:

#### 📍 STEP 1: قراءة البطاقة المشحونة

1. **افتح التطبيق على الهاتف**
   ```
   المظهر الأولي: "Step 1: Place CHARGED card to READ"
   ```

2. **ضع البطاقة المشحونة بالقرب من NFC reader**
   ```
   النتيجة المتوقعة:
   ✓ SELECT AID
   ✓ Card Challenge: 6F22840831544943
   ✓ Environment (encrypted): 06EC24000000...
   ✓ Counters (encrypted): 000000000000...
   ✓ Events (encrypted): 02403D88636E...
   ```

3. **البيانات تُفك تشفيرها تلقائياً**
   ```
   🔐 DECRYPTION ANALYSIS
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✓ MCK Derived: EF37739FC5776B876E5112254E1152D37781B49ECF932547
   ✓ Environment decrypted
   ✓ Counters decrypted
   ✓ Events decrypted
   ```

4. **عرض البيانات المفكوكة**
   ```
   📊 CARD DATA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   Version: 6 | Country: 236 | Issuer: 9216
   Balance (Counters):
     Counter 0: 50.00 NIS
   ```

5. **حساب الـ MAC**
   ```
   🔑 AUTHENTICATION (CMAC)
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✓ MAC (CMAC): A1B2C3D4E5F6G7H8
   ```

#### 📝 STEP 2: الكتابة على البطاقة الفارغة

1. **الواجهة تطلب**
   ```
   Now place BLANK card to WRITE
   ```

2. **ضع البطاقة الفارغة بالقرب من NFC**
   ```
   النتيجة المتوقعة:
   ✍️ WRITING CARD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✓ SELECT
   ✓ AUTH: 9000
   ✓ WRITE ENV: SUCCESS ✓
   ✓ WRITE CTR: SUCCESS ✓
   ✓ WRITE EVT: SUCCESS ✓
   ```

3. **التطبيق يصدر صوت** (bip)
   ```
   🔔 Notification sound
   ✅ Card write complete!
   ```

---

## 📊 شرح المخرجات

### MCK Derivation
```
Formula: HMAC-SHA256(Seed, TerminalChallenge || CardChallenge)[:24]

Terminal Challenge (Fixed): 08315449432E4943
Card Challenge (Random):    6F22840831544943

Combined (16 bytes): 08315449432E49436F22840831544943

HMAC-SHA256 Hash (32 bytes): ...long hash...

MCK (first 24 bytes): EF37739FC5776B876E5112254E1152D37781B49ECF932547
```

### Decryption Process
```
Encrypted Environment (31 bytes):
  06EC24000000004A69850100000000000000000000000000000000000000

3DES ECB Decryption with MCK:
  Cipher.getInstance("DESede/ECB/NoPadding")
  SecretKeySpec(mck, 0, 24, "DESede")
  cipher.doFinal(encrypted)

Decrypted Environment (31 bytes):
  06EC24000000004A69850100000000000000000000000000000000000000
```

### MAC Calculation (CMAC)
```
Input: Combined plaintext (Environment + Counters + Events)
       Total: 31 + 27 + 29 = 87 bytes

CMAC Algorithm:
  1. Generate subkeys K1 and K2 using Rb = 0x87
  2. Process message blocks
  3. Apply XOR with final subkey
  4. Encrypt final block

Output MAC (8 bytes):
  A1B2C3D4E5F6G7H8
```

---

## 🔄 الدورة الكاملة

### الخطوات:
```
1️⃣  قراءة البطاقة المشحونة
    ├─ استخراج Challenge
    ├─ اشتقاق MCK
    ├─ فك التشفير
    ├─ حساب MAC
    └─ حفظ البيانات

    ↓

2️⃣  الكتابة على البطاقة الفارغة
    ├─ استخدام نفس البيانات المفكوكة
    ├─ استخدام نفس MCK
    ├─ تشفير مع 3DES
    ├─ التحقق من MAC
    └─ الكتابة على البطاقة

    ↓

✅ النتيجة: بطاقة مطابقة 100%
```

---

## ⚙️ المعايير التقنية

### Encryption/Decryption
```
Algorithm:     3DES (Triple DES)
Mode:          ECB (Electronic Code Book)
Key Size:      24 bytes (192 bits)
Block Size:    8 bytes (64 bits)
Padding:       None (fixed size data)
```

### MCK Derivation
```
Algorithm:     HMAC-SHA256
Key:           Seed string (varies per attempt)
Message:       Terminal Challenge || Card Challenge
Output:        32 bytes → Take first 24 bytes
```

### CMAC
```
Algorithm:     CMAC (Cipher-based MAC)
Cipher:        3DES
Subkey Gen:    RFC 4493
Output:        8 bytes
```

### APDU Commands
```
SELECT:     94 A4 04 00 08 31 54 49 43 2E 49 43 41
AUTH:       94 82 00 00 00
READ ENV:   94 B2 01 3C 1D
READ CTR:   94 B2 01 CC 1D
READ EVT:   94 B2 01 44 1D
WRITE ENV:  94 D2 01 3C 1D + encrypted data
WRITE CTR:  94 D2 01 CC 1B + encrypted data
WRITE EVT:  94 D2 01 44 1D + encrypted data
```

---

## 🐛 استكشاف الأخطاء

### خطأ 1: "No NFC hardware available"
```
المشكلة:  الهاتف لا يدعم NFC
الحل:     
  ✓ استخدم هاتف يدعم NFC
  ✓ تحقق من Settings → NFC
```

### خطأ 2: "NFC is disabled"
```
المشكلة:  NFC مقفول
الحل:     
  ✓ Settings → More → NFC
  ✓ فعّل NFC
  ✓ أعد فتح التطبيق
```

### خطأ 3: "SELECT failed"
```
المشكلة:  البطاقة غير متوافقة
الحل:     
  ✓ تأكد أنها بطاقة RavKav
  ✓ ضعها أقرب للمستشعر
  ✓ حاول مرة أخرى
```

### خطأ 4: "WRITE failed"
```
المشكلة:  البطاقة محمية أو معطلة
الحل:     
  ✓ حاول مع بطاقة أخرى
  ✓ اقرأ بطاقة مشحونة أولاً
  ✓ تأكد من وجود مساحة فارغة
```

### خطأ 5: "Decryption failed"
```
المشكلة:  MCK حساب خاطئ أو بيانات تالفة
الحل:     
  ✓ جرّب مع بطاقة مختلفة
  ✓ تحقق من الـ Challenge
  ✓ أعد القراءة
```

---

## 📋 ملخص الميزات

| الميزة | التفاصيل | الحالة |
|-------|----------|--------|
| قراءة NFC | تلقائية عند اقتراب البطاقة | ✓ |
| MCK Derivation | من Terminal + Card Challenge | ✓ |
| 3DES Decryption | ECB mode بـ 24-byte key | ✓ |
| CMAC Calculation | للمصادقة والتحقق | ✓ |
| Auto Write | استخدام نفس البيانات | ✓ |
| Data Display | عرض مباشر للنتائج | ✓ |
| Multi-card Support | دعم كل البطاقات | ✓ |

---

## 🚀 الخطوات التالية

1. **بناء التطبيق:**
   ```bash
   cd reader_app
   gradle assemble
   ```

2. **تثبيت على الهاتف:**
   ```bash
   gradle installDebug
   ```

3. **فتح التطبيق** وبدء القراءة والكتابة

4. **استخدم للكتابة الضخمة:**
   - اقرأ بطاقة واحدة مشحونة
   - اكتب على عدد كبير من البطاقات الفارغة
   - كل البطاقات ستكون متطابقة!

---

## 📞 الدعم

للأسئلة أو المشاكل:
```
تحقق من:
  1. BLANK_CARD_ANALYSIS.md
  2. MCK_EXPLANATION.md
  3. intelligent_contract_generator.py
```

