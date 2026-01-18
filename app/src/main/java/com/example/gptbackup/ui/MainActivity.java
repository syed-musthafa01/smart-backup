package com.example.gptbackup.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.adapter.FileAdapter;
import com.example.gptbackup.backup.DriveBackupManager;
import com.example.gptbackup.backup.SmartBackupManager;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.scanner.FileScanner;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int RC_SIGN_IN = 101;

    private List<FileModel> scannedFiles;
    private GoogleSignInClient googleSignInClient;

    // ------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Google Sign-In Setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ✅ Storage Permission + Scan
        if (checkPermission()) {
            scanFiles();
        } else {
            requestPermission();
        }

        // ✅ Start Smart Backup Button
        findViewById(R.id.btnBackup).setOnClickListener(v -> startSmartBackup());
    }

    // ---------------- PERMISSION ----------------

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanFiles();
            } else {
                Toast.makeText(this,
                        "Storage permission required",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------- FILE SCAN + ML ----------------

    private void scanFiles() {

        FileScanner scanner = new FileScanner();
        List<FileModel> files = scanner.scanAllFiles();
        scannedFiles = files;

        // ✅ ML Priority
        FilePriorityEngine engine = new FilePriorityEngine(this);
        engine.assignPriorities(files);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new FileAdapter(files));
    }

    // ---------------- SMART BACKUP ----------------

    private void startSmartBackup() {

        if (scannedFiles == null || scannedFiles.isEmpty()) {
            Toast.makeText(this, "No files to backup", Toast.LENGTH_SHORT).show();
            return;
        }

        // 👉 Start Google Login
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    // ---------------- GOOGLE LOGIN RESULT ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                startDriveBackup(account);
            } catch (ApiException e) {
                Toast.makeText(this, "Google Login Failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- DRIVE BACKUP START ----------------

    private void startDriveBackup(GoogleSignInAccount account) {

        SmartBackupManager manager = new SmartBackupManager(this);

        boolean allowLowPriority = true;

        List<FileModel> backupList =
                manager.selectFilesForBackup(scannedFiles, allowLowPriority);

        DriveBackupManager driveManager =
                new DriveBackupManager(account, this);

        driveManager.uploadFiles(backupList);

        Toast.makeText(this,
                "Uploading " + backupList.size() + " files to Drive...",
                Toast.LENGTH_LONG).show();
    }
}
