package com.unicapitalgroup.ravkavreader;

import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Advanced Rav-Kav / Calypso encryption pattern analyzer
 * Detects cipher type, MAC algorithms, key lengths, and padding schemes
 * Part of AUTH-2026-001 security analysis
 *
 * Enhanced with real Probe APDU execution and actual response analysis
 */
public class CryptoAnalyzer {

    private static final String TAG = "CryptoAnalyzer";

    // Crypto signature patterns
    private static final byte[] MAGIC_3DES_ECB = {(byte) 0xA4, (byte) 0x84};
    private static final byte[] MAGIC_AES_ECB = {(byte) 0xA4, (byte) 0x85};

    // Probe APDU AID variations for Rav-Kav/Calypso detection
    private static final byte[] AID_CALYPSO_1 = hex("315449432E494341"); // "1TIC.ICA"
    private static final byte[] AID_CALYPSO_2 = hex("A0000004540010"); // Calypso standard
    private static final byte[] AID_MIFARE = hex("A0000003960000"); // Mifare
    private static final byte[] AID_GENERIC = hex("00A4040000"); // Generic SELECT

    private CipherInfo detectedCipher;
    private MACInfo detectedMAC;
    private ChallengeSequence terminalChallenge;
    private ChallengeSequence cardChallenge;
    private PaddingScheme detectedPadding;

    private Map<String, Integer> responsePatterns;
    private Map<String, Integer> cipherPatterns;
    private List<ProbeResult> probeResults;
    private CardDetectionResult detectionResult;

    public CryptoAnalyzer() {
        this.responsePatterns = new HashMap<>();
        this.cipherPatterns = new HashMap<>();
        this.probeResults = new ArrayList<>();
        this.detectionResult = new CardDetectionResult();
    }

    /**
     * Execute probe APDUs and detect card type
     */
    public void executeProbes(byte[] probeResponses[]) {
        probeResults.clear();

        for (int i = 0; i < probeResponses.length; i++) {
            byte[] response = probeResponses[i];
            ProbeResult probe = new ProbeResult();
            probe.index = i;
            probe.response = response;
            probe.isSuccess = response != null && response.length >= 2 &&
                    (response[response.length - 2] & 0xFF) == 0x90 &&
                    (response[response.length - 1] & 0xFF) == 0x00;
            probe.dataLength = probe.isSuccess ? response.length - 2 : 0;
            probe.statusWord = String.format("%02X%02X",
                    response[response.length - 2] & 0xFF,
                    response[response.length - 1] & 0xFF);

            probeResults.add(probe);

            // Log probe results
            Log.d(TAG, "Probe[" + i + "] SW=" + probe.statusWord +
                    " len=" + probe.dataLength + " success=" + probe.isSuccess);
        }

        // Analyze card type from probe results
        analyzeFromProbes();
    }

    /**
     * Analyze card type based on probe responses
     */
    private void analyzeFromProbes() {
        int successCount = 0;
        int dataBytes = 0;

        for (ProbeResult probe : probeResults) {
            if (probe.isSuccess) {
                successCount++;
                dataBytes += probe.dataLength;
            }
        }

        detectionResult.probesSuccessful = successCount;
        detectionResult.probesTotal = probeResults.size();
        detectionResult.totalDataBytes = dataBytes;

        if (successCount == 0) {
            detectionResult.cardType = "UNKNOWN / BLANK";
            detectionResult.confidence = 0.3;
        } else if (dataBytes > 500) {
            detectionResult.cardType = "RAV-KAV WITH BALANCE";
            detectionResult.confidence = 0.95;
        } else if (dataBytes > 100) {
            detectionResult.cardType = "RAV-KAV (Data present)";
            detectionResult.confidence = 0.85;
        } else {
            detectionResult.cardType = "RAV-KAV (Minimal data)";
            detectionResult.confidence = 0.70;
        }

        Log.i(TAG, "Card detected: " + detectionResult.cardType +
                " (Confidence: " + String.format("%.0f%%", detectionResult.confidence * 100) + ")");
    }

