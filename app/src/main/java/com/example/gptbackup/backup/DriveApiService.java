package com.example.gptbackup.backup;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.example.gptbackup.model.ManifestModel;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.http.ByteArrayContent;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class DriveApiService {

    private final Drive driveService;
    private final Gson gson;
    private static final String ROOT_FOLDER_NAME = "SmartMobileBackup";

    public DriveApiService(Context context, GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());

        this.driveService = new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("SmartMobileBackup")
                .build();
        this.gson = new Gson();
    }

    public Drive getDriveService() {
        return driveService;
    }

    public Map<String, String> initializeEnvironment() throws IOException {
        Map<String, String> folderMap = new HashMap<>();

        // 1. Root Folder
        String rootId = getOrCreateFolder(ROOT_FOLDER_NAME, null);
        folderMap.put("root", rootId);

        // 2. Subfolders
        folderMap.put("Images", getOrCreateFolder("Images", rootId));
        folderMap.put("Videos", getOrCreateFolder("Videos", rootId));
        folderMap.put("Documents", getOrCreateFolder("Documents", rootId));
        folderMap.put("Audio", getOrCreateFolder("Audio", rootId));
        folderMap.put("AI_Priority", getOrCreateFolder("AI_Priority", rootId));

        return folderMap;
    }

    private String getOrCreateFolder(String folderName, String parentId) throws IOException {
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName + "' and trashed=false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }

        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create folder
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            folderMetadata.setParents(Collections.singletonList(parentId));
        }

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();
        return folder.getId();
    }

    public ManifestModel downloadManifest(String rootFolderId) throws IOException {
        String query = "name='manifest.json' and '" + rootFolderId + "' in parents and trashed=false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() == null || result.getFiles().isEmpty()) {
            return null; // Not found
        }

        String fileId = result.getFiles().get(0).getId();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);

        String json = outputStream.toString("UTF-8");
        return gson.fromJson(json, ManifestModel.class);
    }

    public void uploadManifest(ManifestModel manifest, String rootFolderId) throws IOException {
        String json = gson.toJson(manifest);
        ByteArrayContent mediaContent = ByteArrayContent.fromString("application/json", json);

        String query = "name='manifest.json' and '" + rootFolderId + "' in parents and trashed=false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            // Update existing
            String fileId = result.getFiles().get(0).getId();
            driveService.files().update(fileId, null, mediaContent).execute();
        } else {
            // Create new
            File fileMetadata = new File();
            fileMetadata.setName("manifest.json");
            fileMetadata.setParents(Collections.singletonList(rootFolderId));
            fileMetadata.setMimeType("application/json");

            driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
        }
    }

    public List<File> fetchAllFilesMetadata(String folderId) throws IOException {
        List<File> allFiles = new ArrayList<>();
        String pageToken = null;

        do {
            FileList result = driveService.files().list()
                    .setQ("trashed=false and '" + folderId + "' in parents")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .setPageSize(1000)
                    .execute();

            if (result.getFiles() != null) {
                allFiles.addAll(result.getFiles());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return allFiles;
    }
}
