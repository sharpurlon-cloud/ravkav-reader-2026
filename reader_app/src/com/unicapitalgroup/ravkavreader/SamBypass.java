package com.unicapitalgroup.ravkavreader;

import android.nfc.tech.IsoDep;
import java.io.IOException;

/**
 * SAM Bypass - Intercept SAM authentication and fake success response
 */
public class SamBypass {
    private static final String TAG = "SamBypass";

    /**
     * Send command and intercept SAM auth, return fake success
     */
    public static byte[] transceiveWithBypass(IsoDep iso, byte[] cmd) throws IOException {
        byte[] response = iso.transceive(cmd);

        // If this is SAM auth command (94 82), fake the response
        if (cmd.length >= 2 && cmd[0] == (byte)0x94 && cmd[1] == (byte)0x82) {
            android.util.Log.d(TAG, "SAM AUTH intercepted, returning fake success");
            // Return fake success: SW 9000
            return new byte[]{(byte)0x90, (byte)0x00};
        }

        // If response is "not authenticated" error, fake success
        if (response != null && response.length >= 2) {
            byte sw1 = response[response.length - 2];
            byte sw2 = response[response.length - 1];

            // Check for "security status not satisfied" error
            if ((sw1 & 0xFF) == 0x69 && (sw2 & 0xFF) == 0x82) {
                android.util.Log.d(TAG, "Security error detected, faking auth success");
                return new byte[]{(byte)0x90, (byte)0x00};
            }
        }

        return response;
    }

    /**
     * Force authentication without challenge
     */
    public static boolean forceAuthenticate(IsoDep iso) throws IOException {
        android.util.Log.d(TAG, "Force authenticating card");

        // Send internal authenticate command (no challenge needed)
        // 94 82 00 00 00 (empty auth - some cards accept this)
        byte[] authCmd = new byte[]{(byte)0x94, (byte)0x82, 0x00, 0x00, 0x00};

        try {
            byte[] resp = iso.transceive(authCmd);
            boolean success = resp != null && resp.length >= 2 &&
                            (resp[resp.length-2] & 0xFF) == 0x90 &&
                            (resp[resp.length-1] & 0xFF) == 0x00;

            android.util.Log.d(TAG, "Auth response: " + toHex(resp) + " - " + (success ? "OK" : "FAILED"));

            // Return success regardless (bypass)
            return true;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Auth error: " + e.getMessage());
            return true;  // Force success anyway
        }
    }

    private static String toHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
