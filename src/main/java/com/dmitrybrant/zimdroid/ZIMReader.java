package com.dmitrybrant.zimdroid;

import android.util.LruCache;

import org.tukaani.xz.SingleXZInputStream;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reads content and metadata from a ZIM file.
 * Dmitry Brant, 2017.
 *
 * Loosely based on original implementation by Arunesh Mathur
 */
public class ZIMReader implements Closeable {
    private static final int COMPRESSION_TYPE_NONE = 0;
    private static final int COMPRESSION_TYPE_NONE_OLD = 1;
    private static final int COMPRESSION_TYPE_LZMA = 4;
    private static final int BYTES_PER_INT = 4;
    private static final int BYTES_PER_LONG = 8;
    private static final int CACHE_SIZE = 256;

    private ZIMFile zimFile;
    private ZIMInputStream inputStream;

    private LruCache<Integer, DirectoryEntry> entryByTitleCache = new LruCache<>(CACHE_SIZE);
    private LruCache<Integer, DirectoryEntry> entryByUrlCache = new LruCache<>(CACHE_SIZE);

    private String zimTitle;
    private String zimDescription;

    private int firstArticleTitleIndex = -1;
    private int lastArticleTitleIndex = -1;

    public ZIMReader(ZIMFile file) {
        zimFile = file;
        try {
            inputStream = new ZIMInputStream(new FileInputStream(zimFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    public String getZimTitle() throws IOException {
        if (zimTitle == null) {
            zimTitle = getDataForSpecialUrl("Title").toString("utf-8");
        }
        return zimTitle;
    }

    public String getZimDescription() throws IOException {
        if (zimDescription == null) {
            zimDescription = getDataForSpecialUrl("Description").toString("utf-8");
        }
        return zimDescription;
    }

    public String getRandomTitle() throws IOException {
        int first = getFirstArticleTitleIndex();
        int last = getLastArticleTitleIndex();
        int index = first + new Random().nextInt(last - first);
        DirectoryEntry entry = resolveRedirect(getDirectoryEntryAtTitlePosition(index));
        return entry.getTitle();
    }

    public List<String> searchByPrefix(String prefix, int maxResults) throws IOException {
        List<String> results = new ArrayList<>();
        // artificially capitalize the first character of the prefix
        if (prefix.length() >= 1) {
            prefix = prefix.substring(0, 1).toUpperCase(Locale.ROOT) + prefix.substring(1);
        }
        DirectoryEntry entry = binarySearchByTitle(prefix, true);
        if (entry == null) {
            return results;
        }
        int index = entry.getTitleListIndex();
        if (index < 0) {
            index = 0;
        }

        for (int i = 0; i < maxResults; i++) {
            entry = getDirectoryEntryAtTitlePosition(index);

            if (entry.getTitle().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                results.add(entry.getTitle());
            }

            index++;
            if (index >= zimFile.getArticleCount()) {
                break;
            }
        }
        return results;
    }

    public String getNormalizedTitle(String title) throws IOException {
        return resolveRedirect(binarySearchByTitle(title, false)).getTitle();
    }

    public ByteArrayOutputStream getDataForUrl(String url) throws IOException {
        return getData(binarySearchByUrl(url, false));
    }

    public ByteArrayOutputStream getDataForTitle(String title) throws IOException {
        return getData(binarySearchByTitle(title, false));
    }

    public ByteArrayOutputStream getDataForSpecialUrl(String url) throws IOException {
        return getData(getDirectoryEntryFromEnd(url));
    }

    private synchronized ByteArrayOutputStream getData(DirectoryEntry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        entry = resolveRedirect(entry);

        int clusterNumber = ((ArticleEntry) entry).getClusterNumber();
        int blobNumber = ((ArticleEntry) entry).getBlobNumber();

        inputStream.seek(zimFile.getClusterPtrPos() + clusterNumber * BYTES_PER_LONG);

        long clusterPos = inputStream.readLongLe();
        inputStream.seek(clusterPos);

        int compressionType = inputStream.read();

        SingleXZInputStream xzReader;
        int firstOffset, numberOfBlobs, offset1, offset2, location, differenceOffset;

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer;

        // Check the compression type that was read
        switch (compressionType) {

            case COMPRESSION_TYPE_NONE:
            case COMPRESSION_TYPE_NONE_OLD:

                firstOffset = inputStream.readIntLe();
                numberOfBlobs = firstOffset / BYTES_PER_INT;

                if (blobNumber >= numberOfBlobs) {
                    throw new IllegalArgumentException("Blob number greater than total blobs.");
                }

                if (blobNumber == 0) {
                    // The first offset is what we read earlier
                    offset1 = firstOffset;
                } else {
                    location = (blobNumber - 1) * BYTES_PER_INT;
                    skipFully(inputStream, location);
                    offset1 = inputStream.readIntLe();
                }

                offset2 = inputStream.readIntLe();

                differenceOffset = offset2 - offset1;
                buffer = new byte[differenceOffset];

                skipFully(inputStream, (offset1 - BYTES_PER_INT * (blobNumber + 2)));

                inputStream.read(buffer, 0, differenceOffset);
                outStream.write(buffer, 0, differenceOffset);
                break;

            case COMPRESSION_TYPE_LZMA:

                final int memoryLimit = 4000000;
                xzReader = new SingleXZInputStream(inputStream, memoryLimit);

                buffer = new byte[BYTES_PER_INT];
                xzReader.read(buffer);

                firstOffset = getIntLe(buffer);
                numberOfBlobs = firstOffset / BYTES_PER_INT;

                if (blobNumber >= numberOfBlobs) {
                    throw new IllegalArgumentException("Blob number greater than total blobs.");
                }

                if (blobNumber == 0) {
                    offset1 = firstOffset;
                } else {
                    location = (blobNumber - 1) * BYTES_PER_INT;
                    skipFully(xzReader, location);
                    xzReader.read(buffer);
                    offset1 = getIntLe(buffer);
                }

                xzReader.read(buffer);
                offset2 = getIntLe(buffer);

                differenceOffset = offset2 - offset1;
                buffer = new byte[differenceOffset];

                skipFully(xzReader, (offset1 - BYTES_PER_INT * (blobNumber + 2)));

                xzReader.read(buffer, 0, differenceOffset);
                outStream.write(buffer, 0, differenceOffset);
                break;

            default:
                break;
        }
        return outStream;
    }

    private int getFirstArticleTitleIndex() throws IOException {
        if (firstArticleTitleIndex != -1) {
            return firstArticleTitleIndex;
        }
        DirectoryEntry entry;
        int index = 0;

        while (index < zimFile.getArticleCount()) {
            entry = getDirectoryEntryAtTitlePosition(index);
            if (entry == null) {
                return 0;
            }
            if (entry.getNamespace() == 'A') {
                firstArticleTitleIndex = index;
                break;
            }
            index++;
        }
        return firstArticleTitleIndex;
    }

    private int getLastArticleTitleIndex() throws IOException {
        if (lastArticleTitleIndex != -1) {
            return lastArticleTitleIndex;
        }
        DirectoryEntry entry;
        int beginIndex = 0, endIndex = beginIndex + zimFile.getArticleCount(), midIndex = 0;

        while (beginIndex <= endIndex) {
            midIndex = beginIndex + ((endIndex - beginIndex) / 2);
            entry = getDirectoryEntryAtTitlePosition(midIndex);
            if (entry == null) {
                return 0;
            }
            if (entry.getNamespace() != 'A') {
                endIndex = midIndex - 1;
            } else {
                beginIndex = midIndex + 1;
            }
        }
        lastArticleTitleIndex = midIndex;
        return midIndex;
    }

    private DirectoryEntry binarySearchByUrl(String url, boolean getClosest) throws IOException {
        DirectoryEntry entry = null;
        int beginIndex = 0, endIndex = beginIndex + zimFile.getArticleCount(), midIndex;

        while (beginIndex <= endIndex) {
            midIndex = beginIndex + ((endIndex - beginIndex) / 2);
            entry = getDirectoryEntryAtUrlPosition(midIndex);
            if (entry == null) {
                return null;
            }
            if (url.compareTo(entry.getUrl()) < 0) {
                endIndex = midIndex - 1;
            } else if (url.compareTo(entry.getUrl()) > 0) {
                beginIndex = midIndex + 1;
            } else {
                return entry;
            }
        }
        return getClosest ? entry : null;
    }

    private DirectoryEntry binarySearchByTitle(String title, boolean getClosest) throws IOException {
        DirectoryEntry entry = null;
        int beginIndex = 0, endIndex = beginIndex + zimFile.getArticleCount(), midIndex;

        while (beginIndex <= endIndex) {
            midIndex = beginIndex + ((endIndex - beginIndex) / 2);
            entry = getDirectoryEntryAtTitlePosition(midIndex);
            if (entry == null) {
                return null;
            }
            if (title.compareTo(entry.getTitle()) < 0) {
                endIndex = midIndex - 1;
            } else if (title.compareTo(entry.getTitle()) > 0) {
                beginIndex = midIndex + 1;
            } else {
                return entry;
            }
        }
        return getClosest ? entry : null;
    }

    private DirectoryEntry getDirectoryEntryFromEnd(String url) throws IOException {
        DirectoryEntry entry;
        final int maxEntriesToSearch = 256;
        int index = zimFile.getArticleCount() - 1;

        for (int i = index; i > 0 && zimFile.getArticleCount() - i < maxEntriesToSearch; i--) {
            entry = getDirectoryEntryAtUrlPosition(i);
            if (entry == null) {
                return null;
            }
            if (url.equals(entry.getUrl())) {
                return entry;
            }
        }
        return null;
    }

    private synchronized DirectoryEntry getDirectoryEntryAtTitlePosition(int position) throws IOException {
        if (entryByTitleCache.get(position) != null) {
            return entryByTitleCache.get(position);
        }
        inputStream.seek(zimFile.getTitlePtrPos() + BYTES_PER_INT * position);
        int urlPosition = inputStream.readIntLe();
        DirectoryEntry entry = getDirectoryEntryAtUrlPosition(urlPosition);
        entry.setTitleListIndex(position);
        entryByTitleCache.put(position, entry);
        return entry;
    }

    private synchronized DirectoryEntry getDirectoryEntryAtUrlPosition(int position) throws IOException {
        if (entryByUrlCache.get(position) != null) {
            return entryByUrlCache.get(position);
        }
        inputStream.seek(zimFile.getUrlPtrPos() + BYTES_PER_LONG * position);

        // Go to the location of the directory entry
        inputStream.seek(inputStream.readLongLe());
        int type = inputStream.readShortLe();

        // ignore parameter length
        inputStream.read();

        char namespace = (char) inputStream.read();
        int revision = inputStream.readIntLe();

        DirectoryEntry entry;
        if (type == DirectoryEntry.TYPE_REDIRECT) {
            int redirectIndex = inputStream.readIntLe();
            String url = inputStream.readString();
            String title = inputStream.readString();
            title = title.length() == 0 ? url : title;
            entry = new RedirectEntry(type, namespace, revision, redirectIndex, url, title, position);

        } else {
            int clusterNumber = inputStream.readIntLe();
            int blobNumber = inputStream.readIntLe();
            String url = inputStream.readString();
            String title = inputStream.readString();
            title = title.length() == 0 ? url : title;
            entry = new ArticleEntry(type, namespace, revision, clusterNumber, blobNumber, url, title, position);
        }
        entryByUrlCache.put(position, entry);
        return entry;
    }

    private DirectoryEntry resolveRedirect(DirectoryEntry inEntry) throws IOException {
        DirectoryEntry entry = inEntry;
        final int maxRedirects = 16;
        for (int i = 0; i < maxRedirects; i++) {
            if (!(entry instanceof RedirectEntry)) {
                break;
            }
            entry = getDirectoryEntryAtUrlPosition(((RedirectEntry) entry).getRedirectIndex());
        }
        if (entry instanceof RedirectEntry) {
            throw new IOException("Too many redirects.");
        }
        return entry;
    }

    private static void skipFully(InputStream stream, long bytes) throws IOException {
        while (bytes > 0) {
            bytes -= stream.skip(bytes);
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static int getIntLe(byte[] buffer) {
        return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
    }
}
