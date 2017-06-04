package com.dmitrybrant.zimdroid;

public class ArticleEntry extends DirectoryEntry {
    private int clusterNumber;
    private int blobNumber;

    @SuppressWarnings("checkstyle:parameternumber")
    public ArticleEntry(int mimeType, char namespace, int revision, int clusterNumber,
                        int blobNumber, String url, String title, int urlListIndex) {
        super(mimeType, namespace, revision, url, title, urlListIndex);
        this.clusterNumber = clusterNumber;
        this.blobNumber = blobNumber;
    }

    public int getClusterNumber() {
        return clusterNumber;
    }

    public int getBlobNumber() {
        return blobNumber;
    }
}
