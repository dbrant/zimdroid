package com.dmitrybrant.zimdroid;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Custom stream type for reading data from a ZIM file.
 * Dmitry Brant, 2017.
 *
 * Loosely based on original implementation by Arunesh Mathur
 */
public class ZimInputStream extends BufferedInputStream {
    private static final int TEMP_BUFFER_SIZE = 8;

    private FileInputStream fileStream;
    private byte[] buffer = new byte[TEMP_BUFFER_SIZE];

    public ZimInputStream(FileInputStream fileStream) {
        super(fileStream);
        this.fileStream = fileStream;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public int readShortLe() throws IOException {
        if (read(buffer, 0, 2) != 2) {
            throw new IOException("Failed to read from stream.");
        }
        return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public int readIntLe() throws IOException {
        if (read(buffer, 0, 4) != 4) {
            throw new IOException("Failed to read from stream.");
        }
        return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public long readLongLe() throws IOException {
        if (read(buffer, 0, 8) != 8) {
            throw new IOException("Failed to read from stream.");
        }
        return ((long)(buffer[0] & 0xFF) | ((long)(buffer[1] & 0xFF) << 8)
                | ((long)(buffer[2] & 0xFF) << 16) | ((long)(buffer[3] & 0xFF) << 24)
                | ((long)(buffer[4] & 0xFF) << 32) | ((long)(buffer[5] & 0xFF) << 40)
                | ((long)(buffer[6] & 0xFF) << 48) | ((long)(buffer[7] & 0xFF) << 56));
    }

    public String readString() throws IOException {
        final int bufSize = 256;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(bufSize);
        int b;
        b = read();
        if (b < 0) {
            throw new IOException("Failed to read from stream.");
        }
        while (b != 0) {
            bytes.write(b);
            b = read();
            if (b < 0) {
                throw new IOException("Failed to read from stream.");
            }
        }
        return bytes.toString("utf-8");
    }

    public void seek(long pos) throws IOException {
        fileStream.getChannel().position(pos);
        this.pos = 0;
        this.count = 0;
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