    /**
     * Main analysis entry point after reading a card
     */
    public AnalysisReport analyzeCard(byte[] apdus[], byte[] responses[]) {
        AnalysisReport report = new AnalysisReport();

        // 1. Detect cipher type from APDU patterns
        detectCipherType(apdus, responses);
        report.setCipherInfo(detectedCipher);

        // 2. Detect MAC algorithm
        detectMACAlgorithm(apdus, responses);
        report.setMacInfo(detectedMAC);

        // 3. Analyze challenge sequences
        extractChallenges(apdus, responses);
        report.setTerminalChallenge(terminalChallenge);
        report.setCardChallenge(cardChallenge);

        // 4. Detect padding scheme
        detectPaddingScheme(responses);
        report.setPaddingScheme(detectedPadding);

        // 5. Generate summary statistics
        report.setResponseStats(generateResponseStats(responses));

        return report;
    }

    /**
     * Analyze cipher type from APDU structure
     */
    private void detectCipherType(byte[][] apdus, byte[][] responses) {
        detectedCipher = new CipherInfo();

        for (int i = 0; i < apdus.length; i++) {
            byte[] apdu = apdus[i];
            byte[] response = responses[i];

            // Check P1/P2 for cipher hints
            if (apdu.length >= 4) {
                byte p1 = apdu[1];
                byte p2 = apdu[2];

                // Pattern matching for 3DES vs AES
                if ((p1 & 0xA0) == 0xA0) {
                    // Likely encrypted APDU
                    analyzeResponseLength(response);

                    // 3DES produces 8-byte blocks
                    if (response.length % 8 == 0 || (response.length - 2) % 8 == 0) {
                        detectedCipher.type = CipherType.TRIPLE_DES;
                        detectedCipher.blockSize = 8;
                        cipherPatterns.put("3DES_BLOCK_8", cipherPatterns.getOrDefault("3DES_BLOCK_8", 0) + 1);
                    }
                    // AES produces 16-byte blocks
                    else if (response.length % 16 == 0 || (response.length - 2) % 16 == 0) {
                        detectedCipher.type = CipherType.AES;
                        detectedCipher.blockSize = 16;
                        cipherPatterns.put("AES_BLOCK_16", cipherPatterns.getOrDefault("AES_BLOCK_16", 0) + 1);
                    }
                }
            }
        }

        // Default to 3DES if ambiguous (most common in Calypso)
        if (detectedCipher.type == null) {
            detectedCipher.type = CipherType.TRIPLE_DES;
            detectedCipher.blockSize = 8;
            detectedCipher.keyLength = 192; // 3x56-bit = 168 or 3x64-bit = 192
            detectedCipher.confidence = 0.6;
        } else {
            detectedCipher.confidence = 0.9;
        }

        // Infer key length
        if (detectedCipher.type == CipherType.TRIPLE_DES) {
            detectedCipher.keyLength = 192; // Common: 192-bit (3x64)
            detectedCipher.keyLengthAlt = 168; // Alternative: 168-bit (3x56)
        } else {
            detectedCipher.keyLength = 256; // AES-256
            detectedCipher.keyLengthAlt = 128; // AES-128
        }
    }

    /**
     * Detect MAC algorithm from response patterns
     */
    private void detectMACAlgorithm(byte[][] apdus, byte[][] responses) {
        detectedMAC = new MACInfo();

        // Analyze last bytes of responses for MAC signatures
        int mac4Count = 0, mac8Count = 0;

        for (byte[] response : responses) {
            if (response != null && response.length > 2) {
                byte[] data = Arrays.copyOf(response, response.length - 2);

                // Check if data ends with repeating pattern (MAC signature)
                if (data.length >= 4) {
                    byte[] lastFour = Arrays.copyOfRange(data, data.length - 4, data.length);
                    if (isValidMAC(lastFour)) {
                        mac4Count++;
                    }
                }

                if (data.length >= 8) {
                    byte[] lastEight = Arrays.copyOfRange(data, data.length - 8, data.length);
                    if (isValidMAC(lastEight)) {
                        mac8Count++;
                    }
                }
            }
        }

        // Determine MAC size
        if (mac8Count > mac4Count) {
            detectedMAC.macSize = 8;
            detectedMAC.type = MACType.CMAC_AES_8BYTE;
            detectedMAC.confidence = 0.75;
        } else if (mac4Count > 0) {
            detectedMAC.macSize = 4;
            detectedMAC.type = MACType.CMAC_3DES_4BYTE;
            detectedMAC.confidence = 0.80;
        } else {
            detectedMAC.macSize = 4; // Default
            detectedMAC.type = MACType.UNKNOWN;
            detectedMAC.confidence = 0.5;
        }

        // Infer algorithm based on detected cipher
        if (detectedCipher.type == CipherType.TRIPLE_DES) {
            detectedMAC.algorithm = "CMAC-3DES";
        } else {
            detectedMAC.algorithm = "CMAC-AES";
        }
    }

