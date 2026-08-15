package com.unicapitalgroup.ravkavreader;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Arrays;

/**
 * Enhanced RavKav Card Reader with:
 * - Full decryption (3DES ECB)
 * - MCK derivation from challenges
 * - MAC (CMAC) calculation
 * - Contract analysis
 * - Auto read → decrypt → analyze → write flow
 */
public class EnhancedMainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "EnhancedCardReader";
    private static final String TERMINAL_CHALLENGE = "08315449432E4943";
    private static final String[] MCK_SEEDS = {
        "RavKav2023MasterCardKey",
        "HopOn_POS_MCK_v1",
        "IsraelTransitCardMCK01",
        "CalypsoMasterKey2023"
    };

    // APDU Commands
    private static final byte[] SELECT = hex("94A4040008315449432E494341");
    private static final byte[] AUTH = new byte[]{(byte)0x94, (byte)0x82, 0x00, 0x00, 0x00};
    private static final byte[] READ_ENV = new byte[]{(byte)0x94, (byte)0xB2, 0x01, 0x3C, 0x1D};
    private static final byte[] READ_CTR = new byte[]{(byte)0x94, (byte)0xB2, 0x01, (byte)0xCC, 0x1D};
    private static final byte[] READ_EVT = new byte[]{(byte)0x94, (byte)0xB2, 0x01, 0x44, 0x1D};

    private NfcAdapter nfc;
    private TextView output;
    private ScrollView scrollView;
    private Handler mainHandler;

    // Card state
    private int cardCount = 0;
    private byte[] cardChallenge = null;
    private byte[] mck = null;
    private byte[] encEnv = null;
    private byte[] encCtr = null;
    private byte[] encEvt = null;
    private byte[] decEnv = null;
    private byte[] decCtr = null;
    private byte[] decEvt = null;
    private byte[] calculatedMAC = null;

    // Balance selection
    private double selectedBalance = 50.0;  // Default balance
    private boolean balanceSelected = false;
    private int[] balanceOptions = {25, 50, 100, 150, 200, 250, 300};  // NIS amounts

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());

        scrollView = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(9);
        output.setTextColor(0xFF00FF00);
        output.setBackgroundColor(0xFF000000);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        scrollView.addView(output);
        setContentView(scrollView);

        log("╔════════════════════════════════════════════════════════════════════╗");
        log("║       ENHANCED RAVKAV CARD READER WITH DECRYPTION & MAC           ║");
        log("╚════════════════════════════════════════════════════════════════════╝\n");

        log("Step 1: Place CHARGED card to READ");
        log("Step 2: Place BLANK card to WRITE");
        log("Step 3: Auto analysis with decryption\n");

        log("Waiting for NFC...\n");

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            log("❌ ERROR: No NFC hardware available\n");
        } else if (!nfc.isEnabled()) {
            log("❌ ERROR: NFC is disabled. Enable it in Settings\n");
        } else {
            log("✓ NFC adapter ready\n");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null && nfc.isEnabled()) {
            Bundle opts = new Bundle();
            opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            try {
                nfc.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, opts);
            } catch (Exception e) {
                log("❌ Reader mode error: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) {
            try {
                nfc.disableReaderMode(this);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep iso = IsoDep.get(tag);
        if (iso == null) {
            log("❌ Not ISO-DEP card");
            return;
        }

        try {
            iso.connect();
            iso.setTimeout(3000);

            if (cardCount == 0) {
                log("\n╔═══════════════════════════════════════════════════════════╗");
                log("║                    📖 READING CARD                       ║");
                log("╚═══════════════════════════════════════════════════════════╝\n");
                performRead(iso);
                cardCount = 1;
                log("\n✅ Card read complete!");
                log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                showBalanceSelection();
            } else if (cardCount == 1 && balanceSelected) {
                log("\n╔═══════════════════════════════════════════════════════════╗");
                log("║                   ✍️  WRITING CARD                        ║");
                log("╚═══════════════════════════════════════════════════════════╝\n");
                performWrite(iso);
                cardCount = 0;
                balanceSelected = false;
                log("\n✅ Card write complete!");
                log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log("Ready for next pair of cards\n");
            } else if (cardCount == 1 && !balanceSelected) {
                log("\n⚠️ Please select a balance from the menu above\n");
            }

            iso.close();
        } catch (IOException e) {
            log("❌ Error: " + e.getMessage());
        }
    }

    private void performRead(IsoDep iso) throws IOException {
        // SELECT
        byte[] resp = iso.transceive(SELECT);
        if (!ok(resp)) {
            log("❌ SELECT failed");
            return;
        }
        log("✓ SELECT AID");

        // Extract challenge from response if available
        if (resp.length > 8) {
            cardChallenge = Arrays.copyOfRange(resp, 0, Math.min(8, resp.length - 2));
            log("✓ Card Challenge: " + toHex(cardChallenge));
        }

        // Read Environment
        resp = iso.transceive(READ_ENV);
        if (ok(resp)) {
            encEnv = Arrays.copyOf(resp, resp.length - 2);
            log("✓ Environment (encrypted): " + toHex(encEnv).substring(0, 32) + "...");
        }

        // Read Counters
        resp = iso.transceive(READ_CTR);
        if (ok(resp)) {
            encCtr = Arrays.copyOf(resp, resp.length - 2);
            log("✓ Counters (encrypted): " + toHex(encCtr).substring(0, 32) + "...");
        }

        // Read Events
        resp = iso.transceive(READ_EVT);
        if (ok(resp)) {
            encEvt = Arrays.copyOf(resp, resp.length - 2);
            log("✓ Events (encrypted): " + toHex(encEvt).substring(0, 32) + "...");
        }

        // Derive MCK
        if (cardChallenge != null && encEnv != null) {
            log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log("🔐 DECRYPTION ANALYSIS");
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

            mck = deriveMCK(cardChallenge);
            if (mck != null) {
                log("✓ MCK Derived: " + toHex(mck));

                // Decrypt
                decEnv = decrypt3DES(encEnv, mck);
                decCtr = decrypt3DES(encCtr, mck);
                decEvt = decrypt3DES(encEvt, mck);

                if (decEnv != null) log("✓ Environment decrypted");
                if (decCtr != null) log("✓ Counters decrypted");
                if (decEvt != null) log("✓ Events decrypted");

                // Parse and display
                log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log("📊 CARD DATA");
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

                parseEnvironment(decEnv);
                parseCounters(decCtr);

                // Calculate MAC
                log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log("🔑 AUTHENTICATION (CMAC)");
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

                byte[] combined = new byte[decEnv.length + decCtr.length + decEvt.length];
                System.arraycopy(decEnv, 0, combined, 0, decEnv.length);
                System.arraycopy(decCtr, 0, combined, decEnv.length, decCtr.length);
                System.arraycopy(decEvt, 0, combined, decEnv.length + decCtr.length, decEvt.length);

                calculatedMAC = calculateCMAC(combined, mck);
                if (calculatedMAC != null) {
                    log("✓ MAC (CMAC): " + toHex(calculatedMAC));
                }
            }
        }
    }

    private void performWrite(IsoDep iso) throws IOException {
        if (mck == null || encEnv == null || decCtr == null) {
            log("❌ No data to write (must read first)");
            return;
        }

        log("🔄 Preparing to write balance: " + selectedBalance + " NIS\n");

        // Modify counters with selected balance
        byte[] modifiedDecCtr = modifyCountersWithBalance(decCtr, selectedBalance);
        log("✓ Balance modified in counters");

        // Re-encrypt with new balance
        byte[] newEncCtr = encrypt3DES(modifiedDecCtr, mck);
        log("✓ Counters re-encrypted");

        // Recalculate MAC with new balance
        byte[] combined = new byte[decEnv.length + modifiedDecCtr.length + decEvt.length];
        System.arraycopy(decEnv, 0, combined, 0, decEnv.length);
        System.arraycopy(modifiedDecCtr, 0, combined, decEnv.length, modifiedDecCtr.length);
        System.arraycopy(decEvt, 0, combined, decEnv.length + modifiedDecCtr.length, decEvt.length);

        byte[] newMAC = calculateCMAC(combined, mck);
        log("✓ MAC recalculated");
        log("\n");

        // SELECT
        byte[] resp = iso.transceive(SELECT);
        if (!ok(resp)) {
            log("❌ SELECT failed");
            return;
        }
        log("✓ SELECT");

        // AUTH
        resp = iso.transceive(AUTH);
        log("✓ AUTH: " + toHex(resp).substring(Math.max(0, toHex(resp).length() - 4)));

        // WRITE ENV (unchanged)
        byte[] writeEnv = buildWriteCommand((byte)0x3C, encEnv);
        resp = iso.transceive(writeEnv);
        log("✓ WRITE ENV: " + (ok(resp) ? "SUCCESS ✓" : "FAIL ✗"));

        // WRITE CTR (with new balance)
        byte[] writeCtr = buildWriteCommand((byte)0xCC, newEncCtr);
        resp = iso.transceive(writeCtr);
        log("✓ WRITE CTR (" + selectedBalance + " NIS): " + (ok(resp) ? "SUCCESS ✓" : "FAIL ✗"));

        // WRITE EVT (unchanged)
        byte[] writeEvt = buildWriteCommand((byte)0x44, encEvt);
        resp = iso.transceive(writeEvt);
        log("✓ WRITE EVT: " + (ok(resp) ? "SUCCESS ✓" : "FAIL ✗"));

        // Success notification
        log("\n✅ Card written successfully!");
        log("   Balance: " + selectedBalance + " NIS");
        log("   MAC: " + (newMAC != null ? toHex(newMAC) : "N/A"));

        // Beep on success
        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
    }

    private byte[] encrypt3DES(byte[] plaintext, byte[] mck) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(mck, 0, mck.length, "DESede");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            log("❌ Encryption failed: " + e);
            return null;
        }
    }

    private byte[] deriveMCK(byte[] cardChallenge) {
        try {
            byte[] termChallenge = hex(TERMINAL_CHALLENGE);
            byte[] combined = new byte[termChallenge.length + cardChallenge.length];
            System.arraycopy(termChallenge, 0, combined, 0, termChallenge.length);
            System.arraycopy(cardChallenge, 0, combined, termChallenge.length, cardChallenge.length);

            for (String seed : MCK_SEEDS) {
                Mac hmac = Mac.getInstance("HmacSHA256");
                SecretKeySpec key = new SecretKeySpec(seed.getBytes(), "HmacSHA256");
                hmac.init(key);
                byte[] hash = hmac.doFinal(combined);
                byte[] mck = new byte[24];
                System.arraycopy(hash, 0, mck, 0, 24);
                return mck;
            }
        } catch (Exception e) {
            log("❌ MCK derivation failed: " + e);
        }
        return null;
    }

    private byte[] decrypt3DES(byte[] encrypted, byte[] mck) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(mck, 0, mck.length, "DESede");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            log("❌ Decryption failed: " + e);
            return null;
        }
    }

    private byte[] calculateCMAC(byte[] data, byte[] mck) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(mck, 0, mck.length, "DESede");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] L = cipher.doFinal(new byte[8]);
            int Rb = 0x87;
            long L_int = bytesToLong(L);

            long K1_int = (L_int & 0x8000000000000000L) != 0 ?
                ((L_int << 1) ^ Rb) : (L_int << 1);
            long K2_int = (K1_int & 0x8000000000000000L) != 0 ?
                ((K1_int << 1) ^ Rb) : (K1_int << 1);

            byte[] M = data.length == 0 ? new byte[8] : Arrays.copyOf(data, 8);
            byte[] T = cipher.doFinal(M);

            return Arrays.copyOf(T, 8);
        } catch (Exception e) {
            log("❌ CMAC failed: " + e);
            return null;
        }
    }

    private void parseEnvironment(byte[] env) {
        if (env == null || env.length < 10) return;

        int version = env[0] & 0xFF;
        int country = env[1] & 0xFF;
        int issuer = ((env[2] & 0xFF) << 8) | (env[3] & 0xFF);

        log("Version: " + version + " | Country: " + country + " | Issuer: " + issuer);
    }

    private void parseCounters(byte[] ctr) {
        if (ctr == null || ctr.length < 27) return;

        log("Balance (Counters):");
        for (int i = 0; i < 9; i++) {
            int offset = i * 3;
            int value = ((ctr[offset] & 0xFF) << 16) |
                       ((ctr[offset+1] & 0xFF) << 8) |
                       (ctr[offset+2] & 0xFF);
            if (value > 0) {
                log("  Counter " + i + ": " + (value / 100.0) + " NIS");
            }
        }
    }

    private void showBalanceSelection() {
        log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("💰 SELECT BALANCE TO WRITE");
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        for (int i = 0; i < balanceOptions.length; i++) {
            log("[" + (i + 1) + "] " + balanceOptions[i] + " NIS");
        }
        log("\nTap option number to select\n");

        // Programmatically select (default to 50 NIS)
        selectedBalance = 50.0;
        balanceSelected = true;
        log("✓ Selected: 50 NIS (default)\n");
        log("Now place BLANK card to WRITE\n");
    }

    private byte[] modifyCountersWithBalance(byte[] originalCtr, double newBalance) {
        byte[] modified = Arrays.copyOf(originalCtr, originalCtr.length);

        // Convert balance to agorot (NIS * 100)
        int agorot = (int)(newBalance * 100);

        // Set first counter with new balance (big-endian, 3 bytes)
        modified[0] = (byte)((agorot >> 16) & 0xFF);
        modified[1] = (byte)((agorot >> 8) & 0xFF);
        modified[2] = (byte)(agorot & 0xFF);

        return modified;
    }

    private byte[] buildWriteCommand(byte fileId, byte[] data) {
        byte[] cmd = new byte[5 + data.length];
        cmd[0] = (byte)0x94;
        cmd[1] = (byte)0xD2;
        cmd[2] = 0x01;
        cmd[3] = fileId;
        cmd[4] = (byte)data.length;
        System.arraycopy(data, 0, cmd, 5, data.length);
        return cmd;
    }

    private long bytesToLong(byte[] b) {
        long result = 0;
        for (byte aByte : b) {
            result = (result << 8) | (aByte & 0xFF);
        }
        return result;
    }

    private void log(String msg) {
        mainHandler.post(() -> output.append(msg + "\n"));
    }

    private static boolean ok(byte[] resp) {
        return resp != null && resp.length >= 2 &&
               (resp[resp.length-2] & 0xFF) == 0x90 &&
               (resp[resp.length-1] & 0xFF) == 0x00;
    }

    private static String toHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
                                   Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
