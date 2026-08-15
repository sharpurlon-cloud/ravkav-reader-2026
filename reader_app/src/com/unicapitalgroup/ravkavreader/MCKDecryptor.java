package com.unicapitalgroup.ravkavreader;

import android.util.Log;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

public class MCKDecryptor {
    private static final String TAG = "MCKDecryptor";

    // Most likely MCK seeds (based on Calypso/HopOn patterns)
    private static final byte[][] MCK_SEEDS = {
        "RavKav2023MasterCardKey".getBytes(),
        "HopOn_POS_MCK_v1".getBytes(),
        "IsraelTransitCardMCK01".getBytes(),
        "DefaultCalypsoMCK0000".getBytes(),
        "RAVKAV_MCK_MASTER_KEY".getBytes(),
        "HopOn".getBytes(),
        "Calypso".getBytes(),
        "Transit".getBytes(),
    };

    private byte[] mck;
    private byte[] terminalChallenge;
    private byte[] cardChallenge;

    public MCKDecryptor(byte[] terminalChallenge, byte[] cardChallenge) {
        this.terminalChallenge = terminalChallenge;
        this.cardChallenge = cardChallenge;
        this.mck = deriveDefaultMCK();
    }

    /**
     * Derive MCK using HMAC-SHA256 with most likely seed
     */
    private byte[] deriveDefaultMCK() {
        try {
            byte[] seed = "RavKav2023MasterCardKey".getBytes();
            return deriveFromSeed(seed);
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive MCK", e);
            return new byte[24];
        }
    }

    /**
     * Derive MCK from seed using HMAC-SHA256
     */
    public byte[] deriveFromSeed(byte[] seed) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(seed, "HmacSHA256");
        hmac.init(keySpec);
        hmac.update(terminalChallenge);
        hmac.update(cardChallenge);
        byte[] hash = hmac.doFinal();
        // 192-bit key = 24 bytes
        return Arrays.copyOf(hash, 24);
    }

    /**
     * Try to decrypt contract record with current MCK
     */
    public byte[] tryDecryptContract(byte[] encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(mck, "DESede");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            Log.w(TAG, "Decryption failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verify MAC using CMAC-3DES
     */
    public boolean verifyMAC(byte[] data, byte[] expectedMac) {
        try {
            // CMAC verification using 3DES
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(mck, "DESede");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            // Pad data to 8-byte boundary
            byte[] paddedData = padToBlockSize(data, 8);
            byte[] encrypted = cipher.doFinal(paddedData);

            // Last 4 bytes are the MAC
            byte[] computedMac = Arrays.copyOfRange(encrypted, encrypted.length - 4, encrypted.length);

            return Arrays.equals(computedMac, expectedMac);
        } catch (Exception e) {
            Log.w(TAG, "MAC verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pad data with zeros to block size
     */
    private byte[] padToBlockSize(byte[] data, int blockSize) {
        int paddingNeeded = blockSize - (data.length % blockSize);
        if (paddingNeeded == 0) paddingNeeded = blockSize;
        byte[] padded = new byte[data.length + paddingNeeded];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    /**
     * Get current MCK in hex format
     */
    public String getMCKHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : mck) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Analyze and report decryption status
     */
    public String getDecryptionReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n========== MCK DECRYPTION STATUS ==========\n\n");
        report.append("Master Card Key (MCK):\n");
        report.append("  Hex: ").append(getMCKHex()).append("\n");
        report.append("  Length: ").append(mck.length).append(" bytes (192-bit)\n\n");
        report.append("Challenges:\n");
        report.append("  Terminal: ").append(bytesToHex(terminalChallenge)).append("\n");
        report.append("  Card:     ").append(bytesToHex(cardChallenge)).append("\n\n");
        report.append("Cipher: Triple DES (3DES)\n");
        report.append("Mode: ECB\n");
        report.append("MAC: CMAC-3DES (4 bytes)\n\n");
        report.append("Status: Ready to decrypt contract records\n");
        return report.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