    /**
     * Extract and analyze challenge-response sequences
     */
    private void extractChallenges(byte[][] apdus, byte[][] responses) {
        terminalChallenge = new ChallengeSequence();
        cardChallenge = new ChallengeSequence();

        for (int i = 0; i < apdus.length; i++) {
            byte[] apdu = apdus[i];
            byte[] response = responses[i];

            // Look for challenge patterns in APDU (terminal challenge)
            if (apdu.length >= 8 && (apdu[0] & 0x80) != 0) {
                byte[] challenge = Arrays.copyOfRange(apdu, 4, Math.min(12, apdu.length));
                if (isLikelyChallengeData(challenge)) {
                    terminalChallenge.addChallenge(challenge);
                }
            }

            // Look for challenge patterns in response (card challenge)
            if (response != null && response.length >= 10) {
                byte[] data = Arrays.copyOf(response, response.length - 2);
                // Check first 8 bytes
                if (data.length >= 8) {
                    byte[] challenge = Arrays.copyOfRange(data, 0, 8);
                    if (isLikelyChallengeData(challenge)) {
                        cardChallenge.addChallenge(challenge);
                    }
                }
            }
        }

        terminalChallenge.analyzePattern();
        cardChallenge.analyzePattern();
    }

    /**
     * Detect padding scheme used in encryption
     */
    private void detectPaddingScheme(byte[][] responses) {
        detectedPadding = new PaddingScheme();

        int isoCount = 0, pkcs7Count = 0, zeroCount = 0;

        for (byte[] response : responses) {
            if (response != null && response.length > 2) {
                byte[] data = Arrays.copyOf(response, response.length - 2);

                // Check last byte for padding indicators
                if (data.length > 0) {
                    byte lastByte = data[data.length - 1];

                    // PKCS7: last byte value == padding count
                    if (lastByte > 0 && lastByte <= 16) {
                        if (checkPKCS7Padding(data)) {
                            pkcs7Count++;
                        }
                    }

                    // ISO 10126: last byte value == padding count
                    if (lastByte > 0 && lastByte <= 8) {
                        if (checkISO10126Padding(data)) {
                            isoCount++;
                        }
                    }

                    // Zero padding
                    if (lastByte == 0) {
                        zeroCount++;
                    }
                }
            }
        }

        if (pkcs7Count > isoCount && pkcs7Count > zeroCount) {
            detectedPadding.type = PaddingType.PKCS7;
            detectedPadding.confidence = 0.85;
        } else if (isoCount > pkcs7Count && isoCount > zeroCount) {
            detectedPadding.type = PaddingType.ISO10126;
            detectedPadding.confidence = 0.75;
        } else if (zeroCount > 0) {
            detectedPadding.type = PaddingType.ZERO;
            detectedPadding.confidence = 0.70;
        } else {
            detectedPadding.type = PaddingType.UNKNOWN;
            detectedPadding.confidence = 0.5;
        }
    }

    /**
     * Analyze APDU response length patterns
     */
    private void analyzeResponseLength(byte[] response) {
        if (response == null) return;

        int dataLen = response.length - 2; // Exclude SW bytes
        responsePatterns.put("len_" + dataLen, responsePatterns.getOrDefault("len_" + dataLen, 0) + 1);
    }

