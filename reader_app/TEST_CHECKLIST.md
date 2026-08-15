# RavKav Card Reader - Complete Test Checklist

## Pre-Test Preparation

### Environment Setup
- [ ] Android device available and charged
- [ ] USB cable ready and functioning
- [ ] Computer with ADB installed
- [ ] NFC-enabled Android device (7.0 or higher)
- [ ] 2-4 Rav-Kav cards (mix of empty and loaded)
- [ ] Quiet testing environment
- [ ] Adequate lighting to see screen clearly

### Device Configuration
- [ ] USB Debugging enabled in Developer Options
- [ ] NFC enabled on device
- [ ] USB connection recognized by computer
- [ ] `adb devices` shows device connected
- [ ] Device not in sleep/lock mode

### Files Verification
- [ ] APK file location: `pentest/reader_app/RavKavCardReader_Enhanced_AUTH-2026-001.apk`
- [ ] APK file size: ~24 KB
- [ ] APK file is readable (not corrupted)
- [ ] Logcat viewing method available (adb or Android Studio)

---

## Installation Phase

### Pre-Installation
- [ ] Device connected via USB
- [ ] ADB connection verified: `adb devices`
- [ ] Previous version uninstalled (if existed)
  ```bash
  adb shell pm uninstall com.unicapitalgroup.ravkavreader
  ```

### Installation
- [ ] APK installed successfully
  ```bash
  adb install RavKavCardReader_Enhanced_AUTH-2026-001.apk
  ```
- [ ] Installation output shows "Success"
- [ ] No "INSTALL_FAILED" errors
- [ ] App appears in device app drawer

### Post-Installation Verification
- [ ] App can be found: `adb shell pm list packages | grep ravkav`
- [ ] Expected output: `package:com.unicapitalgroup.ravkavreader`
- [ ] App launches successfully
  ```bash
  adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity
  ```

---

## Test 1: Empty Card

### Pre-Test Setup
- [ ] Logcat monitoring started
  ```bash
  adb logcat -s "RavKavReaderPoC" -v threadtime
  ```
- [ ] Logcat buffer cleared
  ```bash
  adb logcat -c
  ```
- [ ] App is running and showing "Waiting for card..."
- [ ] Empty card is available

### Test Execution
- [ ] Time recorded: ___________
- [ ] Empty card placed on NFC area
- [ ] Card held in place for 2-3 seconds
- [ ] Wait for app to process
- [ ] Results displayed on screen
- [ ] Screenshot taken of results
- [ ] Logcat shows probe execution

### Expected Results Verification
- [ ] Probe[0] shows status word: **6A82** ✓
- [ ] Probe[1] shows status word: **6A82** ✓
- [ ] Probe[2] shows status word: **6A82** ✓
- [ ] Probe[3] shows status word: **6A82** ✓
- [ ] Total probes successful: **0/4** ✓
- [ ] Card classification: **UNKNOWN / BLANK** ✓
- [ ] Confidence score: **30%** ✓
- [ ] Data bytes read: **0** ✓
- [ ] No exceptions or errors in logcat ✓

### Result Recording
- [ ] Screenshot saved: `card_1_empty_results.png`
- [ ] Logcat excerpt saved: `card_1_empty_logcat.txt`
- [ ] Test timestamp noted: ___________
- [ ] Notes added: _________________________________

### Status
- [ ] **PASS** - All expectations met
- [ ] **FAIL** - Some expectations not met (list below)
  - _________________________________

---

## Test 2: Loaded Card

### Pre-Test Setup
- [ ] Logcat still monitoring
- [ ] App still running
- [ ] Logcat buffer NOT cleared (to compare with previous test)
- [ ] Loaded card (with balance) is available

### Test Execution
- [ ] Time recorded: ___________
- [ ] Loaded card placed on NFC area
- [ ] Card held in place for 2-3 seconds
- [ ] Wait for app to process
- [ ] Results displayed on screen
- [ ] Screenshot taken of results
- [ ] Logcat shows probe execution
- [ ] Balance information visible (if shown)

### Expected Results Verification
- [ ] At least 3 probes successful ✓ (count: __/4)
- [ ] Status words show mix of **9000** and **6A82** ✓
- [ ] Data bytes read: **500+** ✓ (actual: ______)
- [ ] Card classification: **RAV-KAV WITH BALANCE** ✓
- [ ] Confidence score: **90-95%** ✓ (actual: ____%)
- [ ] Balance information present (if applicable) ✓
- [ ] Contract information present (if applicable) ✓
- [ ] No exceptions or errors in logcat ✓

### Result Recording
- [ ] Screenshot saved: `card_2_loaded_results.png`
- [ ] Logcat excerpt saved: `card_2_loaded_logcat.txt`
- [ ] Balance noted: _____________ NIS
- [ ] Contracts noted: _________________________________
- [ ] Test timestamp noted: ___________
- [ ] Notes added: _________________________________

### Status
- [ ] **PASS** - All expectations met
- [ ] **FAIL** - Some expectations not met (list below)
  - _________________________________

---

## Test 3: Additional Card (Optional)

### Pre-Test Setup
- [ ] Logcat monitoring continued
- [ ] App still running
- [ ] Third card available
- [ ] Card type noted: [ ] Empty [ ] Loaded [ ] Unknown

### Test Execution
- [ ] Time recorded: ___________
- [ ] Card placed on NFC area
- [ ] Results displayed
- [ ] Screenshot taken
- [ ] Logcat shows execution

