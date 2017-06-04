package com.dmitrybrant.zimdroid;

public abstract class DirectoryEntry {
    public static final int TYPE_REDIRECT = 65535;
    public static final int TYPE_LINK_TARGET = 65534;
    public static final int TYPE_DELETED = 65533;

    private final int mimeType;
    private final char namespace;
    private final int revision;
    private final String url;
    private final String title;
    private final int urlListIndex;

    private int titleListIndex;

    public DirectoryEntry(int mimeType, char namespace, int revision, String url, String title, int index) {
        this.mimeType = mimeType;
        this.namespace = namespace;
        this.revision = revision;
        this.url = url;
        this.title = title;
        this.urlListIndex = index;
    }

    public int getMimeType() {
        return mimeType;
    }

    public char getNamespace() {
        return namespace;
    }

    public int getRevision() {
        return revision;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public int getUrlListIndex() {
        return urlListIndex;
    }

    public void setTitleListIndex(int index) {
        titleListIndex = index;
    }

    public int getTitleListIndex() {
        return titleListIndex;
    }

    @Override public String toString() {
        return title;
    }
}
