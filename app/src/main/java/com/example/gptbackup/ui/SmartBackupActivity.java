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
import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
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

    private boolean includeAppMedia = false;
    private static final int RC_SIGN_IN = 101;

    private enum FileTypeFilter {
        ALL, IMAGES, VIDEOS, AUDIO, DOCUMENTS, APPS
    }

    private FileTypeFilter currentTypeFilter = FileTypeFilter.ALL;

    private TextView txtProgress, txtAccountStatus;
    private RecyclerView recyclerSmart;
    private MaterialButton btnBackup, btnChangeAccount;
    private Chip chipHigh, chipMedium, chipLow;

    private MaterialButton btnAll, btnImages, btnVideos, btnAudio, btnDocs, btnApps;

    private View uploadOverlay;
    private ProgressBar progressUpload;
    private TextView txtUploadInfo, txtFileCount;
    private MaterialButton btnPause, btnResume, btnCancel;

    private final List<FileModel> allFiles = new ArrayList<>();
    private final List<FileModel> filteredFiles = new ArrayList<>();
    private SmartFileAdapter adapter;

    private UploadManager uploadManager;
    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount currentAccount;

    private BackupFileDao backupFileDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_backup);

        backupFileDao = BackupDatabase.getInstance(this).backupFileDao();

        bindViews();
        setupGoogle();
        setupRecycler();
        setupChips();
        setupTypeButtons();
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

    private boolean isAppMedia(FileModel file) {
        String path = file.getPath().toLowerCase();
        return path.contains("/android/media/")
                || path.contains("/android/data/");
    }

    private boolean isThumbnailFile(FileModel file) {
        String path = file.getPath().toLowerCase();
        String name = file.getName().toLowerCase();

        if (path.contains("/.thumbnails/")
                || path.contains("/thumbnail/")
                || path.contains("/thumb/")
                || path.contains("/thumbs/")
                || path.contains("/thumbcache/")
                || path.contains("/.thumbdata/")
                || path.contains("/microthumbnail/")
                || path.contains("/.thumbnail/")) {
            return true;
        }

        return name.contains("thumb")
                || name.startsWith(".")
                || name.endsWith("_thumb.jpg")
                || name.endsWith("_thumb.png")
                || name.endsWith(".thumbnail");
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

        btnAll = findViewById(R.id.btnAll);
        btnImages = findViewById(R.id.btnImages);
        btnVideos = findViewById(R.id.btnVideos);
        btnAudio = findViewById(R.id.btnAudio);
        btnDocs = findViewById(R.id.btnDocs);
        btnApps = findViewById(R.id.btnApps);

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

    private void setupChips() {
        chipHigh.setOnClickListener(v -> filter(70, 100));
        chipMedium.setOnClickListener(v -> filter(40, 69));
        chipLow.setOnClickListener(v -> filter(0, 39));
    }

    private void setupTypeButtons() {
        btnAll.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.ALL;
            applyTypeFilter();
        });

        btnImages.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.IMAGES;
            applyTypeFilter();
        });

        btnVideos.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.VIDEOS;
            applyTypeFilter();
        });

        btnAudio.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.AUDIO;
            applyTypeFilter();
        });

        btnDocs.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.DOCUMENTS;
            applyTypeFilter();
        });

        btnApps.setOnClickListener(v -> {
            currentTypeFilter = FileTypeFilter.APPS;
            applyTypeFilter();
        });
    }

    private void loadFilesAsync() {
        new Thread(() -> {
            FilePriorityEngine engine = new FilePriorityEngine(this);
            List<FileModel> scanned = new FileScanner().scanAllFiles(true);
            engine.assignPriorities(scanned, this);

            List<FileModel> finalList = new ArrayList<>();

            for (FileModel f : scanned) {

                if (!includeAppMedia && isAppMedia(f)) continue;
                if (isThumbnailFile(f)) continue;
                if (f.isDirectory()) continue;

                BackupFileEntity record = backupFileDao.getByPath(f.getPath());

                if (record != null) {
                    boolean unchanged =
                            record.lastUploadedTime >= f.getLastModified()
                                    && record.uploadedFileSize == f.getSize();
                    if (unchanged) continue;
                }

                f.setSelected(false);
                finalList.add(f);
            }

            runOnUiThread(() -> {
                allFiles.clear();
                filteredFiles.clear();
                allFiles.addAll(finalList);
                applyTypeFilter();
                txtProgress.setText("Smart files ready");
            });

        }).start();
    }

    private void applyTypeFilter() {
        filteredFiles.clear();

        for (FileModel f : allFiles) {
            switch (currentTypeFilter) {
                case IMAGES:
                    if (f.isImage()) filteredFiles.add(f);
                    break;
                case VIDEOS:
                    if (f.isVideo()) filteredFiles.add(f);
                    break;
                case AUDIO:
                    if (f.isAudio()) filteredFiles.add(f);
                    break;
                case DOCUMENTS:
                    if (f.isDocument()) filteredFiles.add(f);
                    break;
                case APPS:
                    if (f.isAppFile()) filteredFiles.add(f);
                    break;
                default:
                    filteredFiles.add(f);
            }
        }
        adapter.notifyDataSetChanged();
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
