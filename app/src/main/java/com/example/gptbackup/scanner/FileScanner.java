package com.example.gptbackup.scanner;


import android.os.Environment;

import com.example.gptbackup.model.FileModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScanner {

    private final List<FileModel> fileList = new ArrayList<>();

    public List<FileModel> scanAllFiles() {
        File root = Environment.getExternalStorageDirectory();
        scanFolder(root);
        return fileList;
    }

    private void scanFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.canRead()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanFolder(file);
            } else {
                String name = file.getName();
                String path = file.getAbsolutePath();
                long size = file.length();
                String type = getFileType(name);

                fileList.add(new FileModel(name, path, size, type));
            }
        }
    }

    private String getFileType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".png")) return "image";
        if (name.endsWith(".mp4") || name.endsWith(".mkv")) return "video";
        if (name.endsWith(".mp3") || name.endsWith(".wav")) return "audio";
        if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx")) return "document";
        return "other";
    }
}
