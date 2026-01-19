package com.example.gptbackup.backup;

import android.content.Context;
import android.util.Log;

import com.example.gptbackup.model.FileModel;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.GoogleAuthUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DriveRestUploader {

    private static final String TAG = "DriveRestUploader";
    private static final String DRIVE_SCOPE =
            "oauth2:https://www.googleapis.com/auth/drive.file";

    public static void uploadFiles(Context context,
                                   GoogleSignInAccount account,
                                   List<FileModel> files) {

        new Thread(() -> {

            try {
                // ✅ Get ACCESS TOKEN (this is the correct way)
                String accessToken = GoogleAuthUtil.getToken(
                        context,
                        account.getAccount(),
                        DRIVE_SCOPE
                );

                OkHttpClient client = new OkHttpClient();

                for (FileModel f : files) {

                    File localFile = new File(f.getPath());

                    RequestBody fileBody = RequestBody.create(
                            localFile,
                            MediaType.parse("application/octet-stream")
                    );

                    RequestBody metadata = RequestBody.create(
                            "{ \"name\": \"" + localFile.getName() + "\" }",
                            MediaType.parse("application/json")
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
                        String errorBody = response.body() != null
                                ? response.body().string()
                                : "No error body";

                        Log.e(TAG, "Upload failed");
                        Log.e(TAG, "Code: " + response.code());
                        Log.e(TAG, "Error: " + errorBody);
                    }

                }

            } catch (Exception e) {
                Log.e(TAG, "Drive upload error", e);
            }

        }).start();
    }
}
