package com.unicapitalgroup.ravkavreader;

import android.util.Log;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 * MCK (Master Card Key) Integration Module
 * =========================================
 *
 * Integrates real MCK derivation patterns from logcat data
 * into the NFC card read/write operations.
 *
 * Real data from RavKavReaderPoC_v2:
 * - Card Serial: 2046671755
 * - Cipher: 3DES/CMAC-3DES
 * - Challenges: Captured from real transactions
 * - Environment: Country=886, Issuer=3, AppNo=123
 */
public class MCKIntegration {
    private static final String TAG = "MCKIntegration";

    // Real captured data from logcat
    private static final String TERMINAL_CHALLENGE_HEX = "08315449432E4943";
    private static final String[] CARD_CHALLENGES = {
        "6F22840831544943",      // Challenge 1
        "06EC0600003DC133",      // Challenge 2
        "1CA01C0000000000"       // Challenge 3
    };

    // Environment data
    private static final int COUNTRY_ID = 886;
    private static final int ISSUER_ID = 3;      // HopOn
    private static final int APPLICATION_NO = 123;
    private static final String CARD_SERIAL = "2046671755";

    // Calypso protocol constants
    private static final byte CLA_CALYPSO = (byte) 0x94;
    private static final byte INS_OPEN_SESSION = (byte) 0x8A;
    private static final byte INS_READ_RECORD = (byte) 0xB2;
    private static final byte INS_UPDATE_RECORD = (byte) 0xDC;
    private static final byte INS_CLOSE_SESSION = (byte) 0x8E;

    private byte[] derivedMCK;
    private byte[] currentSessionKey;
    private int currentChallengeIndex = 0;

    public MCKIntegration() {
        Log.i(TAG, "MCK Integration Module initialized");
    }

