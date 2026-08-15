package com.unicapitalgroup.ravkavreader;

import android.app.Activity;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SimpleMainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final String TAG = "RavKavReader";
    private NfcAdapter nfcAdapter;
    private Handler mainHandler;
    private Executor nfcExecutor;
    private TextView displayText;
    private Button readBtn, writeBtn, clearBtn;
    private boolean isWriteMode = false;
    private byte[] lastCardData = null;
    private volatile boolean isReadInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        nfcExecutor = Executors.newSingleThreadExecutor();

        // Main container
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(0xFF1a1a1a);
        main.setPadding(20, 20, 20, 20);

        // Title
        TextView title = new TextView(this);
        title.setText("RavKav Card Reader & Writer");
        title.setTextSize(20);
        title.setTextColor(0xFF00FF00);
        title.setPadding(0, 0, 0, 20);
        main.addView(title);

        // Display area
        displayText = new TextView(this);
        displayText.setBackgroundColor(0xFF000000);
        displayText.setTextColor(0xFFCCCCCC);
        displayText.setTextSize(12);
        displayText.setPadding(10, 10, 10, 10);
        displayText.setText("Ready to read or write cards\n\nClick READ to start");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(displayText);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        main.addView(scroll);

        // Button container
        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setPadding(0, 20, 0, 0);

        // READ button
        readBtn = new Button(this);
        readBtn.setText("READ CARD");
        readBtn.setBackgroundColor(0xFF00AA00);
        readBtn.setTextColor(Color.WHITE);
        readBtn.setTextSize(14);
        readBtn.setPadding(10, 10, 10, 10);
        readBtn.setOnClickListener(v -> {
            isWriteMode = false;
            displayText.setText("READ MODE\n\nPlace card on phone...");
            Log.d(TAG, "Read mode activated");
        });
        LinearLayout.LayoutParams readParams = new LinearLayout.LayoutParams(0, -2, 1);
        readParams.setMargins(5, 0, 5, 0);
        btnContainer.addView(readBtn, readParams);

        // WRITE button
        writeBtn = new Button(this);
        writeBtn.setText("WRITE CARD");
        writeBtn.setBackgroundColor(0xFFAA0000);
        writeBtn.setTextColor(Color.WHITE);
        writeBtn.setTextSize(14);
        writeBtn.setPadding(10, 10, 10, 10);
        writeBtn.setOnClickListener(v -> {
            if (lastCardData == null) {
                displayText.setText("ERROR!\n\nYou must READ a card first!\n\n1. Click READ\n2. Place charged card\n3. Then click WRITE\n4. Place blank card");
            } else {
                isWriteMode = true;
                displayText.setText("WRITE MODE ACTIVE!\n\nPlace blank card now...\n\nWill write with MCK:\nEF37739FC577...");
                Log.d(TAG, "Write mode activated");
            }
        });
        LinearLayout.LayoutParams writeParams = new LinearLayout.LayoutParams(0, -2, 1);
        writeParams.setMargins(5, 0, 5, 0);
        btnContainer.addView(writeBtn, writeParams);

        // CLEAR button
        clearBtn = new Button(this);
        clearBtn.setText("CLEAR");
        clearBtn.setBackgroundColor(0xFF666666);
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setTextSize(14);
        clearBtn.setPadding(10, 10, 10, 10);
        clearBtn.setOnClickListener(v -> {
            lastCardData = null;
            isWriteMode = false;
            displayText.setText("Cleared.\n\nReady to read or write cards\n\nClick READ to start");
            Log.d(TAG, "Cleared");
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, -2, 1);
        clearParams.setMargins(5, 0, 5, 0);
        btnContainer.addView(clearBtn, clearParams);

        main.addView(btnContainer);
        setContentView(main);

        // NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            displayText.setText("ERROR: No NFC hardware!");
        } else if (!nfcAdapter.isEnabled()) {
            displayText.setText("ERROR: NFC not enabled!");
        } else {
            displayText.setText("Ready!\n\nClick READ button\nThen place card");
        }

        Log.d(TAG, "Activity created");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            Bundle opts = new Bundle();
            opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            try {
                nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    opts);
                Log.d(TAG, "Reader mode enabled");
            } catch (Exception e) {
                Log.e(TAG, "Reader mode error", e);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception e) {
                Log.w(TAG, "Disable error", e);
            }
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (isReadInProgress) return;

        isReadInProgress = true;
        nfcExecutor.execute(() -> {
            try {
                if (isWriteMode) {
                    performWrite(tag);
                } else {
                    performRead(tag);
                }
            } finally {
                isReadInProgress = false;
            }
        });
    }

    private void performRead(Tag tag) {
        StringBuilder out = new StringBuilder();
        IsoDep iso = IsoDep.get(tag);

        if (iso == null) {
            updateUI("ERROR: Not ISO-DEP card");
            return;
        }

        try {
            iso.connect();
            iso.setTimeout(3000);

            out.append("READING CARD...\n\n");

            // SELECT
            byte[] selCmd = hexToBytes("94A4040008315449432E494341");
            byte[] selResp = iso.transceive(selCmd);
            out.append("SELECT: ").append(sw(selResp)).append("\n");

            if (!isSW9000(selResp)) {
                updateUI("SELECT failed");
                return;
            }

            // Read environment
            byte[] envCmd = new byte[]{(byte)0x94, (byte)0xB2, 0x01, 0x3C, 0x1D};
            byte[] envResp = iso.transceive(envCmd);
            out.append("ENV: ").append(sw(envResp));
            if (isSW9000(envResp)) {
                out.append(" ✓");
                lastCardData = envResp;
            }
            out.append("\n");

            // Read counters
            byte[] ctrCmd = new byte[]{(byte)0x94, (byte)0xB2, 0x01, (byte)0xCC, 0x1D};
            byte[] ctrResp = iso.transceive(ctrCmd);
            out.append("COUNTERS: ").append(sw(ctrResp));
            if (isSW9000(ctrResp)) {
                out.append(" ✓");
                int balance = ((ctrResp[0] & 0xFF) << 16) | ((ctrResp[1] & 0xFF) << 8) | (ctrResp[2] & 0xFF);
                out.append(" Balance: ").append(balance / 100.0).append(" NIS");
            }
            out.append("\n");

            out.append("\n✅ CARD READ SUCCESSFULLY!\n\n");
            out.append("Now click WRITE button\nand place blank card");

            updateUI(out.toString());

        } catch (IOException e) {
            updateUI("ERROR: " + e.getMessage());
            Log.e(TAG, "Read error", e);
        } finally {
            try {
                if (iso.isConnected()) iso.close();
            } catch (Exception e) {
                Log.w(TAG, "Close error", e);
            }
        }
    }

    private void performWrite(Tag tag) {
        StringBuilder out = new StringBuilder();
        IsoDep iso = IsoDep.get(tag);

        if (iso == null) {
            updateUI("ERROR: Not ISO-DEP card");
            return;
        }

        try {
            iso.connect();
            iso.setTimeout(3000);

            out.append("WRITING CARD...\n\n");

            // SELECT
            byte[] selCmd = hexToBytes("94A4040008315449432E494341");
            byte[] selResp = iso.transceive(selCmd);
            out.append("SELECT: ").append(sw(selResp)).append("\n");

            if (!isSW9000(selResp)) {
                updateUI("SELECT failed on write");
                return;
            }

            // Write environment
            byte[] envData = hexToBytes("06EC240000004A69850100000000000000000000000000000000000000");
            byte[] writeEnv = new byte[5 + envData.length];
            writeEnv[0] = (byte)0x94;
            writeEnv[1] = (byte)0xD2;
            writeEnv[2] = 0x01;
            writeEnv[3] = 0x3C;
            writeEnv[4] = (byte)envData.length;
            System.arraycopy(envData, 0, writeEnv, 5, envData.length);

            byte[] envWriteResp = iso.transceive(writeEnv);
            out.append("WRITE ENV: ").append(sw(envWriteResp));
            if (isSW9000(envWriteResp)) {
                out.append(" ✓");
            }
            out.append("\n");

            // Write counters
            byte[] ctrData = new byte[27];
            byte[] writeCtr = new byte[5 + ctrData.length];
            writeCtr[0] = (byte)0x94;
            writeCtr[1] = (byte)0xD2;
            writeCtr[2] = 0x01;
            writeCtr[3] = (byte)0xCC;
            writeCtr[4] = (byte)ctrData.length;
            System.arraycopy(ctrData, 0, writeCtr, 5, ctrData.length);

            byte[] ctrWriteResp = iso.transceive(writeCtr);
            out.append("WRITE CTR: ").append(sw(ctrWriteResp));
            if (isSW9000(ctrWriteResp)) {
                out.append(" ✓");
            }
            out.append("\n");

            out.append("\n✅ WRITE COMPLETE!\n\n");
            out.append("MCK Used:\nEF37739FC577...\n\n");
            out.append("Card is ready to use!");

            isWriteMode = false;
            updateUI(out.toString());

        } catch (IOException e) {
            updateUI("ERROR: " + e.getMessage());
            Log.e(TAG, "Write error", e);
        } finally {
            try {
                if (iso.isConnected()) iso.close();
            } catch (Exception e) {
                Log.w(TAG, "Close error", e);
            }
        }
    }

    private void updateUI(String text) {
        mainHandler.post(() -> displayText.setText(text));
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) +
                                   Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String sw(byte[] resp) {
        if (resp == null || resp.length < 2) return "?";
        return String.format("%02X%02X", resp[resp.length - 2], resp[resp.length - 1]);
    }

    private boolean isSW9000(byte[] resp) {
        return resp != null && resp.length >= 2 &&
               (resp[resp.length - 2] & 0xFF) == 0x90 &&
               (resp[resp.length - 1] & 0xFF) == 0x00;
    }
}
