package com.dmitrybrant.zimdroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a ZIM file.
 * Dmitry Brant, 2017.
 *
 * Loosely based on original implementation by Arunesh Mathur
 */
public class ZimFile extends File {
    private static final int ZIM_HEADER_MAGIC = 0x044D495A;
    private static final int UUID_SIZE = 16;

    private int version;
    private byte[] uuid = new byte[UUID_SIZE];
    private int articleCount;
    private int clusterCount;
    private long urlPtrPos;
    private long titlePtrPos;
    private long clusterPtrPos;
    private long mimeListPos;
    private int mainPage;
    private int layoutPage;

    private List<String> mimeTypeList = new ArrayList<>();

    public ZimFile(String path) throws IOException {
        super(path);
        readHeader();
    }

    public int getVersion() {
        return version;
    }

    public int getArticleCount() {
        return articleCount;
    }

    public int getClusterCount() {
        return clusterCount;
    }

    public long getUrlPtrPos() {
        return urlPtrPos;
    }

    public long getTitlePtrPos() {
        return titlePtrPos;
    }

    public long getClusterPtrPos() {
        return clusterPtrPos;
    }

    public String getMIMEType(int mimeNumber) {
        return mimeTypeList.get(mimeNumber);
    }

    public long getHeaderSize() {
        return mimeListPos;
    }

    public int getMainPage() {
        return mainPage;
    }

    public int getLayoutPage() {
        return layoutPage;
    }

    private void readHeader() throws IOException {
        ZimInputStream reader = new ZimInputStream(new FileInputStream(this));
        // Read the contents of the header
        try {
            int magic = reader.readIntLe();
            if (magic != ZIM_HEADER_MAGIC) {
                throw new IOException("Invalid ZIM file.");
            }
            version = reader.readIntLe();
            if (reader.read(uuid) != uuid.length) {
                throw new IOException("Failed to read from stream.");
            }
            articleCount = reader.readIntLe();
            clusterCount = reader.readIntLe();
            urlPtrPos = reader.readLongLe();
            titlePtrPos = reader.readLongLe();
            clusterPtrPos = reader.readLongLe();
            mimeListPos = reader.readLongLe();
            mainPage = reader.readIntLe();
            layoutPage = reader.readIntLe();

            reader.seek(mimeListPos);
            while (true) {
                String mimeStr = reader.readString();
                if (mimeStr.length() == 0) {
                    break;
                }
                mimeTypeList.add(mimeStr);
            }

        } finally {
            reader.close();
        }
    }
}
