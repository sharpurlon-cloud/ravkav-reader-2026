package com.unicapitalgroup.ravkavreader;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;

import java.io.IOException;

/**
 * CARD CLONER UI
 * ==============
 *
 * Complete user interface for card cloning:
 * - Step-by-step workflow
 * - Real-time status updates
 * - Comprehensive logging
 * - Success/failure indicators
 *
 * AUTH-2026-001
 */
public class CardClonerUI extends Activity implements NfcAdapter.ReaderCallback {

    private static final String TAG = "CardClonerUI";

    private NfcAdapter nfcAdapter;
    private Handler mainHandler;
    private CardCloner cloner;

    // UI Components
    private TextView titleView;
    private TextView statusView;
    private TextView logView;
    private Button readButton;
    private Button analyzeButton;
    private Button createFilesButton;
    private Button writeButton;
    private Button verifyButton;
    private Button resetButton;
    private ScrollView logScroll;

    // State
    private enum WorkflowStep {
        STEP1_READ("Step 1: Read Old Card", Color.GRAY),
        STEP2_ANALYZE("Step 2: Analyze Crypto", Color.GRAY),
        STEP3_CREATE("Step 3: Create Files", Color.GRAY),
        STEP4_WRITE("Step 4: Write Data", Color.GRAY),
        STEP5_VERIFY("Step 5: Verify", Color.GRAY),
        SUCCESS("SUCCESS!", Color.GREEN),
        FAILED("FAILED!", Color.RED);

        public final String label;
        public final int color;

        WorkflowStep(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private WorkflowStep currentStep = WorkflowStep.STEP1_READ;
    private String extractedMCK = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Create UI
        createUI();

        // Setup NFC
        setupNFC();

        updateStatus("Ready. Tap old card to read...");
    }

    private void createUI() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.setPadding(16, 16, 16, 16);

        // Title
        titleView = new TextView(this);
        titleView.setText("CARD CLONER - Complete Solution");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        rootLayout.addView(titleView);

        // Status
        statusView = new TextView(this);
        statusView.setText("Ready");
        statusView.setTextSize(14);
        statusView.setTextColor(Color.YELLOW);
        statusView.setPadding(0, 16, 0, 8);
        rootLayout.addView(statusView);

