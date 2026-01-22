package com.example.gptbackup.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.adapter.FileAdapter;
import com.example.gptbackup.backup.SystemStatusChecker;
import com.example.gptbackup.backup.UploadManager;
import com.example.gptbackup.backup.DriveRestUploader;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.example.gptbackup.scanner.FileScanner;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int RC_SIGN_IN = 101;

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private List<FileModel> currentFiles = new ArrayList<>();
    private File currentFolder;

    private GoogleSignInClient googleSignInClient;
    private UploadManager uploadManager;

    // UI
    private TextView txtBreadcrumb;
    private TextView txtSelectionSummary;
    private TextView txtGlobalStatus;
    private ProgressBar progressGlobal;
    private Chip chipWifi;
    private Chip chipBattery;
    private TextView txtAccountEmail;
    private ImageView imgAccount;
    private MaterialButton btnPause;
    private MaterialButton btnResume;
    private MaterialButton btnCancel;
    private MaterialButton btnBackup;

    private boolean isGrid = false;

    // ------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        txtBreadcrumb = findViewById(R.id.txtBreadcrumb);
        txtSelectionSummary = findViewById(R.id.txtSelectionSummary);
        txtGlobalStatus = findViewById(R.id.txtGlobalStatus);
        progressGlobal = findViewById(R.id.progressGlobal);
        chipWifi = findViewById(R.id.chipWifi);
        chipBattery = findViewById(R.id.chipBattery);
        txtAccountEmail = findViewById(R.id.txtAccountEmail);
        imgAccount = findViewById(R.id.imgAccount);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnCancel = findViewById(R.id.btnCancel);
        btnBackup = findViewById(R.id.btnBackup);

        btnPause.setOnClickListener(v -> {
            if (uploadManager != null) {
                uploadManager.pause();
                btnPause.setEnabled(false);
                btnResume.setEnabled(true);
            }
        });
        btnResume.setOnClickListener(v -> {
            if (uploadManager != null) {
                uploadManager.resume();
                btnPause.setEnabled(true);
                btnResume.setEnabled(false);
            }
        });
        btnCancel.setOnClickListener(v -> {
            if (uploadManager != null) {
                uploadManager.cancel();
                btnPause.setEnabled(false);
                btnResume.setEnabled(false);
                btnCancel.setEnabled(false);
            }
        });

        // ✅ Google Sign-In setup
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        //.requestIdToken(getString(R.string.server_client_id))
                        .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))
                        .build();


        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Show last signed in account if any
        GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(this);
        updateAccountUi(lastAccount);

        // Change account
        findViewById(R.id.btnChangeAccount).setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
            });
        });

        // ✅ Permission + initial folder
        if (checkPermission()) {
            openRootFolder();
        } else {
            requestPermission();
        }

        // ✅ Backup button
        btnBackup.setOnClickListener(v -> startSmartBackup());

        // System status on launch
        refreshSystemStatus();
    }

    // ---------------- PERMISSION ----------------

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openRootFolder();
        }
    }

    // ---------------- FILE BROWSER + ML ----------------

    private void openRootFolder() {
        currentFolder = Environment.getExternalStorageDirectory();
        loadFolder(currentFolder);
    }

    private void loadFolder(File folder) {
        currentFolder = folder;
        txtBreadcrumb.setText(folder.getAbsolutePath());

        FileScanner scanner = new FileScanner();
        currentFiles = scanner.listFolder(folder);

        // AI priority for visible files
        FilePriorityEngine engine = new FilePriorityEngine(this);
        engine.assignPriorities(currentFiles);

        fileAdapter = new FileAdapter(currentFiles);
        fileAdapter.setOnFileClickListener(new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClicked(FileModel file) {
                if (file.isDirectory()) {
                    loadFolder(new File(file.getPath()));
                }
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                txtSelectionSummary.setText(
                        selectedCount == 0
                                ? getString(R.string.no_files_selected)
                                : getString(R.string.files_selected, selectedCount)
                );
            }
        });
        recyclerView.setAdapter(fileAdapter);
    }

    // ---------------- SMART BACKUP ----------------

    private void startSmartBackup() {

        refreshSystemStatus();

        if (fileAdapter == null || fileAdapter.getSelectedFiles().isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    // ---------------- GOOGLE LOGIN RESULT ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);

                if (account != null) {
                    startBackupAfterLogin(account);
                } else {
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
                }

            } catch (ApiException e) {
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- DRIVE UPLOAD + MANAGER ----------------

    private void startBackupAfterLogin(GoogleSignInAccount account) {

        List<FileModel> backupList = fileAdapter.getSelectedFiles();

        if (backupList.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset upload state
        for (FileModel f : backupList) {
            f.setUploadState(UploadState.WAITING);
            f.setUploadProgress(0);
        }

        uploadManager = new UploadManager(
                this,
                account,
                backupList,
                new UploadManager.Listener() {
                    @Override
                    public void onFileProgress(FileModel file, int progress) {
                        progressGlobal.setIndeterminate(false);
                        progressGlobal.setProgress(progress);
                        recyclerView.getAdapter().notifyDataSetChanged();
                    }

                    @Override
                    public void onFileStateChanged(FileModel file, UploadState state) {
                        recyclerView.getAdapter().notifyDataSetChanged();
                    }

                    @Override
                    public void onGlobalProgress(int completed, int total) {
                        txtGlobalStatus.setText(getString(
                                R.string.upload_progress_label,
                                completed,
                                total
                        ));
                    }

                    @Override
                    public void onCompleted() {
                        btnPause.setEnabled(false);
                        btnResume.setEnabled(false);
                        btnCancel.setEnabled(false);
                        Toast.makeText(MainActivity.this,
                                "Backup completed", Toast.LENGTH_LONG).show();
                    }
                }
        );

        findViewById(R.id.layoutGlobalProgress).setVisibility(View.VISIBLE);
        progressGlobal.setIndeterminate(true);
        btnPause.setEnabled(true);
        btnResume.setEnabled(false);
        btnCancel.setEnabled(true);

        uploadManager.start();

        Toast.makeText(this,
                "Backup started (" + backupList.size() + " files)",
                Toast.LENGTH_LONG).show();
    }

    // --------------- MENU (LIST / GRID TOGGLE) ---------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_layout) {
            toggleLayout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleLayout() {
        isGrid = !isGrid;
        if (isGrid) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    // --------------- SYSTEM STATUS + ACCOUNT UI ---------------

    private void refreshSystemStatus() {
        SystemStatusChecker checker = new SystemStatusChecker(this);
        boolean wifi = checker.isWifiConnected();
        boolean batteryOk = checker.isBatteryOkay();

        chipWifi.setText(wifi
                ? getString(R.string.status_wifi_ok)
                : getString(R.string.status_wifi_off));

        chipBattery.setText(batteryOk
                ? getString(R.string.status_battery_ok)
                : getString(R.string.status_battery_low));
    }

    private void updateAccountUi(GoogleSignInAccount account) {
        if (account == null) {
            txtAccountEmail.setText(R.string.account_not_signed_in);
            imgAccount.setImageResource(R.mipmap.ic_launcher_round);
        } else {
            txtAccountEmail.setText(account.getEmail());
            imgAccount.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}
