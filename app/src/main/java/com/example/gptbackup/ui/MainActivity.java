package com.example.gptbackup.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.adapter.FileAdapter;
import com.example.gptbackup.backup.SystemStatusChecker;
import com.example.gptbackup.backup.UploadManager;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.example.gptbackup.scanner.FileScanner;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

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
    private GoogleSignInAccount currentAccount;
    private UploadManager uploadManager;

    private TextView txtBreadcrumb, txtSelectionSummary, txtGlobalStatus, txtAccountEmail;
    private ProgressBar progressGlobal;
    private Chip chipWifi, chipBattery;
    private ImageView imgAccount;
    private MaterialButton btnPause, btnResume, btnCancel, btnBackup;

    private FilePriorityEngine priorityEngine;

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

        priorityEngine = new FilePriorityEngine(this);

        setupUploadButtons();
        setupGoogleSignIn();

        if (checkPermission()) {
            openRootFolder();
        } else {
            requestPermission();
        }

        btnBackup.setOnClickListener(v -> startSmartBackup());
        refreshSystemStatus();
    }

    private void setupUploadButtons() {
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
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        currentAccount = GoogleSignIn.getLastSignedInAccount(this);
        updateAccountUi(currentAccount);
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
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
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openRootFolder();
        }
    }

    private void openRootFolder() {
        loadFolder(Environment.getExternalStorageDirectory());
    }

    private void loadFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.canRead()) {
            Toast.makeText(this, "Cannot open folder", Toast.LENGTH_SHORT).show();
            return;
        }

        currentFolder = folder;
        txtBreadcrumb.setText(folder.getAbsolutePath());

        FileScanner scanner = new FileScanner();
        currentFiles = scanner.listFolder(folder);

        // ✅ FIXED CALL
        priorityEngine.assignPriorities(currentFiles, this);

        fileAdapter = new FileAdapter(currentFiles);
        fileAdapter.setOnFileClickListener(new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClicked(FileModel file) {
                if (file.isDirectory()) {
                    loadFolder(new File(file.getPath()));
                } else {
                    openFile(file);
                }
            }

            @Override
            public void onSelectionChanged(int count) {
                txtSelectionSummary.setText(
                        count == 0
                                ? getString(R.string.no_files_selected)
                                : getString(R.string.files_selected, count)
                );
            }
        });

        recyclerView.setAdapter(fileAdapter);
    }

    @Override
    public void onBackPressed() {
        if (currentFolder != null && currentFolder.getParentFile() != null) {
            loadFolder(currentFolder.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    private void openFile(FileModel model) {
        File file = new File(model.getPath());
        Uri uri = FileProvider.getUriForFile(
                this, getPackageName() + ".provider", file
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "No app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSmartBackup() {
        if (fileAdapter == null || fileAdapter.getSelectedFiles().isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentAccount != null) {
            startBackupAfterLogin(currentAccount);
        } else {
            startActivityForResult(
                    googleSignInClient.getSignInIntent(),
                    RC_SIGN_IN
            );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                currentAccount = task.getResult(ApiException.class);
                updateAccountUi(currentAccount);
                startBackupAfterLogin(currentAccount);
            } catch (ApiException ignored) {}
        }
    }

    private void startBackupAfterLogin(GoogleSignInAccount account) {
        List<FileModel> backupList = fileAdapter.getSelectedFiles();

        for (FileModel f : backupList) {
            f.setUploadState(UploadState.WAITING);
            f.setUploadProgress(0);
        }

        uploadManager = new UploadManager(this, account, backupList, null);
        uploadManager.start();
    }

    private void refreshSystemStatus() {
        SystemStatusChecker checker = new SystemStatusChecker(this);
        chipWifi.setText(checker.isWifiConnected()
                ? getString(R.string.status_wifi_ok)
                : getString(R.string.status_wifi_off));
        chipBattery.setText(checker.isBatteryOkay()
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
