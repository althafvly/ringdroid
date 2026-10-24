package com.ringdroid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.ringdroid.soundfile.SoundFile;
import com.ringdroid.soundfile.SoundFileWriter;

import java.io.File;
import java.io.IOException;

/**
 * Saves a selection of a sound file as M4A, falling back to WAV, and registers it in MediaStore.
 */
final class RingtoneSaver {
    static final class SaveException extends IOException {
        final int messageId;

        SaveException(int messageId, Throwable cause) {
            super(cause);
            this.messageId = messageId;
        }
    }

    private static final String TAG = "RingtoneSaver";
    private final Context mContext;
    private final SoundFileWriter mWriter;
    private final int mFileKind;

    RingtoneSaver(Context context, SoundFile soundFile, float gain, int fileKind) {
        mContext = context.getApplicationContext();
        mWriter = new SoundFileWriter(soundFile, gain);
        mFileKind = fileKind;
    }

    Uri save(CharSequence title, int startFrame, int numFrames, int durationMs)
            throws SaveException {
        Exception error = null;
        for (boolean wav : new boolean[]{false, true}) {
            try {
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? saveToMediaStore(wav, title, startFrame, numFrames)
                        : saveToFile(wav, title, startFrame, numFrames, durationMs);
            } catch (SaveException e) {
                throw e;
            } catch (IOException | SecurityException | IllegalArgumentException
                    | IllegalStateException e) {
                Log.e(TAG, "Failed to save " + (wav ? "WAV" : "M4A"), e);
                error = e;
            }
        }
        boolean noSpace = "No space left on device".equals(error.getMessage());
        throw new SaveException(noSpace ? R.string.no_space_error : R.string.write_error, error);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri saveToMediaStore(boolean wav, CharSequence title, int startFrame, int numFrames)
            throws IOException {
        ContentValues values = new ContentValues();
        putFileKind(values);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, getSubDir());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, title + (wav ? ".wav" : ".m4a"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(wav));
        ContentResolver resolver = mContext.getContentResolver();
        Uri uri = resolver.insert(RingdroidUtils.getExternalAudioCollectionUri(), values);
        if (uri == null) {
            throw new IOException("MediaStore insert failed");
        }
        try {
            if (wav) {
                mWriter.writeWAV(mContext, uri, startFrame, numFrames);
            } else {
                mWriter.writeM4A(mContext, uri, startFrame, numFrames);
            }
        } catch (IOException | SecurityException | IllegalArgumentException
                | IllegalStateException e) {
            try {
                resolver.delete(uri, null, null);
            } catch (SecurityException | IllegalArgumentException
                    | UnsupportedOperationException deleteError) {
                Log.w(TAG, "Failed to delete incomplete entry: " + uri, deleteError);
            }
            throw e;
        }
        return uri;
    }

    private Uri saveToFile(boolean wav, CharSequence title, int startFrame, int numFrames,
            int durationMs) throws IOException {
        String path = makeRingtoneFilename(title, wav ? ".wav" : ".m4a");
        if (path == null) {
            throw new SaveException(R.string.no_unique_filename, null);
        }
        File file = new File(path);
        try {
            if (wav) {
                mWriter.writeWAV(file, startFrame, numFrames);
            } else {
                mWriter.writeM4A(file, startFrame, numFrames);
            }
            verifyReadable(file);
            if (file.length() <= 512) {
                throw new SaveException(R.string.too_small_error, null);
            }
            Uri uri = mContext.getContentResolver().insert(
                    RingdroidUtils.getExternalAudioCollectionUri(),
                    getFileValues(file, title, wav, durationMs));
            if (uri == null) {
                throw new SaveException(R.string.write_error, null);
            }
            return uri;
        } catch (IOException | SecurityException | IllegalArgumentException
                | IllegalStateException e) {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete " + file);
            }
            throw e;
        }
    }

    private void verifyReadable(File file) throws IOException {
        try {
            SoundFile saved = SoundFile.create(mContext.getCacheDir(), file.getPath(),
                    frac -> false);
            if (saved != null) {
                saved.release();
            }
        } catch (SoundFile.InvalidInputException e) {
            throw new IOException("Saved file is unreadable", e);
        }
    }

    private ContentValues getFileValues(File file, CharSequence title, boolean wav,
            int durationMs) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, file.getPath());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, title.toString());
        values.put(MediaStore.MediaColumns.SIZE, file.length());
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(wav));
        values.put(MediaStore.Audio.Media.TITLE, title.toString());
        values.put(MediaStore.Audio.Media.ARTIST, mContext.getString(R.string.artist_name));
        values.put(MediaStore.Audio.Media.DURATION, durationMs);
        putFileKind(values);
        return values;
    }

    private static String getMimeType(boolean wav) {
        return wav ? "audio/wav" : "audio/mp4a-latm";
    }

    private void putFileKind(ContentValues values) {
        values.put(MediaStore.Audio.Media.IS_RINGTONE,
                mFileKind == FileSaveDialog.FILE_KIND_RINGTONE);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION,
                mFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION);
        values.put(MediaStore.Audio.Media.IS_ALARM, mFileKind == FileSaveDialog.FILE_KIND_ALARM);
        values.put(MediaStore.Audio.Media.IS_MUSIC, mFileKind == FileSaveDialog.FILE_KIND_MUSIC);
    }

    // MediaStore only accepts standard top-level folders such as Ringtones from Android 10.
    private String getSubDir() {
        String saveDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? Environment.DIRECTORY_RINGTONES
                : "media/audio";
        switch (mFileKind) {
            case FileSaveDialog.FILE_KIND_MUSIC :
                return saveDir + "/music/";
            case FileSaveDialog.FILE_KIND_ALARM :
                return saveDir + "/alarms/";
            case FileSaveDialog.FILE_KIND_NOTIFICATION :
                return saveDir + "/notifications/";
            case FileSaveDialog.FILE_KIND_RINGTONE :
                return saveDir + "/ringtones/";
            default :
                return saveDir + "/others/";
        }
    }

    private String makeRingtoneFilename(CharSequence title, String extension) {
        String externalRootDir = Environment.getExternalStorageDirectory().getPath();
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/";
        }

        // Fall back to the storage root if the folder can't be created.
        String parentDir = externalRootDir + getSubDir();
        File parentDirFile = new File(parentDir);
        if (!parentDirFile.mkdirs() && !parentDirFile.isDirectory()) {
            parentDir = externalRootDir;
        }

        StringBuilder filename = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            if (Character.isLetterOrDigit(title.charAt(i))) {
                filename.append(title.charAt(i));
            }
        }

        for (int i = 0; i < 100; i++) {
            String path = parentDir + filename + (i > 0 ? i : "") + extension;
            if (!new File(path).exists()) {
                return path;
            }
        }
        return null;
    }
}