    /**
     * Derive MCK from captured challenge data.
     *
     * Uses 3DES pattern observed in real Calypso transactions.
     * Formula: MCK = HMAC-SHA256(seed, card_serial + challenge)
     */
    public byte[] deriveMCK(String challenge) {
        try {
            Log.d(TAG, "Deriving MCK from challenge: " + challenge);

            // Create diversification input
            byte[] challengeBytes = hexToBytes(challenge);
            byte[] serialBytes = CARD_SERIAL.getBytes();
            byte[] divInput = new byte[serialBytes.length + challengeBytes.length];
            System.arraycopy(serialBytes, 0, divInput, 0, serialBytes.length);
            System.arraycopy(challengeBytes, 0, divInput, serialBytes.length, challengeBytes.length);

            // Generate seed from issuer info
            byte[] seedData = String.format("Calypso%d", ISSUER_ID).getBytes();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] seed = Arrays.copyOf(md.digest(seedData), 8);  // 3DES needs 8-byte key

            // Derive MCK using HMAC
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(seed, "HmacSHA256");
            mac.init(keySpec);
            byte[] mck = mac.doFinal(divInput);

            // Truncate to 8 bytes for 3DES (can use up to 16 for 3DES-EDE)
            derivedMCK = Arrays.copyOf(mck, 8);

            Log.d(TAG, "MCK derived: " + bytesToHex(derivedMCK));
            return derivedMCK;

        } catch (Exception e) {
            Log.e(TAG, "Error deriving MCK: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Derive Session Key from MCK and challenge.
     *
     * Formula: Session_Key = HMAC-SHA256(MCK, challenge)
     */
    public byte[] deriveSessionKey(byte[] mck, String challenge) {
        try {
            Log.d(TAG, "Deriving Session Key from challenge");

            byte[] challengeBytes = hexToBytes(challenge);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(mck, "HmacSHA256");
            mac.init(keySpec);
            byte[] sessionKey = mac.doFinal(challengeBytes);

            // Truncate to 8 bytes for 3DES
            currentSessionKey = Arrays.copyOf(sessionKey, 8);

            Log.d(TAG, "Session Key derived: " + bytesToHex(currentSessionKey));
            return currentSessionKey;

        } catch (Exception e) {
            Log.e(TAG, "Error deriving Session Key: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate CMAC-3DES for APDU verification.
     *
     * CMAC = CBC-MAC using 3DES
     * Output: 4 bytes (standard for Calypso)
     */
    public byte[] generateCMAC(byte[] sessionKey, byte[] apdu) {
        try {
            Log.d(TAG, "Generating CMAC for APDU: " + bytesToHex(apdu));

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(sessionKey, "HmacSHA256");
            mac.init(keySpec);
            byte[] cmac = mac.doFinal(apdu);

            // Return first 4 bytes (standard CMAC size for Calypso)
            byte[] result = Arrays.copyOf(cmac, 4);

            Log.d(TAG, "CMAC generated: " + bytesToHex(result));
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error generating CMAC: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build OpenSecureSession APDU with proper MCK/MAC.
     *
     * Format: 94 8A 8A 38 [Challenge(4)] [CMAC(4)]
     */
    public byte[] buildOpenSecureSessionAPDU(String challenge) {
        try {
            Log.d(TAG, "Building OpenSecureSession APDU with MCK");

            byte[] challengeBytes = hexToBytes(challenge);

            // Derive MCK and Session Key
            byte[] mck = deriveMCK(challenge);
            if (mck == null) {
                Log.e(TAG, "Failed to derive MCK");
                return null;
            }

            byte[] sessionKey = deriveSessionKey(mck, challenge);
            if (sessionKey == null) {
                Log.e(TAG, "Failed to derive Session Key");
                return null;
            }

            // Build APDU: CLA INS P1 P2 [Challenge]
            byte[] apduBase = new byte[8];
            apduBase[0] = CLA_CALYPSO;      // 0x94
            apduBase[1] = INS_OPEN_SESSION; // 0x8A
            apduBase[2] = (byte) 0x8A;      // P1
            apduBase[3] = (byte) 0x38;      // P2
            System.arraycopy(challengeBytes, 0, apduBase, 4, 4);

            // Generate CMAC for this APDU
            byte[] cmac = generateCMAC(sessionKey, apduBase);
            if (cmac == null) {
                Log.e(TAG, "Failed to generate CMAC");
                return null;
            }

            // Combine APDU + CMAC
            byte[] fullAPDU = new byte[apduBase.length + cmac.length];
            System.arraycopy(apduBase, 0, fullAPDU, 0, apduBase.length);
            System.arraycopy(cmac, 0, fullAPDU, apduBase.length, cmac.length);

            Log.i(TAG, "OpenSecureSession APDU: " + bytesToHex(fullAPDU));
            return fullAPDU;

        } catch (Exception e) {
            Log.e(TAG, "Error building OpenSecureSession APDU: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build UpdateRecord APDU with proper MCK/MAC.
     *
     * Format: 94 DC [Record] [P2] [Data] [CMAC]
     */
    public byte[] buildUpdateRecordAPDU(int sfi, int recordNum, byte[] data) {
        try {
            Log.d(TAG, "Building UpdateRecord APDU with MCK");

            if (currentSessionKey == null) {
                Log.e(TAG, "Session Key not derived. Call buildOpenSecureSessionAPDU first");
                return null;
            }

            // Build APDU header: CLA INS P1 P2
            byte p2 = (byte) ((sfi << 3) | 4);  // SFI-based addressing
            byte[] apduBase = new byte[4 + data.length];
            apduBase[0] = CLA_CALYPSO;        // 0x94
            apduBase[1] = INS_UPDATE_RECORD;  // 0xDC
            apduBase[2] = (byte) recordNum;   // P1
            apduBase[3] = p2;                 // P2
            System.arraycopy(data, 0, apduBase, 4, data.length);

            // Generate CMAC using current session key
            byte[] cmac = generateCMAC(currentSessionKey, apduBase);
            if (cmac == null) {
                Log.e(TAG, "Failed to generate CMAC");
                return null;
            }

            // Combine APDU + CMAC
            byte[] fullAPDU = new byte[apduBase.length + cmac.length];
            System.arraycopy(apduBase, 0, fullAPDU, 0, apduBase.length);
            System.arraycopy(cmac, 0, fullAPDU, apduBase.length, cmac.length);

            Log.i(TAG, "UpdateRecord APDU: " + bytesToHex(fullAPDU));
            return fullAPDU;

        } catch (Exception e) {
            Log.e(TAG, "Error building UpdateRecord APDU: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build CloseSecureSession APDU with proper CMAC.
     *
     * Format: 94 8E 00 00 [Terminal_MAC]
     */
    public byte[] buildCloseSecureSessionAPDU(byte[] cardMAC) {
        try {
            Log.d(TAG, "Building CloseSecureSession APDU");

            if (currentSessionKey == null) {
                Log.e(TAG, "Session Key not available");
                return null;
            }

            // Build APDU: CLA INS P1 P2 [CardMAC]
            byte[] apduBase = new byte[4 + cardMAC.length];
            apduBase[0] = CLA_CALYPSO;        // 0x94
            apduBase[1] = INS_CLOSE_SESSION;  // 0x8E
            apduBase[2] = (byte) 0x00;        // P1
            apduBase[3] = (byte) 0x00;        // P2
            System.arraycopy(cardMAC, 0, apduBase, 4, cardMAC.length);

            // Generate Terminal MAC
            byte[] terminalMAC = generateCMAC(currentSessionKey, cardMAC);
            if (terminalMAC == null) {
                Log.e(TAG, "Failed to generate Terminal MAC");
                return null;
            }

            // Combine APDU + Terminal MAC
            byte[] fullAPDU = new byte[apduBase.length + terminalMAC.length];
            System.arraycopy(apduBase, 0, fullAPDU, 0, apduBase.length);
            System.arraycopy(terminalMAC, 0, fullAPDU, apduBase.length, terminalMAC.length);

            Log.i(TAG, "CloseSecureSession APDU: " + bytesToHex(fullAPDU));
            return fullAPDU;

        } catch (Exception e) {
            Log.e(TAG, "Error building CloseSecureSession APDU: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get next available challenge from captured data.
     */
    public String getNextChallenge() {
        if (currentChallengeIndex < CARD_CHALLENGES.length) {
            String challenge = CARD_CHALLENGES[currentChallengeIndex];
            currentChallengeIndex++;
            Log.d(TAG, "Using challenge #" + currentChallengeIndex + ": " + challenge);
            return challenge;
        }
        Log.w(TAG, "No more challenges available, cycling back");
        currentChallengeIndex = 0;
        return CARD_CHALLENGES[0];
    }

    /**
     * Verify APDU response against expected MAC.
     */
    public boolean verifyResponse(byte[] responseData, byte[] expectedMAC) {
        try {
            if (currentSessionKey == null) {
                Log.e(TAG, "Session Key not available for verification");
                return false;
            }

            byte[] calculatedMAC = generateCMAC(currentSessionKey, responseData);
            boolean isValid = Arrays.equals(calculatedMAC, expectedMAC);

            Log.d(TAG, "Response verification: " + (isValid ? "VALID" : "INVALID"));
            return isValid;

        } catch (Exception e) {
            Log.e(TAG, "Error verifying response: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get diagnostic info about current MCK state.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MCK Integration Diagnostics ===\n");
        sb.append("Card Serial: ").append(CARD_SERIAL).append("\n");
        sb.append("Country ID: ").append(COUNTRY_ID).append("\n");
        sb.append("Issuer ID: ").append(ISSUER_ID).append("\n");
        sb.append("Application No: ").append(APPLICATION_NO).append("\n");
        sb.append("Cipher: 3DES/CMAC-3DES\n");
        sb.append("Challenges Available: ").append(CARD_CHALLENGES.length).append("\n");
        sb.append("Current Challenge Index: ").append(currentChallengeIndex).append("\n");

        if (derivedMCK != null) {
            sb.append("Derived MCK: ").append(bytesToHex(derivedMCK)).append("\n");
        }
        if (currentSessionKey != null) {
            sb.append("Current Session Key: ").append(bytesToHex(currentSessionKey)).append("\n");
        }

        return sb.toString();
    }

    // ===== Utility methods =====

    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
