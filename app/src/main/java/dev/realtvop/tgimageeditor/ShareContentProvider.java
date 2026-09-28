package dev.realtvop.tgimageeditor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.content.res.AssetFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Serves one-shot in-memory JPEGs to share targets without creating a media file. */
public final class ShareContentProvider extends ContentProvider {
    private static final String PATH_IMAGE = "image";
    private static final ConcurrentHashMap<String, Entry> CONTENT = new ConcurrentHashMap<>();

    public static Uri publish(Context context, byte[] bytes) {
        String token = UUID.randomUUID().toString();
        CONTENT.put(token, new Entry(bytes));
        return new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(PATH_IMAGE)
                .appendPath(token)
                .build();
    }

    public static void release(Uri uri) {
        if (uri != null && uri.getLastPathSegment() != null) {
            CONTENT.remove(uri.getLastPathSegment());
        }
    }

    private static String authority(Context context) {
        return context.getPackageName() + ".share";
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        return entry(uri) == null ? null : "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Entry value = entry(uri);
        if (value == null) return null;
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = "TGImageEditor.jpg";
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = (long) value.bytes.length;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.indexOf('w') >= 0) {
            throw new FileNotFoundException("Read-only share content");
        }
        Entry value = entry(uri);
        if (value == null) throw new FileNotFoundException("Share content expired");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(value.bytes);
                } catch (IOException ignored) {
                    // The receiving app may close the pipe before consuming all bytes.
                }
            }, "tg-image-share");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        Entry value = entry(uri);
        if (value == null) throw new FileNotFoundException("Share content expired");
        return new AssetFileDescriptor(openFile(uri, mode), 0, value.bytes.length);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private static Entry entry(Uri uri) {
        if (uri == null || uri.getPathSegments().size() != 2
                || !PATH_IMAGE.equals(uri.getPathSegments().get(0))) return null;
        return CONTENT.get(uri.getLastPathSegment());
    }

    private static final class Entry {
        final byte[] bytes;

        Entry(byte[] bytes) {
            this.bytes = bytes;
        }
    }
}
