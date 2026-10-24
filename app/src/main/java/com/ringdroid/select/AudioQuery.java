package com.ringdroid.select;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** The MediaStore query that lists audio files, optionally filtered by a search term. */
final class AudioQuery {
    static final String[] PROJECTION = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM, MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.DATA};

    final String selection;
    final String[] selectionArgs;

    private AudioQuery(String selection, List<String> selectionArgs) {
        this.selection = selection;
        this.selectionArgs = selectionArgs.toArray(new String[0]);
    }

    /**
     * @param showAll
     *            list every audio file instead of supported files on the given storage roots
     * @param filter
     *            search term matched against title, artist and album, or null
     */
    static AudioQuery build(boolean showAll, String filter, String[] extensions,
            List<String> storageRoots) {
        List<String> args = new ArrayList<>();
        StringBuilder selection = new StringBuilder();
        if (showAll) {
            selection.append("(_DATA LIKE ?)");
            args.add("%");
        } else {
            selection.append('(');
            for (int i = 0; i < extensions.length; i++) {
                selection.append(i > 0 ? " OR " : "").append("(_DATA LIKE ?)");
                args.add("%." + extensions[i]);
            }
            selection.append(") AND (");
            for (int i = 0; i < storageRoots.size(); i++) {
                selection.append(i > 0 ? " OR " : "").append("_DATA LIKE ?");
                args.add(storageRoots.get(i) + "/%");
            }
            selection.append(')');
        }
        if (filter != null && !filter.isEmpty()) {
            selection.insert(0, '(')
                    .append(" AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))");
            String like = "%" + filter + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return new AudioQuery(selection.toString(), args);
    }

    Cursor run(ContentResolver resolver, Uri collection) {
        return resolver.query(collection, PROJECTION, selection, selectionArgs,
                MediaStore.Audio.Media.TITLE + " ASC");
    }
}
