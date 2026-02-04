package com.example.gptbackup.scanner;

import android.os.Environment;
import com.example.gptbackup.model.FileModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScanner {

    private final List<FileModel> fileList = new ArrayList<>();
    private boolean excludeNoMedia = false; // NEW (default = show all)

    // ================= PUBLIC API =================

    // Existing behavior (UNCHANGED)
    public List<FileModel> scanAllFiles() {
        excludeNoMedia = false; // show everything
        fileList.clear();
        File root = Environment.getExternalStorageDirectory();
        scanFolder(root);
        return fileList;
    }

    // NEW: Used by Smart Backup
    public List<FileModel> scanAllFiles(boolean excludeNoMedia) {
        this.excludeNoMedia = excludeNoMedia;
        fileList.clear();
        File root = Environment.getExternalStorageDirectory();
        scanFolder(root);
        return fileList;
    }

    // File Manager folder listing (UNCHANGED behavior)
    public List<FileModel> listFolder(File folder) {
        List<FileModel> list = new ArrayList<>();
        if (folder == null || !folder.exists() || !folder.canRead()) return list;

        File[] children = folder.listFiles();
        if (children == null) return list;

        for (File file : children) {

            // Apply filter ONLY if enabled
            if (excludeNoMedia && file.getName().equalsIgnoreCase(".nomedia")) {
                continue;
            }

            String name = file.getName();
            String path = file.getAbsolutePath();
            long size = file.isDirectory() ? 0L : file.length();
            String type = getFileType(name);

            FileModel model = new FileModel(name, path, size, type);
            model.setDirectory(file.isDirectory());
            model.setLastModified(file.lastModified());
            model.setType(type);

            list.add(model);
        }
        return list;
    }

    // ================= INTERNAL SCAN =================

    private void scanFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.canRead()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {

            // Apply filter ONLY for Smart Backup
            if (excludeNoMedia && file.getName().equalsIgnoreCase(".nomedia")) {
                continue;
            }

            if (file.isDirectory()) {
                scanFolder(file);
            } else {
                String name = file.getName();
                String path = file.getAbsolutePath();
                long size = file.length();
                String type = getFileType(name);

                FileModel model = new FileModel(name, path, size, type);
                model.setLastModified(file.lastModified());
                model.setType(type);

                fileList.add(model);
            }
        }
    }

    // ================= HELPERS =================

    private String getFileType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".png")) return "image";
        if (name.endsWith(".mp4") || name.endsWith(".mkv")) return "video";
        if (name.endsWith(".mp3") || name.endsWith(".wav")) return "audio";
        if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx"))
            return "document";
        return "other";
    }
}
