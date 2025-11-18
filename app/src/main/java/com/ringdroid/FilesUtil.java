package com.ringdroid;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

public class FilesUtil {

    public static String getFullPathFromUri(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String authority = uri.getAuthority();
            if ("com.android.externalstorage.documents".equals(authority)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");

                if (split.length == 2 && "primary".equalsIgnoreCase(split[0])) {
                    return Environment.getExternalStorageDirectory().getAbsolutePath()
                            + "/" + split[1];
                }
            }

            if ("com.android.providers.media.documents".equals(authority)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String mediaId = split[1];

                Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String sel = MediaStore.Audio.Media._ID + "=?";
                String[] selArgs = new String[]{mediaId};

                return getDataColumn(context, contentUri, sel, selArgs);
            }

            if ("com.android.providers.downloads.documents".equals(authority)) {
                String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        Long.parseLong(id)
                );
                return getDataColumn(context, contentUri, null, null);
            }

            return getDataColumn(context, uri, null, null);
        }

        return null;
    }

    private static String getDataColumn(Context context, Uri uri,
                                        String sel, String[] selArgs) {
        String[] projection = { MediaStore.MediaColumns.DATA };
        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, sel, selArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                return cursor.getString(idx);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static File getFileFromUri(Context context, Uri uri) {
        String fullPath = getFullPathFromUri(context, uri);
        return fullPath != null ? new File(fullPath) : null;
    }
}