### Results
- [ ] Probes successful: ___/4
- [ ] Detection result: _________________________________
- [ ] Confidence: _____%
- [ ] Data bytes: _______
- [ ] Status: [ ] PASS [ ] FAIL [ ] INCONCLUSIVE

---

## Test 4: Fourth Card (Optional)

### Pre-Test Setup
- [ ] Logcat monitoring continued
- [ ] App still running
- [ ] Fourth card available
- [ ] Card type noted: [ ] Empty [ ] Loaded [ ] Unknown

### Test Execution
- [ ] Time recorded: ___________
- [ ] Card placed on NFC area
- [ ] Results displayed
- [ ] Screenshot taken
- [ ] Logcat shows execution

### Results
- [ ] Probes successful: ___/4
- [ ] Detection result: _________________________________
- [ ] Confidence: _____%
- [ ] Data bytes: _______
- [ ] Status: [ ] PASS [ ] FAIL [ ] INCONCLUSIVE

---

## Post-Test Data Collection

### Logcat Extraction
- [ ] Full logcat exported
  ```bash
  adb logcat -s "RavKavReaderPoC" -d > card_test_results_full.txt
  ```
- [ ] File saved location: _________________________________
- [ ] File size: _____________ bytes
- [ ] Logcat contains all probes: ✓

### Evidence Collection
- [ ] All screenshots saved
- [ ] All logcat files saved
- [ ] Test notes compiled
- [ ] Test timestamps recorded
- [ ] Balance/contract info documented (if applicable)

### Data Organization
- [ ] Created folder: `test_results_[DATE]`
- [ ] All files organized
- [ ] README created with test summary
- [ ] JSON template filled with actual data

---

## Analysis & Comparison

### Empty vs. Loaded Comparison
- [ ] Probe success rate clearly different (0/4 vs 3+/4) ✓
- [ ] Detection results clearly different ✓
- [ ] Confidence scores significantly different (30% vs 95%) ✓
- [ ] Data bytes clearly different (0 vs 500+) ✓
- [ ] Distinction is obvious and repeatable ✓

### Quality Checks
- [ ] No app crashes observed ✓
- [ ] No exceptions in logcat ✓
- [ ] App responds consistently ✓
- [ ] Logcat output is clean ✓
- [ ] No timeouts or delays ✓

### Data Integrity
- [ ] All probe responses recorded correctly ✓
- [ ] Status words match expected patterns ✓
- [ ] Balance data matches card info (if known) ✓
- [ ] No data corruption observed ✓

---

## Final Verification

### Test Completion
- [ ] All planned tests executed
- [ ] All expected differences confirmed
- [ ] All data collected and organized
- [ ] No significant issues encountered
- [ ] Testing time: _______ minutes

### Success Criteria Met
- [ ] Empty card detection works (0/4 probes)
- [ ] Loaded card detection works (3+/4 probes)
- [ ] Clear distinction between card types
- [ ] Probe-based detection accurate
- [ ] Confidence scores reliable
- [ ] No false positives
- [ ] No false negatives
- [ ] Reproducible results

### Overall Assessment
- [ ] **PASS** - App ready for deployment
- [ ] **CONDITIONAL PASS** - Works with notes
- [ ] **FAIL** - Issues need resolution

### Issues Summary
- [ ] No issues encountered
- [ ] Issues encountered (list):
  1. _________________________________
  2. _________________________________
  3. _________________________________

### Recommendations
- [ ] Ready for production use
- [ ] Ready for production with fixes
- [ ] Needs further investigation
- [ ] Needs hardware testing with more cards

---

## Test Report Metadata

| Item | Value |
|------|-------|
| Test Date | _______________ |
| Tester Name | _______________ |
| Device Model | _______________ |
| Android Version | _______________ |
| App Version | 1.1-AUTH-2026-001-Enhanced |
| APK File | RavKavCardReader_Enhanced_AUTH-2026-001.apk |
| Total Duration | ___ minutes |
| Cards Tested | ___ |
| Tests Passed | ___ |
| Tests Failed | ___ |
| Overall Result | [ ] PASS [ ] FAIL |

---

## Sign-Off

**Tester Name:** ___________________________

**Signature:** _____________________________

**Date:** ___________________________

**Contact (if issues found):** ___________________________

---

## Additional Notes

```
[Use this space for any observations, issues, or additional findings]

_________________________________________________________________

_________________________________________________________________

_________________________________________________________________

_________________________________________________________________
```

---

## Appendix: Quick Command Reference

### ADB Commands Used
```bash
# Check connection
adb devices

# Uninstall
adb shell pm uninstall com.unicapitalgroup.ravkavreader

# Install
adb install RavKavCardReader_Enhanced_AUTH-2026-001.apk

# Launch
adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity

# View logcat
adb logcat -s "RavKavReaderPoC" -v threadtime

# Clear logcat
adb logcat -c

# Export logcat
adb logcat -s "RavKavReaderPoC" -d > results.txt
```

### Expected Probe Status Words
- **9000** = Success (data returned)
- **6A82** = File not found (failure)
- **6F00** = Technical problem
- **6100** = More data available

### Detection Classification
- **UNKNOWN / BLANK** = 0/4 probes successful (empty card)
- **RAV-KAV WITH BALANCE** = 3-4/4 probes successful (loaded card)
- **UNKNOWN / NO RESPONSE** = Card not responding
- **UNKNOWN / PARTIAL** = 1-2/4 probes successful (unclear)

---

**End of Checklist**

Good luck with your testing! 🚀
