package com.example.gptbackup.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

public class MainActivity extends AppCompatActivity
        implements UploadManager.Listener {

    private static final int RC_SIGN_IN = 101;

    private boolean storageLoaded = false;

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

        imgAccount.setOnClickListener(v -> openAccountChooser());
        txtAccountEmail.setOnClickListener(v -> openAccountChooser());

        if (!hasFullStorageAccess()) {
            requestFullStorageAccess();
        }

        findViewById(R.id.btnOpenSmart)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, SmartBackupActivity.class)));

        btnBackup.setOnClickListener(v -> startSmartBackup());

        refreshSystemStatus();
    }

    /* ================= GOOGLE SIGN-IN ================= */

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

    private void openAccountChooser() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            currentAccount = null;
            updateAccountUi(null);
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
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                currentAccount = task.getResult(ApiException.class);
                updateAccountUi(currentAccount);
                Toast.makeText(this,
                        "Signed in as " + currentAccount.getEmail(),
                        Toast.LENGTH_SHORT).show();
            } catch (ApiException e) {
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateAccountUi(GoogleSignInAccount account) {
        if (account == null) {
            txtAccountEmail.setText(R.string.account_not_signed_in);
        } else {
            txtAccountEmail.setText(account.getEmail());
        }
        imgAccount.setImageResource(R.mipmap.ic_launcher_round);
    }

    /* ================= STORAGE ================= */

    private boolean hasFullStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();
    }

    private void requestFullStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        }
    }

    /* ================= FILE BROWSER ================= */

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
        priorityEngine.assignPriorities(currentFiles, this);

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
                txtSelectionSummary.setText(selectedCount + " files selected");
            }
        });

        recyclerView.setAdapter(fileAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!storageLoaded && hasFullStorageAccess()) {
            storageLoaded = true;
            openRootFolder();
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

    private void startBackupAfterLogin(GoogleSignInAccount account) {
        uploadManager = new UploadManager(
                this,
                account,
                fileAdapter.getSelectedFiles(),
                this
        );

        // ✅ START UPLOAD THREAD (THIS WAS MISSING)
        uploadManager.start();

        // ✅ Make progress UI visible immediately
        progressGlobal.setVisibility(ProgressBar.VISIBLE);
        txtGlobalStatus.setText("Starting backup...");
    }

    /* ================= STATUS ================= */

    private void refreshSystemStatus() {
        SystemStatusChecker checker = new SystemStatusChecker(this);
        chipWifi.setText(checker.isWifiConnected()
                ? getString(R.string.status_wifi_ok)
                : getString(R.string.status_wifi_off));
        chipBattery.setText(checker.isBatteryOkay()
                ? getString(R.string.status_battery_ok)
                : getString(R.string.status_battery_low));
    }

    @Override
    public void onBackPressed() {
        if (currentFolder != null && currentFolder.getParentFile() != null) {
            loadFolder(currentFolder.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    /* ================= UPLOAD CALLBACKS ================= */

    @Override
    public void onFileProgress(FileModel file, int progress) {}

    @Override
    public void onGlobalProgress(int completed, int total) {
        progressGlobal.setMax(total);
        progressGlobal.setProgress(completed);
        txtGlobalStatus.setText("Uploaded " + completed + " / " + total);
    }

    @Override
    public void onFileStateChanged(FileModel file, UploadState state) {}

    @Override
    public void onCompleted() {
        txtGlobalStatus.setText("Backup completed");
        progressGlobal.setVisibility(ProgressBar.GONE);
    }

    private void setupUploadButtons() {
        btnPause.setOnClickListener(v -> {
            if (uploadManager != null) uploadManager.pause();
        });
        btnResume.setOnClickListener(v -> {
            if (uploadManager != null) uploadManager.resume();
        });
        btnCancel.setOnClickListener(v -> {
            if (uploadManager != null) uploadManager.cancel();
        });
    }
}
