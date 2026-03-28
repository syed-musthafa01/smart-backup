package com.example.gptbackup.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.model.ManifestFile;
import com.example.gptbackup.model.ManifestModel;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDriveSyncManager {

    private static final String TAG = "GoogleDriveSyncManager";
    private static final String PREF_NAME = "DriveSyncPrefs";
    private static final String KEY_ROOT_FOLDER_ID = "root_folder_id";

    private final Context context;
    private final DriveApiService apiService;
    private final BackupFileDao backupDao;
    private final SharedPreferences prefs;
    private final Gson gson;

    private Map<String, String> folderIds = new HashMap<>();

    public GoogleDriveSyncManager(Context context, GoogleSignInAccount account) {
        this.context = context.getApplicationContext();
        this.apiService = new DriveApiService(context, account);
        this.backupDao = BackupDatabase.getInstance(context).backupFileDao();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public void startSync(SyncCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 1. Initialize environment (find/create folders)
                folderIds = apiService.initializeEnvironment();
                String rootId = folderIds.get("root");
                prefs.edit().putString(KEY_ROOT_FOLDER_ID, rootId).apply();

                // 2. Fetch remote manifest
                ManifestModel remoteManifest = apiService.downloadManifest(rootId);
                if (remoteManifest == null) {
                    remoteManifest = new ManifestModel();
                }

                // 3. Save local copy
                saveLocalManifest(remoteManifest);

                // 4. Fetch Drive metadata (from all subfolders)
                Set<String> driveFileIds = new HashSet<>();
                for (String folderId : folderIds.values()) {
                    List<File> files = apiService.fetchAllFilesMetadata(folderId);
                    for (File f : files) {
                        driveFileIds.add(f.getId());
                    }
                }

                // 5. Compare Manifest vs Drive metadata
                boolean manifestChanged = false;
                List<ManifestFile> manifestFiles = remoteManifest.getFiles();
                
                for (int i = manifestFiles.size() - 1; i >= 0; i--) {
                    ManifestFile mf = manifestFiles.get(i);
                    if (mf.getFileId() != null && !driveFileIds.contains(mf.getFileId())) {
                        // File is deleted in Drive
                        mf.setBackupStatus("MISSING");
                        manifestChanged = true;

                        // Update local DB
                        BackupFileEntity entity = backupDao.getByPath(mf.getFilePath());
                        if (entity != null) {
                            entity.backupStatus = "NOT_BACKED_UP"; // or MISSING
                            backupDao.insertOrUpdate(entity);
                        }
                    }
                }

                // If deletions found, sync local copy and upload fixed manifest
                if (manifestChanged) {
                    saveLocalManifest(remoteManifest);
                    apiService.uploadManifest(remoteManifest, rootId);
                }

                callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                callback.onError(e);
            }
        });
    }

    public Map<String, String> getFolderIds() {
        return folderIds;
    }

    public String getRootFolderId() {
        return prefs.getString(KEY_ROOT_FOLDER_ID, null);
    }

    public ManifestModel getLocalManifest() {
        java.io.File file = new java.io.File(context.getFilesDir(), "manifest.json");
        if (!file.exists()) return new ManifestModel();
        
        try (FileReader reader = new FileReader(file)) {
            ManifestModel model = gson.fromJson(reader, ManifestModel.class);
            return model != null ? model : new ManifestModel();
        } catch (IOException e) {
            Log.e(TAG, "Error reading local manifest", e);
            return new ManifestModel();
        }
    }

    public void saveLocalManifest(ManifestModel manifest) {
        java.io.File file = new java.io.File(context.getFilesDir(), "manifest.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(manifest, writer);
        } catch (IOException e) {
            Log.e(TAG, "Error saving local manifest", e);
        }
    }

    public void updateAndUploadManifest(ManifestModel updatedManifest) throws IOException {
        saveLocalManifest(updatedManifest);
        String rootId = getRootFolderId();
        if (rootId != null) {
            apiService.uploadManifest(updatedManifest, rootId);
        }
    }
}
