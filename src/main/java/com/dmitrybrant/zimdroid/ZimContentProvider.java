package com.dmitrybrant.zimdroid;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public abstract class ZimContentProvider extends ContentProvider {
    private static final String TAG = "ZimContentProvider";

    protected abstract Uri getContentUri();

    protected abstract ByteArrayOutputStream getDataForUrl(String url) throws IOException;

    @Override
    public String getType(Uri uri) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString().toLowerCase(Locale.ROOT));
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();

            String url = getFilePath(uri);
            Log.d(TAG, "Retrieving " + url);
            ByteArrayOutputStream stream = getDataForUrl(url);

            new TransferThread(stream, new AutoCloseOutputStream(pipe[1])).start();

        } catch (IOException e) {
            e.printStackTrace();
            throw new FileNotFoundException("Could not open pipe for: " + uri.toString());
        }
        return (pipe[0]);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection,
                        String[] selectionArgs, String sort) {
        throw new RuntimeException("Operation not supported");
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        throw new RuntimeException("Operation not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        throw new RuntimeException("Operation not supported");
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        throw new RuntimeException("Operation not supported");
    }

    private String getFilePath(Uri articleUri) {
        String filePath = articleUri.toString();
        int pos = articleUri.toString().indexOf(getContentUri().toString());
        if (pos != -1) {
            filePath = articleUri.toString().substring(getContentUri().toString().length());
        }
        // Remove fragment (#...) since it's not supported in ZIM structure.
        pos = filePath.indexOf("#");
        if (pos != -1) {
            filePath = filePath.substring(0, pos);
        }
        return filePath;
    }

    private static class TransferThread extends Thread {
        private final ByteArrayOutputStream in;
        private final OutputStream out;

        TransferThread(ByteArrayOutputStream in, OutputStream out) throws IOException {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                out.write(in.toByteArray());
                out.flush();
            } catch (IOException | NullPointerException e) {
                // ignore
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
    }
}
