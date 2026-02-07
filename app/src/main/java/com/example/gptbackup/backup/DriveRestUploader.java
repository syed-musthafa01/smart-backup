package com.example.gptbackup.backup;

import android.content.Context;
import android.util.Log;

import com.example.gptbackup.model.FileModel;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class DriveRestUploader {

    private static final String TAG = "DriveRestUploader";
    private static final String DRIVE_SCOPE =
            "oauth2:https://www.googleapis.com/auth/drive.file";

    // ================= CALLBACKS =================

    public interface ProgressCallback {
        void onProgress(int progress);
    }

    /** ✅ Industrial upload control handle */
    public interface UploadHandle {
        void pause();
        void resume();
        void cancel();
    }

    // ================= PUBLIC API =================

    public static UploadHandle uploadSingleFile(
            Context context,
            GoogleSignInAccount account,
            FileModel fileModel,
            ProgressCallback callback
    ) {

        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicBoolean paused = new AtomicBoolean(false);
        Object pauseLock = new Object();

        new Thread(() -> {
            try {
                String accessToken = GoogleAuthUtil.getToken(
                        context,
                        account.getAccount(),
                        DRIVE_SCOPE
                );

                OkHttpClient client = new OkHttpClient();

                Map<String, String> folders =
                        ensureFolderStructure(client, accessToken);

                File localFile = new File(fileModel.getPath());
                String parentId = resolveParentId(folders, fileModel);

                String metadataJson =
                        "{ \"name\": \"" + localFile.getName() + "\", " +
                                "\"mimeType\": \"application/octet-stream\", " +
                                "\"parents\": [\"" + parentId + "\"] }";

                RequestBody metadata = RequestBody.create(
                        metadataJson,
                        MediaType.parse("application/json; charset=utf-8")
                );

                RequestBody fileBody = new ProgressRequestBody(
                        localFile,
                        MediaType.parse("application/octet-stream"),
                        callback,
                        canceled,
                        fileModel.getPausedFlag()   // 👈 REQUIRED
                );


                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("metadata", null, metadata)
                        .addFormDataPart("file", localFile.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "Uploaded: " + localFile.getName());
                } else {
                    Log.e(TAG, "Upload failed: " + response.code());
                }

            } catch (Exception e) {
                if (canceled.get()) {
                    Log.d(TAG, "Upload canceled by user");
                } else {
                    Log.e(TAG, "Upload error", e);
                }
            }
        }).start();

        // ========= CONTROL HANDLE =========
        return new UploadHandle() {
            @Override
            public void pause() {
                paused.set(true);
            }

            @Override
            public void resume() {
                synchronized (pauseLock) {
                    paused.set(false);
                    pauseLock.notifyAll();
                }
            }

            @Override
            public void cancel() {
                canceled.set(true);
                resume(); // unblock if paused
            }
        };
    }

    // ================= PROGRESS BODY =================

     private static class ProgressRequestBody extends RequestBody {

        private final File file;
        private final MediaType contentType;
        private final ProgressCallback callback;
        private final AtomicBoolean canceled;

        // 🔹 NEW: pause control (from FileModel)
        private final AtomicBoolean paused;

        ProgressRequestBody(File file,
                            MediaType contentType,
                            ProgressCallback callback,
                            AtomicBoolean canceled,
                            AtomicBoolean paused) {

            this.file = file;
            this.contentType = contentType;
            this.callback = callback;
            this.canceled = canceled;
            this.paused = paused;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {

            long length = contentLength();
            long uploaded = 0;

            try (Source source = Okio.source(file)) {

                long read;
                while ((read = source.read(sink.getBuffer(), 8 * 1024)) != -1) {

                    // 🔴 HARD CANCEL
                    if (canceled.get()) {
                        throw new IOException("Upload canceled");
                    }

                    // ⏸ PAUSE (BLOCK THREAD SAFELY)
                    while (paused.get()) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ignored) {}
                    }

                    uploaded += read;
                    sink.flush();

                    if (callback != null && length > 0) {
                        int progress = (int) ((uploaded * 100) / length);
                        callback.onProgress(progress);
                    }
                }
            }
        }
    }


    // ================= FOLDER LOGIC (UNCHANGED) =================

    private static Map<String, String> ensureFolderStructure(
            OkHttpClient client,
            String accessToken
    ) throws IOException {

        Map<String, String> ids = new HashMap<>();

        String rootId = createFolderIfNotExists(
                client, accessToken, "SmartAIBackup", null);
        ids.put("root", rootId);

        ids.put("Images", createFolderIfNotExists(client, accessToken, "Images", rootId));
        ids.put("Videos", createFolderIfNotExists(client, accessToken, "Videos", rootId));
        ids.put("Documents", createFolderIfNotExists(client, accessToken, "Documents", rootId));
        ids.put("Audio", createFolderIfNotExists(client, accessToken, "Audio", rootId));
        ids.put("AI_Priority", createFolderIfNotExists(client, accessToken, "AI_Priority", rootId));

        return ids;
    }

    private static String createFolderIfNotExists(
            OkHttpClient client,
            String accessToken,
            String name,
            String parentId
    ) throws IOException {

        String query = "mimeType='application/vnd.google-apps.folder' and name='" + name + "'";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }

        Request listRequest = new Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=" + query.replace(" ", "%20"))
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        Response listResponse = client.newCall(listRequest).execute();
        if (listResponse.isSuccessful() && listResponse.body() != null) {
            String body = listResponse.body().string();
            int idIndex = body.indexOf("\"id\":");
            if (idIndex != -1) {
                int start = body.indexOf("\"", idIndex + 5) + 1;
                int end = body.indexOf("\"", start);
                return body.substring(start, end);
            }
        }

        String parentPart = parentId == null ? "" :
                ", \"parents\": [\"" + parentId + "\"]";

        String json = "{ \"name\": \"" + name + "\", " +
                "\"mimeType\": \"application/vnd.google-apps.folder\"" +
                parentPart + " }";

        RequestBody body = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request createReq = new Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        Response createResp = client.newCall(createReq).execute();
        String respBody = createResp.body().string();

        int idIndex = respBody.indexOf("\"id\":");
        int start = respBody.indexOf("\"", idIndex + 5) + 1;
        int end = respBody.indexOf("\"", start);
        return respBody.substring(start, end);
    }

    private static String resolveParentId(
            Map<String, String> folders,
            FileModel fileModel
    ) {
        if (fileModel.getPriority() == 2) {
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
