package com.example.gptbackup.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.adapter.SmartFileAdapter;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.scanner.FileScanner;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SmartBackupActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 101;

    private RecyclerView recyclerSmart;
    private SmartFileAdapter adapter;

    private Chip chipHigh, chipMedium, chipLow;
    private FilePriorityEngine priorityEngine;

    private final List<FileModel> allSmartFiles = new ArrayList<>();
    private final List<FileModel> filteredFiles = new ArrayList<>();

    // 🔹 Account
    private GoogleSignInClient googleSignInClient;
    private TextView txtAccountStatus;
    private MaterialButton btnChangeAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_backup);

        // 🔹 Account UI
        txtAccountStatus = findViewById(R.id.txtAccountStatus);
        btnChangeAccount = findViewById(R.id.btnChangeAccount);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        updateAccountUI();

        btnChangeAccount.setOnClickListener(v -> {
            startActivityForResult(
                    googleSignInClient.getSignInIntent(),
                    RC_SIGN_IN
            );
        });

        // 🔹 File Manager
        ImageButton btnOpenFileManager = findViewById(R.id.btnOpenFileManager);
        btnOpenFileManager.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class))
        );

        recyclerSmart = findViewById(R.id.recyclerSmart);
        recyclerSmart.setLayoutManager(new LinearLayoutManager(this));

        chipHigh = findViewById(R.id.chipHigh);
        chipMedium = findViewById(R.id.chipMedium);
        chipLow = findViewById(R.id.chipLow);

        priorityEngine = new FilePriorityEngine(this);

        loadSmartFiles();
        setupChipClicks();
    }

    // ---------------- ACCOUNT RESULT ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            try {
                GoogleSignInAccount account =
                        GoogleSignIn.getSignedInAccountFromIntent(data)
                                .getResult(ApiException.class);

                updateAccountUI();

            } catch (Exception e) {
                Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateAccountUI() {
        GoogleSignInAccount account =
                GoogleSignIn.getLastSignedInAccount(this);

        if (account == null) {
            txtAccountStatus.setText("Not signed in");
            btnChangeAccount.setText("SIGN IN");
        } else {
            txtAccountStatus.setText(account.getEmail());
            btnChangeAccount.setText("CHANGE");
        }
    }

    // ---------------- LOAD FILES ----------------

    private void loadSmartFiles() {
        FileScanner scanner = new FileScanner();
        List<FileModel> scannedFiles = scanner.scanAllFiles();

        priorityEngine.assignPriorities(scannedFiles, this);

        scannedFiles.sort((a, b) ->
                Integer.compare(b.getPriority(), a.getPriority())
        );

        allSmartFiles.clear();

        for (FileModel file : scannedFiles) {
            if (!file.isDirectory()) {
                file.setSelected(true); // ✅ default checked
                allSmartFiles.add(file);
                if (allSmartFiles.size() >= 200) break;
            }
        }

        filteredFiles.clear();
        filteredFiles.addAll(allSmartFiles);

        adapter = new SmartFileAdapter(filteredFiles);
        recyclerSmart.setAdapter(adapter);

    }

    // ---------------- FILE PREVIEW ----------------

    private void openFileExternally(FileModel file) {
        File f = new File(file.getPath());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                Uri.fromFile(f),
                "*/*"
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(Intent.createChooser(intent, "Open with"));
    }

    // ---------------- CHIPS ----------------

    private void setupChipClicks() {
        chipHigh.setOnClickListener(v -> {
            selectChip(chipHigh);
            filterByPriority(70, 100);
        });

        chipMedium.setOnClickListener(v -> {
            selectChip(chipMedium);
            filterByPriority(40, 69);
        });

        chipLow.setOnClickListener(v -> {
            selectChip(chipLow);
            filterByPriority(0, 39);
        });
    }

    private void filterByPriority(int min, int max) {
        filteredFiles.clear();

        for (FileModel f : allSmartFiles) {
            if (f.getPriority() >= min && f.getPriority() <= max) {
                filteredFiles.add(f);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void selectChip(Chip selected) {
        chipHigh.setChecked(false);
        chipMedium.setChecked(false);
        chipLow.setChecked(false);
        selected.setChecked(true);
    }
}
