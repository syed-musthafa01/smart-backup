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
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
        };

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(uri, projection, null, null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC");

        if (cursor != null) {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                Uri contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );

                FileModel f = new FileModel(
                        cursor.getString(nameCol),
                        cursor.getString(dataCol),
                        cursor.getLong(sizeCol),
                        "image"
                );

                f.setLastModified(cursor.getLong(dateCol) * 1000);
                f.setContentUri(contentUri);
                f.setFromMediaStore(true);

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
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED
        };

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(uri, projection, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC");

        if (cursor != null) {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                Uri contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );

                FileModel f = new FileModel(
                        cursor.getString(nameCol),
                        cursor.getString(dataCol),
                        cursor.getLong(sizeCol),
                        "video"
                );
                f.setLastModified(cursor.getLong(dateCol) * 1000);
                f.setContentUri(contentUri);
                f.setFromMediaStore(true);
                list.add(f);
            }
            cursor.close();
        }
        return list;
    }
}