    /**
     * Verify if data looks like a valid MAC
     */
    private boolean isValidMAC(byte[] macBytes) {
        if (macBytes == null || macBytes.length < 4) return false;

        // MAC should have reasonable entropy (not all zeros or all same value)
        int uniqueBytes = 0;
        for (byte b : macBytes) {
            boolean found = false;
            for (byte b2 : macBytes) {
                if (b != b2) {
                    found = true;
                    break;
                }
            }
            if (found) uniqueBytes++;
        }

        return uniqueBytes >= 2; // At least 2 different byte values
    }

    /**
     * Check if data uses PKCS7 padding
     */
    private boolean checkPKCS7Padding(byte[] data) {
        if (data == null || data.length == 0) return false;

        byte lastByte = data[data.length - 1];
        if (lastByte <= 0 || lastByte > 16) return false;

        for (int i = 0; i < lastByte && i < data.length; i++) {
            if (data[data.length - 1 - i] != lastByte) return false;
        }
        return true;
    }

    /**
     * Check if data uses ISO 10126 padding
     */
    private boolean checkISO10126Padding(byte[] data) {
        if (data == null || data.length == 0) return false;

        byte lastByte = data[data.length - 1];
        if (lastByte <= 0 || lastByte > 8) return false;

        // ISO 10126: last byte is length, previous bytes are random
        // We can't fully verify without the original key, but structure is consistent
        return lastByte <= 8;
    }

    /**
     * Determine if sequence looks like challenge data
     */
    private boolean isLikelyChallengeData(byte[] data) {
        if (data == null || data.length < 4) return false;

        // Challenges typically have good entropy and no obvious patterns
        int uniqueBytes = 0;
        for (byte b : data) {
            for (int i = 0; i < data.length; i++) {
                if (data[i] != b) {
                    uniqueBytes++;
                    break;
                }
            }
        }

        return uniqueBytes >= data.length / 2; // At least 50% unique bytes
    }

