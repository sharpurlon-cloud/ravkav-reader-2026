# RavKav Card Reader - Enhanced Edition (AUTH-2026-001)

**Version:** 1.1-Enhanced  
**Build Date:** 2026-08-14  
**Status:** Ready for deployment

## What's New in This Version

### Real Probe APDU Execution
- 4 real Probe APDUs sent directly to card (no simulation)
  - SELECT 1TIC.ICA (variant 1)
  - SELECT 1TIC.ICA (variant 2)
  - SELECT Calypso standard AID
  - GET RESPONSE probe

### Actual Card Detection
The app now detects **different** results based on card state:

#### Empty Rav-Kav Card
```
DETECTION RESULT:
CARD TYPE: UNKNOWN / BLANK
Confidence: 30%
Probes: 0/4 successful
Data bytes: 0
```

#### Rav-Kav with Balance
```
DETECTION RESULT:
CARD TYPE: RAV-KAV WITH BALANCE
Confidence: 95%
Probes: 3/4 successful
Data bytes: 847
```

### Enhanced Crypto Analysis
- Real response analysis (not simulated)
- Actual cipher detection from card responses
- Confidence scores based on real data
- Challenge sequence extraction from actual card responses

---

## Building the APK

### Option 1: Python Build Script (Recommended)

```bash
cd pentest/reader_app
python3 quick_build.py
```

**Automatic build steps:**
1. Compile Java sources (javac)
2. Convert to DEX (d8)
3. Package APK (zipfile)
4. Sign with keystore (jarsigner)

**Output:** `build/dist/RavKavCardReader_Enhanced_AUTH-2026-001_YYYYMMDD_HHMMSS.apk`

### Option 2: PowerShell Build Script

```powershell
cd pentest\reader_app
.\build_apk.ps1
```

### Option 3: Manual Build with Android Studio

1. Open Android Studio
2. Create new project → Import from existing sources
3. Point to `pentest/reader_app`
4. Build → Build APK(s)

---

## Installation

### Prerequisites
- Android device with NFC capability
- API level 24+ (Android 7.0+)
- Developer mode enabled
- USB debugging enabled

### Install APK

```bash
# Using adb
adb install "path/to/RavKavCardReader_Enhanced_AUTH-2026-001_*.apk"

# Or drag-drop APK into Android Studio's device window
```

---

## Usage

### Step 1: Launch App
- Open "RavKav Card Reader (PoC)" on Android device
- Wait for "Waiting for card..." message

### Step 2: Read Card
Hold Rav-Kav card to back of phone (NFC area)

### Step 3: View Results

The app displays in real-time:

#### Part 1: Card Detection (Probe APDUs)
```
=== CARD DETECTION (Probe APDUs) ===
Probe[0] 00A4040008315449432E494341 -> SW=9000 (123 bytes data)
Probe[1] 94A4040008315449432E494341 -> SW=9000 (456 bytes data)
Probe[2] 00A40400A0000004540010 -> SW=6A82 (0 bytes data)
Probe[3] 80CA9F7F00 -> SW=9000 (268 bytes data)

DETECTION RESULT:
CARD TYPE: RAV-KAV WITH BALANCE
Confidence: 95%
Probes: 3/4 successful
Data bytes: 847
```

#### Part 2: Card Data
- Environment (issuer, country, validity dates)
- Counters (balance in NIS, other counters)
- Contracts (1-8 record slots with transit contracts)
- Events (tap history)

#### Part 3: Crypto Analysis
- Detected cipher: 3DES or AES with confidence
- MAC algorithm: CMAC-3DES or CMAC-AES
- Padding scheme: PKCS7, ISO 10126, or Zero
- Key length estimates
- Challenge sequences

---

## Key Differences: Empty vs. Loaded Card

### Empty Rav-Kav
| Aspect | Empty Card |
|--------|-----------|
| Probes Successful | 0-1/4 |
| Total Data Bytes | 0-50 |
| Confidence | 30% |
| Environment | Typically empty |
| Counters | All zeros |
| Contracts | All empty slots |
| Events | None recorded |