        // Log (scrollable)
        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT - 300
        ));

        logView = new TextView(this);
        logView.setText("=== Log ===\n");
        logView.setTextSize(10);
        logView.setTextColor(Color.GREEN);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(8, 8, 8, 8);
        logScroll.addView(logView);

        rootLayout.addView(logScroll);

        // Buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(0, 8, 0, 0);

        readButton = createButton("1. Read Old Card", new Runnable() {
            @Override
            public void run() {
                onReadButtonClicked();
            }
        });

        analyzeButton = createButton("2. Analyze Crypto", new Runnable() {
            @Override
            public void run() {
                onAnalyzeButtonClicked();
            }
        });
        analyzeButton.setEnabled(false);

        createFilesButton = createButton("3. Create Files on New Card", new Runnable() {
            @Override
            public void run() {
                onCreateFilesButtonClicked();
            }
        });
        createFilesButton.setEnabled(false);

        writeButton = createButton("4. Write Data", new Runnable() {
            @Override
            public void run() {
                onWriteButtonClicked();
            }
        });
        writeButton.setEnabled(false);

        verifyButton = createButton("5. Verify Result", new Runnable() {
            @Override
            public void run() {
                onVerifyButtonClicked();
            }
        });
        verifyButton.setEnabled(false);

        resetButton = createButton("Reset", new Runnable() {
            @Override
            public void run() {
                onResetButtonClicked();
            }
        });

        buttonLayout.addView(readButton);
        buttonLayout.addView(analyzeButton);
        buttonLayout.addView(createFilesButton);
        buttonLayout.addView(writeButton);
        buttonLayout.addView(verifyButton);
        buttonLayout.addView(resetButton);

        rootLayout.addView(buttonLayout);

        setContentView(rootLayout);
    }

    private Button createButton(String text, Runnable onClick) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.DKGRAY);
        btn.setPadding(8, 8, 8, 8);
        btn.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                80
        ));
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private void setupNFC() {
        if (nfcAdapter == null) {
            updateStatus("NFC not available");
            return;
        }
        nfcAdapter.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B,
                null);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Log.i(TAG, "Tag discovered");

        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            updateStatus("IsoDep not supported");
            return;
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(3000);

            cloner = new CardCloner(isoDep);

            // Auto-run based on current step
            switch (currentStep) {
                case STEP1_READ:
                    performRead(isoDep);
                    break;
                case STEP3_CREATE:
                    performCreateFiles(isoDep);
                    break;
                case STEP4_WRITE:
                    performWrite(isoDep);
                    break;
                case STEP5_VERIFY:
                    performVerify(isoDep);
                    break;
                default:
                    updateStatus("Select a step");
            }

            isoDep.close();

        } catch (IOException e) {
            Log.e(TAG, "Error: " + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    // ==================== BUTTON HANDLERS ====================

    private void onReadButtonClicked() {
        appendLog("\n========== STEP 1: READ OLD CARD ==========");
        appendLog("Instructions:");
        appendLog("• Place old card (with balance) on NFC reader");
        appendLog("• Keep it still for 3-5 seconds");
        appendLog("• Do NOT remove until reading completes");
        appendLog("\nWaiting for card...");
        updateStatus("Tap old card to read...");
        currentStep = WorkflowStep.STEP1_READ;
    }

    private void onAnalyzeButtonClicked() {
        appendLog("\n========== STEP 2: ANALYZE CRYPTOGRAPHY ==========");
        appendLog("Processing read data...");
        appendLog("• Detecting cipher type");
        appendLog("• Identifying MAC algorithm");
        appendLog("• Extracting challenge sequences");
        appendLog("• Analyzing padding scheme");
        updateStatus("Analyzing crypto...");
        currentStep = WorkflowStep.STEP2_ANALYZE;

        new Thread(() -> {
            try {
                if (cloner.analyzeCrypto()) {
                    mainHandler.post(() -> {
                        appendLog("\n✓ Crypto analysis complete!");
                        appendLog("Ready for next step.");
                        updateStatus("Crypto analyzed. Tap new card for file creation.");
                        analyzeButton.setEnabled(false);
                        createFilesButton.setEnabled(true);
                        currentStep = WorkflowStep.STEP3_CREATE;
                    });
                } else {
                    mainHandler.post(() -> {
                        appendLog("\n✗ Crypto analysis FAILED!");
                    });
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    appendLog("\n✗ Error: " + e.getMessage());
                    updateStatus("Analysis failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onCreateFilesButtonClicked() {
        appendLog("\n========== STEP 3: CREATE FILE STRUCTURE ==========");
        appendLog("IMPORTANT - Use DIFFERENT card!");
        appendLog("\nInstructions:");
        appendLog("• Remove old card from reader");
        appendLog("• Place NEW BLANK card on NFC reader");
        appendLog("• This card should be empty/new");
        appendLog("• Keep it still for 3-5 seconds");
        appendLog("\nThis step will:");
        appendLog("✓ Create Environment file (0x2000)");
        appendLog("✓ Create Counters file (0x2001)");
        appendLog("✓ Create Contracts file (0x2002)");
        appendLog("✓ Create Events file (0x2003)");
        appendLog("\nWaiting for new card...");
        updateStatus("Tap new blank card to create files...");
        currentStep = WorkflowStep.STEP3_CREATE;
    }

    private void onWriteButtonClicked() {
        appendLog("\n========== STEP 4: WRITE DATA ==========");
        appendLog("SAME card from step 3!");
        appendLog("\nInstructions:");
        appendLog("• Keep the new card on NFC reader");
        appendLog("• Do NOT remove it");
        appendLog("• Keep it still for 5-8 seconds");
        appendLog("\nThis step will:");
        appendLog("✓ Open secure session");
        appendLog("✓ Write Environment data");
        appendLog("✓ Write Counters data");
        appendLog("✓ Write Contracts data");
        appendLog("✓ Write Events data");
        appendLog("✓ Close secure session");
        appendLog("\nProcessing...");
        updateStatus("Tap new card to write data...");
        currentStep = WorkflowStep.STEP4_WRITE;
    }

    private void onVerifyButtonClicked() {
        appendLog("\n========== STEP 5: VERIFICATION ==========");
        appendLog("FINAL CHECK - Same card!");
        appendLog("\nInstructions:");
        appendLog("• Keep the new card on NFC reader");
        appendLog("• Keep it still for 3-5 seconds");
        appendLog("\nThis step will:");
        appendLog("✓ Read all files from new card");
        appendLog("✓ Compare with original data");
        appendLog("✓ Verify byte-by-byte match");
        appendLog("✓ Confirm successful cloning");
        appendLog("\nVerifying...");
        updateStatus("Tap new card to verify...");
        currentStep = WorkflowStep.STEP5_VERIFY;
    }

    private void onResetButtonClicked() {
        cloner = null;
        extractedMCK = "";
        currentStep = WorkflowStep.STEP1_READ;
        logView.setText("=== CARD CLONER LOG ===\n\n");
        appendLog("Reset complete!");
        appendLog("Ready to start a new cloning session.");
        appendLog("\nNext step: Read old card");
        updateStatus("Reset. Ready to start.");

        readButton.setEnabled(true);
        analyzeButton.setEnabled(false);
        createFilesButton.setEnabled(false);
        writeButton.setEnabled(false);
        verifyButton.setEnabled(false);
    }

    // ==================== OPERATIONS ====================

    private void performRead(IsoDep isoDep) throws IOException {
        updateStatus("Reading card...");

        new Thread(() -> {
            try {
                if (cloner.readOldCard()) {
                    mainHandler.post(() -> {
                        appendLog(cloner.getFormattedLog());
                        updateStatus("Read successful! Tap analyze button or new card for analysis.");
                        readButton.setEnabled(false);
                        analyzeButton.setEnabled(true);
                    });
                } else {
                    mainHandler.post(() -> updateStatus("Read failed!"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> updateStatus("Read error: " + e.getMessage()));
            }
        }).start();
    }

    private void performCreateFiles(IsoDep isoDep) throws IOException {
        updateStatus("Creating files on new card...");

        new Thread(() -> {
            try {
                if (cloner.createFilesOnNewCard()) {
                    mainHandler.post(() -> {
                        appendLog(cloner.getFormattedLog());
                        updateStatus("Files created! Tap write button or new card.");
                        createFilesButton.setEnabled(false);
                        writeButton.setEnabled(true);
                        currentStep = WorkflowStep.STEP4_WRITE;
                    });
                } else {
                    mainHandler.post(() -> updateStatus("File creation failed!"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> updateStatus("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void performWrite(IsoDep isoDep) throws IOException {
        updateStatus("Writing data to new card...");

        new Thread(() -> {
            try {
                // For now, use MCK from old card - can be improved
                String mck = "5adc08bb02a7d54a"; // Example MCK

                if (cloner.writeDataToNewCard(mck)) {
                    mainHandler.post(() -> {
                        appendLog(cloner.getFormattedLog());
                        updateStatus("Data written! Tap verify button or new card.");
                        writeButton.setEnabled(false);
                        verifyButton.setEnabled(true);
                        currentStep = WorkflowStep.STEP5_VERIFY;
                    });
                } else {
                    mainHandler.post(() -> updateStatus("Write failed!"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> updateStatus("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void performVerify(IsoDep isoDep) throws IOException {
        updateStatus("Verifying data on new card...");

        new Thread(() -> {
            try {
                if (cloner.verifyNewCard()) {
                    mainHandler.post(() -> {
                        appendLog(cloner.getFormattedLog());
                        appendLog("\n=== SUCCESS ===");
                        appendLog(cloner.getResult().toString());
                        updateStatus("CLONING SUCCESSFUL!");
                        statusView.setTextColor(Color.GREEN);
                        verifyButton.setEnabled(false);
                        currentStep = WorkflowStep.SUCCESS;
                    });
                } else {
                    mainHandler.post(() -> {
                        updateStatus("Verification failed!");
                        currentStep = WorkflowStep.FAILED;
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> updateStatus("Error: " + e.getMessage()));
            }
        }).start();
    }

    // ==================== UI HELPERS ====================

    private void updateStatus(String msg) {
        mainHandler.post(() -> {
            statusView.setText(currentStep.label + " | " + msg);
            statusView.setTextColor(currentStep.color);
            Log.i(TAG, msg);
        });
    }

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            logView.append(msg + "\n");
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }
}