    /**
     * Generate comprehensive statistics
     */
    private String generateResponseStats(byte[][] responses) {
        StringBuilder sb = new StringBuilder();
        sb.append("Response Pattern Statistics:\n");
        for (Map.Entry<String, Integer> entry : responsePatterns.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get final analysis report
     */
    public AnalysisReport getReport(byte[][] apdus, byte[][] responses) {
        return analyzeCard(apdus, responses);
    }

    /**
     * Get detection result
     */
    public CardDetectionResult getDetectionResult() {
        return detectionResult;
    }

    /**
     * Get probe results
     */
    public List<ProbeResult> getProbeResults() {
        return probeResults;
    }

    /**
     * Hex decoder
     */
    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
                    Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }

    // ==================== Inner classes ====================

    public enum CipherType {
        TRIPLE_DES("3DES", 8),
        AES("AES", 16),
        UNKNOWN("UNKNOWN", 0);

        public final String name;
        public final int blockSize;

        CipherType(String name, int blockSize) {
            this.name = name;
            this.blockSize = blockSize;
        }
    }

    public enum MACType {
        CMAC_3DES_4BYTE("CMAC-3DES (4 bytes)", 4),
        CMAC_AES_8BYTE("CMAC-AES (8 bytes)", 8),
        CMAC_AES_4BYTE("CMAC-AES (4 bytes)", 4),
        UNKNOWN("UNKNOWN", 0);

        public final String name;
        public final int size;

        MACType(String name, int size) {
            this.name = name;
            this.size = size;
        }
    }

    public enum PaddingType {
        PKCS7("PKCS7"),
        ISO10126("ISO 10126"),
        ZERO("Zero Padding"),
        NONE("None"),
        UNKNOWN("Unknown");

        public final String name;

        PaddingType(String name) {
            this.name = name;
        }
    }

    public static class CipherInfo {
        public CipherType type;
        public int blockSize;
        public int keyLength;
        public int keyLengthAlt;
        public double confidence;

        @Override
        public String toString() {
            return String.format(
                    "Cipher: %s\n  Block Size: %d bytes\n  Key Length: %d bits (alt: %d bits)\n  Confidence: %.1f%%",
                    type.name, blockSize, keyLength, keyLengthAlt, confidence * 100
            );
        }
    }

    public static class MACInfo {
        public MACType type;
        public String algorithm;
        public int macSize;
        public double confidence;

        @Override
        public String toString() {
            return String.format(
                    "MAC: %s\n  Algorithm: %s\n  Size: %d bytes\n  Confidence: %.1f%%",
                    type.name, algorithm, macSize, confidence * 100
            );
        }
    }

    public static class PaddingScheme {
        public PaddingType type;
        public double confidence;

        @Override
        public String toString() {
            return String.format("Padding: %s (Confidence: %.1f%%)", type.name, confidence * 100);
        }
    }

    public static class ChallengeSequence {
        private byte[][] challenges = new byte[10][];
        private int count = 0;
        private boolean isRandom = false;
        private boolean isSequential = false;

        public void addChallenge(byte[] challenge) {
            if (count < challenges.length) {
                challenges[count++] = challenge.clone();
            }
        }

        public void analyzePattern() {
            if (count < 2) {
                isRandom = true;
                return;
            }

            // Check for sequential pattern
            boolean sequential = true;
            for (int i = 1; i < count; i++) {
                if (!isNextSequential(challenges[i - 1], challenges[i])) {
                    sequential = false;
                    break;
                }
            }

            isSequential = sequential;
            isRandom = !sequential;
        }

        private boolean isNextSequential(byte[] prev, byte[] curr) {
            if (prev == null || curr == null || prev.length != curr.length) return false;
            // Simple check: last bytes increment
            return ((prev[prev.length - 1] & 0xFF) + 1) == (curr[curr.length - 1] & 0xFF);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Challenges captured: %d\n", count));
            for (int i = 0; i < count && i < 5; i++) {
                sb.append(String.format("  [%d]: %s\n", i, toHex(challenges[i])));
            }
            if (count > 5) sb.append("  ...\n");
            sb.append(String.format("Pattern: %s", isSequential ? "Sequential" : isRandom ? "Random" : "Unknown"));
            return sb.toString();
        }

        private String toHex(byte[] b) {
            if (b == null) return "null";
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02X", x));
            return sb.toString();
        }
    }

    public static class AnalysisReport {
        private CipherInfo cipherInfo;
        private MACInfo macInfo;
        private ChallengeSequence terminalChallenge;
        private ChallengeSequence cardChallenge;
        private PaddingScheme paddingScheme;
        private String responseStats;

        public void setCipherInfo(CipherInfo info) { this.cipherInfo = info; }
        public void setMacInfo(MACInfo info) { this.macInfo = info; }
        public void setTerminalChallenge(ChallengeSequence ch) { this.terminalChallenge = ch; }
        public void setCardChallenge(ChallengeSequence ch) { this.cardChallenge = ch; }
        public void setPaddingScheme(PaddingScheme ps) { this.paddingScheme = ps; }
        public void setResponseStats(String stats) { this.responseStats = stats; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========== CRYPTOGRAPHY ANALYSIS REPORT ==========\n\n");
            sb.append(cipherInfo).append("\n\n");
            sb.append(macInfo).append("\n\n");
            sb.append(paddingScheme).append("\n\n");
            sb.append("=== Terminal Challenge Sequence ===\n").append(terminalChallenge).append("\n\n");
            sb.append("=== Card Challenge Sequence ===\n").append(cardChallenge).append("\n\n");
            sb.append(responseStats);
            return sb.toString();
        }
    }

    /**
     * Probe result from real APDU execution
     */
    public static class ProbeResult {
        public int index;
        public byte[] response;
        public boolean isSuccess;
        public int dataLength;
        public String statusWord;

        @Override
        public String toString() {
            return String.format("Probe[%d]: SW=%s len=%d %s",
                    index, statusWord, dataLength, isSuccess ? "OK" : "FAIL");
        }
    }

    /**
     * Card detection result based on actual responses
     */
    public static class CardDetectionResult {
        public String cardType;
        public double confidence;
        public int probesSuccessful;
        public int probesTotal;
        public int totalDataBytes;

        @Override
        public String toString() {
            return String.format("CARD TYPE: %s\nConfidence: %.0f%%\nProbes: %d/%d successful\nData bytes: %d",
                    cardType, confidence * 100, probesSuccessful, probesTotal, totalDataBytes);
        }
    }
}
