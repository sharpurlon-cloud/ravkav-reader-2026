# RavKav Card Reader - Complete Testing Guide
**Version:** 1.1-AUTH-2026-001-Enhanced  
**Date:** 2026-08-14  
**Status:** Ready for Field Testing

---

## 📋 Overview

This guide provides step-by-step instructions for testing the enhanced Rav-Kav card reader with real NFC cards. The app tests whether a card is empty or has balance by executing real Probe APDUs.

### Expected Differences:
- **Empty Card:** 0/4 probes successful → "UNKNOWN / BLANK" (30% confidence)
- **Loaded Card:** 3-4/4 probes successful → "RAV-KAV WITH BALANCE" (95% confidence)

---

## 🔧 Prerequisites

### Hardware
- Android device with NFC capability (Android 7.0+)
- USB cable for ADB connection
- 2-4 Rav-Kav cards (mix of empty and loaded)

### Software Setup
```bash
# Enable Developer Mode on Android device:
# Settings → About Phone → Build Number (tap 7 times)
# Settings → Developer Options → Enable "USB Debugging"

# Connect via ADB and verify
adb devices
```

---

## 📦 Installation

### Step 1: Uninstall Previous Version (if any)
```bash
adb shell su -c "pm uninstall com.unicapitalgroup.ravkavreader"
```

### Step 2: Install Enhanced APK
```bash
adb install "pentest/reader_app/RavKavCardReader_Enhanced_AUTH-2026-001.apk"
```

### Step 3: Verify Installation
```bash
adb shell pm list packages | grep ravkav
# Should output: package:com.unicapitalgroup.ravkavreader
```

### Step 4: Launch Application
```bash
adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity
```

---

## 🧪 Test Procedure

### TEST 1: Empty Card (5 minutes)

**Setup:**
1. Launch the app on your Android device
2. You should see "Waiting for card..." message
3. Prepare an empty/unused Rav-Kav card

**Execution:**
```bash
# Terminal 1: Start logcat monitoring
adb logcat -s "RavKavReaderPoC" -v threadtime &

# In terminal 2 or manually on device:
# Hold the empty card to the NFC area of your phone (usually back top corner)
# Keep it there for 2-3 seconds
# Wait for results to display
```

**Expected Output (on device and in logcat):**
```
Probe[0] SELECT 1TIC.ICA (00 A4 04 00 08...) → SW=6A82 (0 bytes)
Probe[1] SELECT 1TIC.ICA (94 A4 04 00 08...) → SW=6A82 (0 bytes)
Probe[2] SELECT Calypso (00 A4 04 00 A0...) → SW=6A82 (0 bytes)
Probe[3] GET RESPONSE (80 CA 9F 7F 00)     → SW=6A82 (0 bytes)

═══════════════════════════════════════════════════════════
CARD TYPE: UNKNOWN / BLANK
Confidence: 30%
Probes Successful: 0/4
Total Data Bytes: 0
═══════════════════════════════════════════════════════════
```

**Recording Results:**
- Screenshot the results
- Note the timestamp from logcat
- Save to: `card_1_empty_results.txt`

---

### TEST 2: Loaded Card (5 minutes)

**Setup:**
1. Keep the app running
2. Prepare a Rav-Kav card with balance
3. Make sure NFC is still enabled

**Execution:**
```bash
# Continue monitoring logcat from TEST 1
# Hold the loaded card to the NFC area of your phone
# Keep it there for 2-3 seconds
# Wait for results to display
```

**Expected Output (on device and in logcat):**
```
Probe[0] SELECT 1TIC.ICA (00 A4 04 00 08...) → SW=9000 (123 bytes)
Probe[1] SELECT 1TIC.ICA (94 A4 04 00 08...) → SW=9000 (456 bytes)
Probe[2] SELECT Calypso (00 A4 04 00 A0...) → SW=6A82 (0 bytes)
Probe[3] GET RESPONSE (80 CA 9F 7F 00)     → SW=9000 (268 bytes)

═══════════════════════════════════════════════════════════
CARD TYPE: RAV-KAV WITH BALANCE
Confidence: 95%
Probes Successful: 3/4
Total Data Bytes: 847

ENVIRONMENT:
  EnvApplicationNo = 12345678
  EnvEndDate = 31.12.2026

COUNTERS:
  counter[0] = 4550 (balance = 45.50 NIS)
  counter[1] = 42
  counter[2] = 0
  ...

CONTRACTS:
  Contract[0]: Bus Line 1 (expires 15.08.2026)
  ...
═══════════════════════════════════════════════════════════
```

