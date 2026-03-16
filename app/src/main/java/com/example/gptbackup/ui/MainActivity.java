package com.example.gptbackup.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.OnBackPressedCallback;

import com.bumptech.glide.Glide;
import com.example.gptbackup.R;
import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.adapter.FileAdapter;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements UploadManager.Listener {

    private static final int RC_SIGN_IN = 101;

    private boolean storageLoaded = false;

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private final List<FileModel> currentFiles = new ArrayList<>();
    private File currentFolder;

    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount currentAccount;
    private UploadManager uploadManager;

    private TextView txtGlobalStatus, txtAccountEmail;
    private ProgressBar progressGlobal;
    private ImageView imgAccount;
    private MaterialButton btnChangeAccount;

    private ExtendedFloatingActionButton btnBackup;

    private FilePriorityEngine priorityEngine;

    private boolean doubleBackToExitPressedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        txtGlobalStatus = findViewById(R.id.txtGlobalStatus);
        progressGlobal = findViewById(R.id.progressGlobal);
        txtAccountEmail = findViewById(R.id.txtAccountEmail);
        imgAccount = findViewById(R.id.imgAccount);
        btnChangeAccount = findViewById(R.id.btnChangeAccount);

        btnBackup = findViewById(R.id.btnBackup);

        priorityEngine = new FilePriorityEngine(this);

        setupGoogleSignIn();

        imgAccount.setOnClickListener(v -> openAccountChooser());
        btnChangeAccount.setOnClickListener(v -> openAccountChooser());

        if (!hasFullStorageAccess()) {
            requestFullStorageAccess();
        }

        btnBackup.setOnClickListener(v -> startSmartBackup());

        setupScrollBehavior();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFolder != null && currentFolder.getParentFile() != null && 
                    !currentFolder.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                    loadFolder(currentFolder.getParentFile());
                } else {
                    if (doubleBackToExitPressedOnce) {
                        startActivity(new Intent(MainActivity.this, SmartBackupActivity.class));
                        return;
                    }

                    doubleBackToExitPressedOnce = true;
                    Toast.makeText(MainActivity.this, "Press BACK again to enter Smart Backup", Toast.LENGTH_SHORT).show();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                }
            }
        });
    }

    private void setupScrollBehavior() {
        if (recyclerView == null || btnBackup == null) return;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

    /* ================= GOOGLE SIGN-IN ================= */

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
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
                    RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                currentAccount = task.getResult(ApiException.class);
                updateAccountUi(currentAccount);
            } catch (ApiException e) {
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateAccountUi(GoogleSignInAccount account) {
        if (account == null) {
            txtAccountEmail.setText(R.string.account_not_signed_in);
            imgAccount.setImageResource(R.drawable.ic_launcher_foreground);
            imgAccount.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_black)));
        } else {
            txtAccountEmail.setText(account.getEmail());
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
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
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

        FileScanner scanner = new FileScanner();
        List<FileModel> files = scanner.listFolder(folder);
        currentFiles.clear();
        currentFiles.addAll(files);
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
                    RC_SIGN_IN);
        }
    }

    private void startBackupAfterLogin(GoogleSignInAccount account) {
        uploadManager = new UploadManager(
                this,
                account,
                fileAdapter.getSelectedFiles(),
                this);

        uploadManager.start();
        progressGlobal.setVisibility(ProgressBar.VISIBLE);
        txtGlobalStatus.setText("Starting backup...");
    }

    /* ================= UPLOAD CALLBACKS ================= */

    @Override
    public void onFileProgress(FileModel file, int progress) {
        runOnUiThread(() -> {
            int index = currentFiles.indexOf(file);
            if (index != -1 && fileAdapter != null) {
                fileAdapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void onGlobalProgress(int completed, int total) {
        runOnUiThread(() -> {
            progressGlobal.setMax(total);
            progressGlobal.setProgress(completed);
            txtGlobalStatus.setText("Uploaded " + completed + " / " + total);
        });
    }

    @Override
    public void onFileStateChanged(FileModel file, UploadState state) {
        runOnUiThread(() -> {
            int index = currentFiles.indexOf(file);
            if (index != -1 && fileAdapter != null) {
                fileAdapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void onCompleted() {
        runOnUiThread(() -> {
            txtGlobalStatus.setText("Backup completed");
            progressGlobal.setVisibility(ProgressBar.GONE);
        });
    }
}
