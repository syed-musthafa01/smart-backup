package com.example.gptbackup.backup;

import android.content.Context;

import com.example.gptbackup.model.FileModel;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

public class DriveBackupManager {

    private final Drive driveService;

    public DriveBackupManager(GoogleSignInAccount account, Context context) {

        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton("https://www.googleapis.com/auth/drive.file")
                );

        credential.setSelectedAccount(account.getAccount());

        driveService = new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential
        ).setApplicationName("Smart Backup App").build();
    }

    public void uploadFiles(List<FileModel> files) {

        new Thread(() -> {
            for (FileModel f : files) {
                try {
                    uploadSingleFile(f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void uploadSingleFile(FileModel fileModel) throws Exception {

        java.io.File localFile = new java.io.File(fileModel.getPath());

        File metadata = new File();
        metadata.setName(localFile.getName());

        com.google.api.client.http.FileContent mediaContent =
                new com.google.api.client.http.FileContent(null, localFile);

        driveService.files().create(metadata, mediaContent)
                .setFields("id")
                .execute();

        System.out.println("Uploaded: " + localFile.getName());
    }
}