**Recording Results:**
- Screenshot the results
- Note the timestamp from logcat
- Note the balance amount if visible
- Save to: `card_2_loaded_results.txt`

---

### TEST 3 & 4: Additional Cards (Optional)

Repeat TEST 1 or TEST 2 format for cards 3 and 4, recording results similarly.

**File naming:** `card_3_results.txt`, `card_4_results.txt`

---

## 📊 Logcat Extraction

### Extract All Results
```bash
# Get all RavKav-related logs
adb logcat -s "RavKavReaderPoC" -d > card_test_results_full.txt

# Get last N minutes of logs
adb logcat -s "RavKavReaderPoC" -d | tail -200 > card_test_results_recent.txt

# Get specific timeframe (example: last 30 minutes)
adb logcat -s "RavKavReaderPoC" -d | grep "08:14:" > card_test_results_timed.txt
```

### Clear Logcat Before Testing
```bash
adb logcat -c
```

---

## 📝 Result Comparison Template

### Quick Reference Table
```
╔════════════════════╦═══════════════════╦════════════════════════╗
║ Metric             ║ Empty Card (Card 1)║ Loaded Card (Card 2)   ║
╠════════════════════╬═══════════════════╬════════════════════════╣
║ Probes Successful  ║ 0/4               ║ 3-4/4                  ║
║ Detection Result   ║ UNKNOWN / BLANK   ║ RAV-KAV WITH BALANCE   ║
║ Confidence         ║ 30%               ║ 95%                    ║
║ Total Data Bytes   ║ 0                 ║ 500-1000+              ║
║ Balance Shown      ║ (none)            ║ XX.XX NIS              ║
║ Contracts          ║ All empty         ║ 1+ active              ║
║ Events/Transactions║ None              ║ Tap history present    ║
╚════════════════════╩═══════════════════╩════════════════════════╝
```

---

## 🔍 Probe Details

### What Each Probe Does

**Probe[0]: Standard Rav-Kav Selection**
```
Command:  00 A4 04 00 08 31 54 49 43 2E 49 43 41
Purpose:  SELECT "1TIC.ICA" (Rav-Kav standard applet)
Success:  Returns SW=9000 if card has applet
Failure:  Returns SW=6A82 if file not found
```

**Probe[1]: Alternative Rav-Kav Selection**
```
Command:  94 A4 04 00 08 31 54 49 43 2E 49 43 41
Purpose:  SELECT "1TIC.ICA" with different CLA byte (94)
Success:  Returns SW=9000 if card supports variant
Failure:  Returns SW=6A82 if variant not supported
```

**Probe[2]: Calypso Standard Selection**
```
Command:  00 A4 04 00 A0 00 00 04 54 00 10
Purpose:  SELECT Calypso standard AID
Success:  Returns SW=9000 if card is Calypso-based
Failure:  Returns SW=6A82 if not Calypso
```

**Probe[3]: Encryption Probe**
```
Command:  80 CA 9F 7F 00
Purpose:  GET RESPONSE for encryption status
Success:  Returns encryption data (SW=9000)
Failure:  Returns SW=6A82
```

---

## ✅ Success Criteria

### Pass Conditions:
- [ ] Empty card shows 0/4 successful probes
- [ ] Loaded card shows 3-4/4 successful probes
- [ ] Detection results clearly differ between cards
- [ ] Confidence scores are significantly different (30% vs 95%)
- [ ] Loaded card displays balance and/or contract information
- [ ] All test results reproducible across multiple tests

### Fail Conditions:
- [ ] All cards show same results regardless of content
- [ ] No distinction between empty and loaded cards
- [ ] Logcat shows errors or exceptions
- [ ] App crashes during card detection
- [ ] Confidence scores don't change between tests

---

## 🐛 Troubleshooting

### Issue: "Tag does not support ISO-DEP"
**Cause:** Card is not NFC-compatible or not properly positioned  
**Solution:** 
- Try repositioning card (usually back top-right area)
- Try different card
- Check NFC is enabled in phone settings

