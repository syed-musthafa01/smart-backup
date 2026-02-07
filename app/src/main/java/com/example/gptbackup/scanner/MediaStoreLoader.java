package com.example.gptbackup.scanner;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.example.gptbackup.model.FileModel;

import java.util.ArrayList;
import java.util.List;

public class MediaStoreLoader {

    public static List<FileModel> loadImages(Context context) {
        List<FileModel> list = new ArrayList<>();

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
        };

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(uri, projection, null, null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                FileModel f = new FileModel(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        "image"
                );
                f.setLastModified(cursor.getLong(3) * 1000);
                list.add(f);
            }
            cursor.close();
        }
        return list;
    }

    public static List<FileModel> loadVideos(Context context) {
        List<FileModel> list = new ArrayList<>();

        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED
        };

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(uri, projection, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                FileModel f = new FileModel(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        "video"
                );
                f.setLastModified(cursor.getLong(3) * 1000);
                list.add(f);
            }
            cursor.close();
        }
        return list;
    }
}