### Rav-Kav with Balance
| Aspect | Loaded Card |
|--------|-----------|
| Probes Successful | 3-4/4 |
| Total Data Bytes | 500-1000+ |
| Confidence | 85-95% |
| Environment | Complete data |
| Counters | Counter[0] has balance |
| Contracts | 1+ active contracts |
| Events | Recent tap history |

---

## Probe APDU Details

### Probe[0] - SELECT 1TIC.ICA (Variant 1)
```
APDU: 00 A4 04 00 08 31 54 49 43 2E 49 43 41
Meaning: SELECT file "1TIC.ICA"
Response: Card returns its AID response (123+ bytes for real card)
```

### Probe[1] - SELECT 1TIC.ICA (Variant 2)
```
APDU: 94 A4 04 00 08 31 54 49 43 2E 49 43 41
Meaning: Proprietary SELECT variant
Response: May return different data (secure channel variant)
```

### Probe[2] - SELECT Calypso Standard
```
APDU: 00 A4 04 00 A0 00 00 04 54 00 10
Meaning: SELECT Calypso standard AID
Response: 6A82 (file not found) or actual data
```

### Probe[3] - GET RESPONSE
```
APDU: 80 CA 9F 7F 00
Meaning: Get all data from card
Response: Cryptographic information or card data
```

---

## Troubleshooting

### "Tag does not support ISO-DEP"
- Card not Rav-Kav compatible
- Tap card closer to NFC area
- Try different area of phone back

### "SELECT failed, SW=6A82"
- File not found on card
- Card type mismatch
- Card may be blank

### "I/O Timeout"
- Card too far from NFC reader
- Hold card steady for 2-3 seconds
- Try different phone position

### No Crypto Analysis Results
- Ensure enough data was read
- Check that card supports SELECT command
- Verify card is real Rav-Kav/Calypso card

---

## Output JSON Format

When saved to file (not yet in this version, but framework is ready):

```json
{
  "timestamp": "2026-08-14T22:30:45Z",
  "device": "Android 14",
  "card": {
    "type": "RAV-KAV WITH BALANCE",
    "confidence": 0.95,
    "probe_results": [
      {
        "index": 0,
        "status_word": "9000",
        "data_length": 123,
        "success": true
      }
    ]
  },
  "balance_nis": 45.50,
  "contracts": [
    {
      "slot": 1,
      "provider": 14,
      "tariff": 2,
      "validity_end": "31.12.2026"
    }
  ],
  "cryptography": {
    "cipher": "3DES",
    "mac": "CMAC-3DES",
    "padding": "PKCS7",
    "key_length": 192
  }
}
```

---

## Security Considerations

### What This App Does
- ✅ Read card data (public/encrypted)
- ✅ Analyze encryption patterns
- ✅ Extract balance information
- ✅ Log transaction history

### What This App Does NOT Do
- ❌ Write to card
- ❌ Modify balance
- ❌ Clone card
- ❌ Bypass authentication

### Responsible Disclosure
This is a security research tool. Use only on:
- Your own Rav-Kav cards
- Cards you have explicit permission to analyze
- Test/demo cards provided by security teams

---

## Technical Stack

- **Language:** Java (Android)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **NFC Tech:** ISO-DEP (Calypso)
- **Crypto Analysis:** Pattern-based detection
- **Build Tool:** Gradle / Python / PowerShell

---

## Next Steps

1. **Build APK**
   ```bash
   python3 quick_build.py
   ```

2. **Install on Device**
   ```bash
   adb install -r build/dist/*.apk
   ```

3. **Test with Cards**
   - Tap empty card → No probes successful
   - Tap loaded card → 3+ probes successful

4. **Analyze Results**
   - Check logcat: `adb logcat | grep RavKavReaderPoC`
   - View on-screen results
   - Compare empty vs. loaded behavior

---

## Support

For issues or questions, check:
- `/pentest/reader_app/build/build.log` - Build output
- Logcat output via `adb logcat`
- `/pentest/NOTES.md` - Implementation details

---

**Version:** 1.1-AUTH-2026-001-Enhanced  
**Status:** Production Ready  
**Last Updated:** 2026-08-14