### Issue: All probes fail even on loaded card
**Cause:** Card may be damaged, encrypted, or not Rav-Kav compatible  
**Solution:**
- Try different card
- Ensure card is genuine Rav-Kav
- Check card contact points are clean

### Issue: Can't see app output
**Cause:** Output scrolled off screen  
**Solution:**
```bash
# Check logcat
adb logcat | grep "RavKavReaderPoC"
```

### Issue: Installation fails
**Cause:** Previous version still installed  
**Solution:**
```bash
adb uninstall com.unicapitalgroup.ravkavreader
adb install RavKavCardReader_Enhanced_AUTH-2026-001.apk
```

### Issue: ADB not found
**Cause:** Android SDK Platform Tools not installed  
**Solution:**
- Install Android SDK Platform Tools
- Add to system PATH
- Verify: `adb version`

---

## 📋 Test Report Template

Create a file: `test_report_[DATE].txt`

```
═══════════════════════════════════════════════════════════
RAV-KAV CARD READER - TEST REPORT
═══════════════════════════════════════════════════════════

Test Date: [DATE]
Tester: [NAME]
Device: [PHONE MODEL]
Android Version: [VERSION]
App Version: 1.1-AUTH-2026-001-Enhanced
APK: RavKavCardReader_Enhanced_AUTH-2026-001.apk

═══════════════════════════════════════════════════════════
TEST ENVIRONMENT
═══════════════════════════════════════════════════════════

NFC Enabled: [YES/NO]
Cards Tested: [NUMBER]
Card Types: [LIST]
Test Location: [LOCATION]

═══════════════════════════════════════════════════════════
TEST RESULTS SUMMARY
═══════════════════════════════════════════════════════════

Card 1 (Empty):
  - Probes Successful: 0/4
  - Detection: UNKNOWN / BLANK
  - Confidence: 30%
  - Data Bytes: 0
  - Status: ✓ PASS

Card 2 (Loaded):
  - Probes Successful: 3/4
  - Detection: RAV-KAV WITH BALANCE
  - Confidence: 95%
  - Data Bytes: 847
  - Balance: 45.50 NIS
  - Status: ✓ PASS

Card 3:
  - [RESULTS]

Card 4:
  - [RESULTS]

═══════════════════════════════════════════════════════════
OVERALL RESULT: ✓ PASS / ✗ FAIL
═══════════════════════════════════════════════════════════

Key Findings:
- Clear distinction between empty and loaded cards
- Probe-based detection working correctly
- Confidence scores match expected ranges
- No errors or crashes observed

Issues Encountered:
- [None / List any issues]

Conclusion:
[Summary of testing experience]

═══════════════════════════════════════════════════════════
```

---

## 📱 Mobile App Screenshots to Capture

1. **App Launch Screen:** "Waiting for card..." message
2. **Empty Card Result:** 0/4 probes screen
3. **Loaded Card Result:** 3/4 probes + balance screen
4. **Error Screen:** (if any occur)
5. **Full Output Screen:** Scroll down to show all data

---

## ⏱️ Testing Timeline

| Step | Task | Est. Time | Notes |
|------|------|-----------|-------|
| 1 | Setup & Prerequisites | 5 min | Enable developer mode, install ADB |
| 2 | Install APK | 2 min | Run adb install command |
| 3 | Verify Installation | 2 min | Check logcat |
| 4 | Test Empty Card | 5 min | Hold card, capture results |
| 5 | Test Loaded Card | 5 min | Hold card, capture results |
| 6 | Compare Results | 5 min | Document differences |
| 7 | Extract Logcat | 3 min | Save full logs |
| 8 | Create Report | 5 min | Compile findings |
| **Total** | | **32 min** | Complete testing cycle |

---

## 🎯 Success Indicators

Your testing is successful if:
1. ✅ App installs without errors
2. ✅ Empty card detection works (shows 0/4 probes)
3. ✅ Loaded card detection works (shows 3+/4 probes)
4. ✅ Results differ clearly between card types
5. ✅ Confidence scores match expectations (30% vs 95%)
6. ✅ No crashes or exceptions in logcat
7. ✅ You can reproduce results consistently

---

## 📞 Support

For detailed documentation, see:
- `ENHANCED_BUILD_README.md` - Complete technical guide
- `BUILD_RESULTS.json` - Machine-readable build info
- `QUICK_START.txt` - Quick reference

---

**Good luck with testing!** 🚀
