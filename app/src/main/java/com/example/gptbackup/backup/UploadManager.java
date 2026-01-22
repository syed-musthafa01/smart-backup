package com.example.gptbackup.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High‑level upload controller.
 * - Sorts by AI priority (High -> Medium -> Low)
 * - Exposes pause / resume / cancel
 * - Reports per‑file + global progress back to UI thread.
 */
public class UploadManager {

    public interface Listener {
        void onFileProgress(FileModel file, int progress);
        void onFileStateChanged(FileModel file, UploadState state);
        void onGlobalProgress(int completed, int total);
        void onCompleted();
    }

    private final Context context;
    private final GoogleSignInAccount account;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean canceled = new AtomicBoolean(false);

    private List<FileModel> uploadQueue = new ArrayList<>();

    public UploadManager(Context context,
                         GoogleSignInAccount account,
                         List<FileModel> files,
                         Listener listener) {
        this.context = context.getApplicationContext();
        this.account = account;
        this.listener = listener;

        // Sort by priority: high->medium->low
        List<FileModel> copy = new ArrayList<>(files);
        Collections.sort(copy, new Comparator<FileModel>() {
            @Override
            public int compare(FileModel o1, FileModel o2) {
                return Integer.compare(o2.getPriority(), o1.getPriority());
            }
        });
        this.uploadQueue = copy;
    }

    public void start() {
        canceled.set(false);
        paused.set(false);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int completed = 0;
                int total = uploadQueue.size();

                for (FileModel file : uploadQueue) {
                    if (canceled.get()) {
                        updateState(file, UploadState.CANCELED);
                        break;
                    }

                    // Wait if paused
                    while (paused.get() && !canceled.get()) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                    }

                    if (canceled.get()) {
                        updateState(file, UploadState.CANCELED);
                        break;
                    }

                    updateState(file, UploadState.UPLOADING);
                    boolean ok = DriveRestUploader.uploadSingleFile(context, account, file,
                            new DriveRestUploader.ProgressCallback() {
                                @Override
                                public void onProgress(final int progress) {
                                    file.setUploadProgress(progress);
                                    if (listener != null) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                listener.onFileProgress(file, progress);
                                            }
                                        });
                                    }
                                }
                            });

                    if (ok) {
                        file.setUploadProgress(100);
                        updateState(file, UploadState.COMPLETED);
                        completed++;
                    } else {
                        updateState(file, UploadState.FAILED);
                    }

                    final int finalCompleted = completed;
                    if (listener != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onGlobalProgress(finalCompleted, total);
                            }
                        });
                    }
                }

                if (listener != null && !canceled.get()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onCompleted();
                        }
                    });
                }
            }
        });
    }

    public void pause() {
        paused.set(true);
        for (FileModel f : uploadQueue) {
            if (f.getUploadState() == UploadState.UPLOADING) {
                updateState(f, UploadState.PAUSED);
                break;
            }
        }
    }

    public void resume() {
        paused.set(false);
        for (FileModel f : uploadQueue) {
            if (f.getUploadState() == UploadState.PAUSED) {
                updateState(f, UploadState.WAITING);
            }
        }
    }

    public void cancel() {
        canceled.set(true);
    }

    private void updateState(final FileModel file, final UploadState state) {
        file.setUploadState(state);
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onFileStateChanged(file, state);
                }
            });
        }
    }
}

