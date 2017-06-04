package com.dmitrybrant.zimdroid;

public class RedirectEntry extends DirectoryEntry {
    public static final int ENTRY_SIZE = 12;

    private int redirectIndex;

    public RedirectEntry(int mimeType, char namespace, int revision, int redirectIndex, String url,
                         String title, int urlListIndex) {
        super(mimeType, namespace, revision, url, title, urlListIndex);
        this.redirectIndex = redirectIndex;
    }

    public int getRedirectIndex() {
        return redirectIndex;
    }
}
