    package com.example.gptbackup.ui;

    import android.content.Intent;
    import android.os.Bundle;
    import android.view.View;
    import android.widget.ProgressBar;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.appcompat.app.AppCompatActivity;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;

    import com.example.gptbackup.R;
    import com.example.gptbackup.adapter.SmartFileAdapter;
    import com.example.gptbackup.ai.FilePriorityEngine;
    import com.example.gptbackup.backup.UploadManager;
    import com.example.gptbackup.model.FileModel;
    import com.example.gptbackup.model.UploadState;
    import com.example.gptbackup.scanner.FileScanner;
    import com.google.android.gms.auth.api.signin.GoogleSignIn;
    import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
    import com.google.android.gms.auth.api.signin.GoogleSignInClient;
    import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
    import com.google.android.material.button.MaterialButton;
    import com.google.android.material.chip.Chip;

    import java.util.ArrayList;
    import java.util.List;

    public class SmartBackupActivity extends AppCompatActivity
            implements UploadManager.Listener {

        private static final int RC_SIGN_IN = 101;

        // Main UI
        private TextView txtProgress, txtAccountStatus;
        private RecyclerView recyclerSmart;
        private MaterialButton btnBackup, btnChangeAccount;
        private Chip chipHigh, chipMedium, chipLow;

        // Popup UI
        private View uploadOverlay;
        private ProgressBar progressUpload;
        private TextView txtUploadInfo, txtFileCount;
        private MaterialButton btnPause, btnResume, btnCancel;

        // Data
        private final List<FileModel> allFiles = new ArrayList<>();
        private final List<FileModel> filteredFiles = new ArrayList<>();
        private SmartFileAdapter adapter;

        // Backup
        private UploadManager uploadManager;
        private GoogleSignInClient googleSignInClient;
        private GoogleSignInAccount currentAccount;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_smart_backup);

            bindViews();
            setupGoogle();
            setupRecycler();
            setupChips();
            loadFilesAsync();
            btnChangeAccount.setOnClickListener(v -> openAccountChooser());

            btnBackup.setOnClickListener(v -> startBackup());

            btnPause.setOnClickListener(v -> {
                if (uploadManager != null) uploadManager.pause();
            });

            btnResume.setOnClickListener(v -> {
                if (uploadManager != null) uploadManager.resume();
            });

            btnCancel.setOnClickListener(v -> {
                if (uploadManager != null) uploadManager.cancel();
                hideOverlay();
            });

            findViewById(R.id.btnOpenFileManager)
                    .setOnClickListener(v ->
                            startActivity(new Intent(this, MainActivity.class)));
        }

        private void bindViews() {
            txtProgress = findViewById(R.id.txtProgress);
            txtAccountStatus = findViewById(R.id.txtAccountStatus);
            recyclerSmart = findViewById(R.id.recyclerSmart);
            btnBackup = findViewById(R.id.btnBackupHigh);
            btnChangeAccount = findViewById(R.id.btnChangeAccount);

            chipHigh = findViewById(R.id.chipHigh);
            chipMedium = findViewById(R.id.chipMedium);
            chipLow = findViewById(R.id.chipLow);

            uploadOverlay = findViewById(R.id.uploadOverlay);
            progressUpload = findViewById(R.id.progressUpload);
            txtUploadInfo = findViewById(R.id.txtUploadInfo);
            txtFileCount = findViewById(R.id.txtFileCount);

            btnPause = findViewById(R.id.btnPause);
            btnResume = findViewById(R.id.btnResume);
            btnCancel = findViewById(R.id.btnCancel);
        }

        private void setupRecycler() {
            recyclerSmart.setLayoutManager(new LinearLayoutManager(this));
            adapter = new SmartFileAdapter(filteredFiles);
            recyclerSmart.setAdapter(adapter);
        }

        private void setupGoogle() {
            GoogleSignInOptions gso =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build();

            googleSignInClient = GoogleSignIn.getClient(this, gso);
            currentAccount = GoogleSignIn.getLastSignedInAccount(this);

            txtAccountStatus.setText(
                    currentAccount == null ? "Not signed in" : currentAccount.getEmail()
            );
        }
        private void openAccountChooser() {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                currentAccount = null;
                txtAccountStatus.setText("Not signed in");

                startActivityForResult(
                        googleSignInClient.getSignInIntent(),
                        RC_SIGN_IN
                );
            });
        }
        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == RC_SIGN_IN) {
                try {
                    currentAccount = GoogleSignIn
                            .getSignedInAccountFromIntent(data)
                            .getResult(Exception.class);

                    txtAccountStatus.setText(currentAccount.getEmail());

                    Toast.makeText(
                            this,
                            "Signed in as " + currentAccount.getEmail(),
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {
                    Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void setupChips() {
            chipHigh.setOnClickListener(v -> filter(70, 100));
            chipMedium.setOnClickListener(v -> filter(40, 69));
            chipLow.setOnClickListener(v -> filter(0, 39));
        }

        /**
         * IMPORTANT:
         * - All files UNCHECKED by default
         * - Testing-safe behaviour
         */
        private void loadFilesAsync() {
            new Thread(() -> {
                FilePriorityEngine engine = new FilePriorityEngine(this);
                List<FileModel> scanned = new FileScanner().scanAllFiles();
                engine.assignPriorities(scanned, this);

                runOnUiThread(() -> {
                    allFiles.clear();
                    filteredFiles.clear();

                    for (FileModel f : scanned) {
                        if (!f.isDirectory() && allFiles.size() < 200) {

                            // 🔴 CRITICAL CHANGE (TESTING MODE)
                            f.setSelected(false); // UN-TICK ALL FILES

                            allFiles.add(f);
                            filteredFiles.add(f);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    txtProgress.setText("Files ready (none selected)");
                });
            }).start();
        }

        private void filter(int min, int max) {
            filteredFiles.clear();
            for (FileModel f : allFiles) {
                if (f.getPriority() >= min && f.getPriority() <= max) {
                    filteredFiles.add(f);
                }
            }
            adapter.notifyDataSetChanged();
        }

        private void startBackup() {
            if (currentAccount == null) {
                Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show();
                return;
            }

            List<FileModel> selected = new ArrayList<>();
            for (FileModel f : allFiles) {
                if (f.isSelected()) selected.add(f);
            }

            if (selected.isEmpty()) {
                Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
                return;
            }

            uploadManager = new UploadManager(this, currentAccount, selected, this);
            uploadOverlay.setVisibility(View.VISIBLE);
            uploadManager.start();
        }

        private void hideOverlay() {
            uploadOverlay.setVisibility(View.GONE);
        }

        // ===== Upload callbacks =====

        @Override
        public void onFileProgress(FileModel file, int progress) {
            progressUpload.setProgress(progress);
        }

        @Override
        public void onFileStateChanged(FileModel file, UploadState state) {}

        @Override
        public void onGlobalProgress(int completed, int total) {
            txtFileCount.setText(completed + " / " + total + " files uploaded");
            txtUploadInfo.setText((completed * 100 / total) + "%");
        }

        @Override
        public void onCompleted() {
            hideOverlay();
            txtProgress.setText("Backup completed");
        }
    }
