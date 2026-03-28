package com.example.gptbackup.backup;

import android.content.Context;
import android.util.Log;

import com.example.gptbackup.model.FileModel;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DriveRestUploader {

    private static final String TAG = "DriveRestUploader";

    public interface ProgressCallback {
        void onProgress(int progress);
    }

    public interface UploadHandle {
        void pause();
        void resume();
        void cancel();
    }

    public static UploadHandle uploadToDrive(
            Context context,
            GoogleSignInAccount account,
            FileModel fileModel,
            Map<String, String> folderIds,
            ProgressCallback callback,
            AtomicBoolean globalPaused,
            AtomicBoolean globalCanceled
    ) {

        AtomicBoolean localCanceled = new AtomicBoolean(false);
        AtomicBoolean localPaused = fileModel.getPausedFlag();

        new Thread(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(account.getAccount());

                Drive driveService = new Drive.Builder(
                        new NetHttpTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName("SmartMobileBackup")
                        .build();

                String fileName;
                long fileSize;
                InputStreamContent mediaContent = null;
                FileContent fileContent = null;

                if (fileModel.getPath() != null && !fileModel.getPath().isEmpty()) {
                    java.io.File localFile = new java.io.File(fileModel.getPath());
                    fileName = localFile.getName();
                    fileSize = localFile.length();
                    fileContent = new FileContent("application/octet-stream", localFile);
                } else if (fileModel.isFromMediaStore()) {
                    fileName = fileModel.getName();
                    fileSize = fileModel.getSize();
                    InputStream inputStream = context.getContentResolver().openInputStream(fileModel.getContentUri());
                    if (inputStream != null) {
                        mediaContent = new InputStreamContent("application/octet-stream", inputStream);
                        mediaContent.setLength(fileSize);
                    } else {
                        throw new Exception("Unable to open input stream");
                    }
                } else {
                    throw new Exception("Invalid file source");
                }

                String parentId = resolveParentId(folderIds, fileModel);

                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(parentId));

                Drive.Files.Create createRequest;
                if (fileContent != null) {
                    createRequest = driveService.files().create(fileMetadata, fileContent);
                } else {
                    createRequest = driveService.files().create(fileMetadata, mediaContent);
                }

                MediaHttpUploader uploader = createRequest.getMediaHttpUploader();
                uploader.setDirectUploadEnabled(false); // Enable resumable upload
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);

                uploader.setProgressListener(new MediaHttpUploaderProgressListener() {
                    @Override
                    public void progressChanged(MediaHttpUploader uploader) throws java.io.IOException {
                        if (localCanceled.get() || globalCanceled.get()) {
                            // Can't easily cancel the Google API client mid-chunk without throwing exception,
                            // but we can just let it finish the chunk and not report.
                            // However, we just stop tracking here.
                        }

                        while ((localPaused.get() || globalPaused.get()) && !localCanceled.get() && !globalCanceled.get()) {
                            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        }

                        switch (uploader.getUploadState()) {
                            case INITIATION_STARTED:
                            case INITIATION_COMPLETE:
                                callback.onProgress(0);
                                break;
                            case MEDIA_IN_PROGRESS:
                                int progress = (int) (uploader.getProgress() * 100);
                                callback.onProgress(progress);
                                break;
                            case MEDIA_COMPLETE:
                                callback.onProgress(100);
                                break;
                            case NOT_STARTED:
                                break;
                        }
                    }
                });

                File uploadedFile = createRequest.setFields("id").execute();
                
                if (uploadedFile != null && uploadedFile.getId() != null) {
                    fileModel.setDriveFileId(uploadedFile.getId());
                    Log.d(TAG, "Uploaded: " + fileName + " with ID: " + uploadedFile.getId());
                }

            } catch (Exception e) {
                if (localCanceled.get() || globalCanceled.get()) {
                    Log.d(TAG, "Upload canceled by user");
                } else {
                    Log.e(TAG, "Upload error", e);
                }
            }
        }).start();

        return new UploadHandle() {
            @Override
            public void pause() { localPaused.set(true); }
            @Override
            public void resume() { localPaused.set(false); }
            @Override
            public void cancel() {
                localCanceled.set(true);
                resume();
            }
        };
    }

    private static String resolveParentId(Map<String, String> folders, FileModel fileModel) {
        if (fileModel.getPriority() >= 70) {
            return folders.get("AI_Priority");
        }
        String type = fileModel.getType();
        if ("image".equals(type)) return folders.get("Images");
        if ("video".equals(type)) return folders.get("Videos");
        if ("audio".equals(type)) return folders.get("Audio");
        if ("document".equals(type)) return folders.get("Documents");
        return folders.get("root");
    }
}