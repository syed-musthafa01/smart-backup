package com.example.gptbackup.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.gptbackup.R;
import com.example.gptbackup.adapter.SmartFileAdapter;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.ai.UserPreferenceManager;
import com.example.gptbackup.backup.GoogleDriveSyncManager;
import com.example.gptbackup.backup.UploadForegroundService;
import com.example.gptbackup.backup.UploadManager;
import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.example.gptbackup.scanner.FileScanner;
import com.example.gptbackup.scanner.MediaStoreLoader;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class SmartBackupActivity extends AppCompatActivity
        implements UploadManager.Listener {

    private static final int RC_SIGN_IN = 101;

    private enum FileTypeFilter {
        ALL, PHOTOS, VIDEOS, AUDIO, FILES
    }

    private FileTypeFilter currentTypeFilter = FileTypeFilter.ALL;
    
    private enum PriorityFilter { ALL, HIGH, MEDIUM, LOW }
    private PriorityFilter currentPriorityFilter = PriorityFilter.ALL;

    private TextView txtProgress, txtAccountStatus;
    private ImageView imgAccount;
    private RecyclerView recyclerSmart;
    private ExtendedFloatingActionButton btnBackup;
    private MaterialButton btnChangeAccount, btnSelectAll;
    private MaterialButton btnSettings, btnOpenFileManager; 
    private ChipGroup chipGroupFilter;
    private LinearProgressIndicator scanProgressBar;

    private final List<FileModel> allFiles = new ArrayList<>();
    private final List<FileModel> filteredFiles = new ArrayList<>();
    private SmartFileAdapter adapter;

    private UploadManager uploadManager;
    private GoogleDriveSyncManager syncManager;
    private boolean isSyncing = false;
    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount currentAccount;

    private BackupFileDao backupFileDao;
    private boolean isAllSelected = false;
    private boolean storageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_backup);

        // ── First-launch onboarding check ──
        if (!UserPreferenceManager.isOnboardingComplete(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        backupFileDao = BackupDatabase.getInstance(this).backupFileDao();

        bindViews();
        setupGoogle();
        setupRecycler();
        setupFilterChips();
        setupPriorityChips();

        if (!hasFullStorageAccess()) {
            requestFullStorageAccess();
        } else {
            storageLoaded = true;
            loadFilesAsync();
        }

        com.example.gptbackup.backup.AutoBackupTriggerController.getInstance(this).applySettings();

        if (btnChangeAccount != null) btnChangeAccount.setOnClickListener(v -> openAccountChooser());
        if (imgAccount != null) imgAccount.setOnClickListener(v -> openAccountChooser());
        if (btnBackup != null) btnBackup.setOnClickListener(v -> startBackup());

        if (btnOpenFileManager != null) {
            btnOpenFileManager.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> 
                    startActivity(new Intent(this, SettingsActivity.class)));
        }

        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(v -> {
                isAllSelected = !isAllSelected;
                adapter.selectAll(isAllSelected);
                btnSelectAll.setText(isAllSelected ? "Deselect All" : "Select All");
            });
        }

        setupScrollBehavior();
    }

    private void setupScrollBehavior() {
        if (recyclerSmart == null || btnBackup == null) return;
        
        recyclerSmart.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    btnBackup.extend();
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    btnBackup.shrink();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!storageLoaded && hasFullStorageAccess()) {
            storageLoaded = true;
            loadFilesAsync();
        }
    }

    private boolean hasFullStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();
    }

    private void requestFullStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void bindViews() {
        txtProgress = findViewById(R.id.txtProgress);
        txtAccountStatus = findViewById(R.id.txtAccountStatus);
        imgAccount = findViewById(R.id.imgAccount);
        recyclerSmart = findViewById(R.id.recyclerSmart);
        btnBackup = findViewById(R.id.btnBackupHigh);
        btnChangeAccount = findViewById(R.id.btnChangeAccount);
        btnSettings = findViewById(R.id.btnSettings);
        btnOpenFileManager = findViewById(R.id.btnOpenFileManager);
        scanProgressBar = findViewById(R.id.scanProgressBar);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        btnSelectAll = findViewById(R.id.btnSelectAll);
    }

    private void setupRecycler() {
        if (recyclerSmart == null) return;
        recyclerSmart.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmartFileAdapter(filteredFiles);
        adapter.setSelectionListener(selectedCount -> {
            if (btnSelectAll != null) {
                btnSelectAll.setVisibility(selectedCount > 0 ? View.VISIBLE : View.GONE);
                if (selectedCount == 0) isAllSelected = false;
                btnSelectAll.setText(isAllSelected ? "Deselect All" : "Select All");
            }
        });
        recyclerSmart.setAdapter(adapter);
    }

    private void setupGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        currentAccount = GoogleSignIn.getLastSignedInAccount(this);
        updateAccountUi(currentAccount);
    }

    private void updateAccountUi(@Nullable GoogleSignInAccount account) {
        if (account == null) {
            if (txtAccountStatus != null) txtAccountStatus.setText("Not signed in");
            if (imgAccount != null) {
                imgAccount.setImageResource(R.drawable.ic_launcher_foreground);
                imgAccount.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_black)));
            }
        } else {
            if (txtAccountStatus != null) txtAccountStatus.setText(account.getEmail());
            if (imgAccount != null) {
                if (account.getPhotoUrl() != null) {
                    imgAccount.setImageTintList(null);
                    Glide.with(this)
                            .load(account.getPhotoUrl())
                            .circleCrop()
                            .into(imgAccount);
                } else {
                    imgAccount.setImageResource(R.drawable.ic_launcher_foreground);
                    imgAccount.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_black)));
                }
            }
            
            // Trigger Drive Sync
            syncManager = new GoogleDriveSyncManager(this, account);
            isSyncing = true;
            if (txtProgress != null) txtProgress.setText("Syncing metadata...");
            syncManager.startSync(new GoogleDriveSyncManager.SyncCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isSyncing = false;
                        if (txtProgress != null) txtProgress.setText("Sync complete.");
                    });
                }
                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        isSyncing = false;
                        if (txtProgress != null) txtProgress.setText("Sync failed.");
                        Toast.makeText(SmartBackupActivity.this, "Drive Sync Failed", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void openAccountChooser() {
        if (googleSignInClient == null) return;
        if (txtAccountStatus != null) txtAccountStatus.setText("Signing out...");
        
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            currentAccount = null;
            updateAccountUi(null);
            startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            currentAccount = completedTask.getResult(ApiException.class);
            updateAccountUi(currentAccount);
            Toast.makeText(this, "Welcome " + currentAccount.getEmail(), Toast.LENGTH_SHORT).show();
        } catch (ApiException e) {
            updateAccountUi(null);
            Toast.makeText(this, "Sign in failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupFilterChips() {
        if (chipGroupFilter == null) return;
        
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            
            if (checkedId == R.id.chipAll) currentTypeFilter = FileTypeFilter.ALL;
            else if (checkedId == R.id.chipPhotos) currentTypeFilter = FileTypeFilter.PHOTOS;
            else if (checkedId == R.id.chipVideos) currentTypeFilter = FileTypeFilter.VIDEOS;
            else if (checkedId == R.id.chipAudio) currentTypeFilter = FileTypeFilter.AUDIO;
            else if (checkedId == R.id.chipFiles) currentTypeFilter = FileTypeFilter.FILES;
            
            applyFilters();
        });
    }

    private void setupPriorityChips() {
        ChipGroup priorityGroup = findViewById(R.id.priorityChipGroup);
        if (priorityGroup != null) {
            priorityGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    currentPriorityFilter = PriorityFilter.ALL;
                } else {
                    int checkedId = checkedIds.get(0);
                    if (checkedId == R.id.chipHigh) currentPriorityFilter = PriorityFilter.HIGH;
                    else if (checkedId == R.id.chipMedium) currentPriorityFilter = PriorityFilter.MEDIUM;
                    else if (checkedId == R.id.chipLow) currentPriorityFilter = PriorityFilter.LOW;
                }
                applyFilters();
            });
        }
    }

    private void loadFilesAsync() {
        if (scanProgressBar != null) scanProgressBar.setVisibility(View.VISIBLE);
        if (txtProgress != null) txtProgress.setText("Scanning device...");

        new Thread(() -> {
            FilePriorityEngine engine = new FilePriorityEngine(this);
            List<FileModel> scanned = new ArrayList<>();
            scanned.addAll(MediaStoreLoader.loadImages(this));
            scanned.addAll(MediaStoreLoader.loadVideos(this));
            List<FileModel> fsFiles = new FileScanner().scanAllFiles(true);
            for (FileModel f : fsFiles) { if (!f.isImage() && !f.isVideo()) scanned.add(f); }
            if (txtProgress != null) {
                runOnUiThread(() -> txtProgress.setText("Running AI analysis..."));
            }
            engine.assignPriorities(scanned, this);
            Log.d("SmartBackup", "AI analysis complete for " + scanned.size() + " files");
            
            List<FileModel> finalList = new ArrayList<>();
            for (FileModel f : scanned) {
                if ("other".equals(f.getType())) continue;
                if (f.isDirectory()) continue;
                
                String path = f.getPath();
                if (path != null) {
                    BackupFileEntity record = backupFileDao.getByPath(path);
                    if (record != null) {
                        if (record.lastUploadedTime >= f.getLastModified() && record.uploadedFileSize == f.getSize()) continue;
                    }
                }
                f.setSelected(false); 
                finalList.add(f);
            }

            runOnUiThread(() -> {
                allFiles.clear();
                allFiles.addAll(finalList);
                if (scanProgressBar != null) scanProgressBar.setVisibility(View.GONE);
                if (txtProgress != null) txtProgress.setText(finalList.size() + " files found");

                filteredFiles.clear();
                if (currentTypeFilter == FileTypeFilter.ALL && currentPriorityFilter == PriorityFilter.ALL) {
                    filteredFiles.addAll(finalList);
                } else {
                    applyFilters();
                    return;
                }
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void applyFilters() {
        filteredFiles.clear();
        for (FileModel f : allFiles) {
            boolean matchesType = false;
            switch (currentTypeFilter) {
                case ALL: matchesType = true; break;
                case PHOTOS: matchesType = f.isImage(); break;
                case VIDEOS: matchesType = f.isVideo(); break;
                case AUDIO: matchesType = f.isAudio(); break;
                case FILES: matchesType = f.isDocument(); break;
            }

            boolean matchesPriority = false;
            int p = f.getPriority();
            switch (currentPriorityFilter) {
                case ALL: matchesPriority = true; break;
                case HIGH: matchesPriority = (p >= 70 && p <= 100); break;
                case MEDIUM: matchesPriority = (p >= 40 && p <= 69); break;
                case LOW: matchesPriority = (p >= 0 && p <= 39); break;
            }

            if (matchesType && matchesPriority) {
                filteredFiles.add(f);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void startBackup() {
        if (currentAccount == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isSyncing) {
            Toast.makeText(this, "Please wait for sync to finish", Toast.LENGTH_SHORT).show();
            return;
        }

        if (syncManager == null) {
            syncManager = new GoogleDriveSyncManager(this, currentAccount);
            Toast.makeText(this, "Syncing metadata, please try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        List<FileModel> selected = new ArrayList<>();
        for (FileModel f : allFiles) { if (f.isSelected()) selected.add(f); }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select files to backup", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, UploadForegroundService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);

        uploadManager = new UploadManager(this, currentAccount, selected, this, syncManager);
        if (adapter != null) adapter.setBackupRunning(true);
        uploadManager.start();
    }

    private void hideOverlay() {
        // No-op as requested
    }

    @Override
    public void onFileProgress(FileModel file, int progress) {
        int index = filteredFiles.indexOf(file);
        if (index != -1 && adapter != null) adapter.notifyItemChanged(index);
    }

    @Override
    public void onFileStateChanged(FileModel file, UploadState state) {
        int index = filteredFiles.indexOf(file);
        if (index != -1 && adapter != null) adapter.notifyItemChanged(index);
    }

    @Override
    public void onGlobalProgress(int completed, int total) {
        if (txtProgress != null) txtProgress.setText("Backing up: " + completed + " / " + total);
    }

    @Override
    public void onCompleted() {
        if (txtProgress != null) txtProgress.setText("All files secured");
        if (adapter != null) adapter.setBackupRunning(false);
        stopService(new Intent(this, UploadForegroundService.class));
    }
}
