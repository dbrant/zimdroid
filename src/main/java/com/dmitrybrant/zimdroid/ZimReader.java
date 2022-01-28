package com.dmitrybrant.zimdroid;

import android.util.LruCache;

import org.tukaani.xz.SingleXZInputStream;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reads content and metadata from a ZIM file.
 * Copyright Dmitry Brant, 2017-2018.
 *
 * Loosely based on original implementation by Arunesh Mathur
 */
public class ZimReader implements Closeable {
    private static final char NAMESPACE_ARTICLE = 'A';
    private static final char NAMESPACE_MEDIA = 'I';
    private static final char NAMESPACE_META = 'M';

    private static final int COMPRESSION_TYPE_NONE = 0;
    private static final int COMPRESSION_TYPE_NONE_OLD = 1;
    private static final int COMPRESSION_TYPE_LZMA = 4;
    private static final int BYTES_PER_INT = 4;
    private static final int BYTES_PER_LONG = 8;
    private static final int CACHE_SIZE = 256;

    private ZimFile zimFile;
    private ZimInputStream inputStream;

    private final LruCache<Integer, DirectoryEntry> entryByTitleCache;
    private final LruCache<Integer, DirectoryEntry> entryByUrlCache;
    private int lzmaDictSize;

    private String zimTitle;
    private String zimDescription;
    private Date zimDate;

    private int firstArticleTitleIndex = -1;
    private int lastArticleTitleIndex = -1;

    /**
     * Construct a ZIM reader that operates on the given ZIM file.
     * @param file ZIM file from which to read content.
     */
    public ZimReader(ZimFile file) {
        init(file);
        entryByTitleCache = new LruCache<>(CACHE_SIZE);
        entryByUrlCache = new LruCache<>(CACHE_SIZE);
    }

    /**
     * For testing purposes only. Do not use this constructor for production.
     * @param file ZIM file from which to read content.
     * @param titleCache Object for caching Title pointers. Can be mocked with get() returning null.
     * @param urlCache Object for caching Url pointers. Can be mocked with get() returning null.
     */
    public ZimReader(ZimFile file, LruCache<Integer, DirectoryEntry> titleCache, LruCache<Integer, DirectoryEntry> urlCache) {
        init(file);
        entryByTitleCache = titleCache;
        entryByUrlCache = urlCache;
    }

    private void init(ZimFile file) {
        zimFile = file;
        try {
            inputStream = new ZimInputStream(new FileInputStream(zimFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    /**
     * Set the dictionary size that will be used by the LZMA decoder. This is useful for
     * constraining the decoder's memory usage in environments with very little memory, e.g.
     * Android devices with a small Java VM size.  The tradeoff is that if a custom dictionary
     * size is set, it will not be possible to decode chunks of data whose uncompressed size
     * is larger than the dictionary size.
     * @param dictSize If this is set to 0, the decoder will use the default dictionary size that is
     *                 dictated by the actual file that's being processed. Otherwise, the specified
     *                 size will be used.
     */
    public void setLzmaDictSize(int dictSize) {
        lzmaDictSize = dictSize;
    }

    public String getZimTitle() throws IOException {
        if (zimTitle == null || zimTitle.length() == 0) {
            ByteArrayOutputStream stream = getDataForMetaTag("Title");
            zimTitle = stream != null ? stream.toString("utf-8") : "";
        }
        return zimTitle;
    }

    public String getZimDescription() throws IOException {
        if (zimDescription == null || zimDescription.length() == 0) {
            ByteArrayOutputStream stream = getDataForMetaTag("Description");
            if (stream != null) {
                zimDescription = stream.toString("utf-8");
            } else {
                stream = getDataForMetaTag("Subtitle");
                zimDescription = stream != null ? stream.toString("utf-8") : "";
            }
        }
        return zimDescription;
    }

    public Date getZimDate() {
        if (zimDate == null) {
            try {
                ByteArrayOutputStream stream = getDataForMetaTag("Date");
                String dateStr = stream != null ? stream.toString("utf-8") : "";
                zimDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(dateStr);
            } catch (IOException e) {
                // Keep our internal date null, so we'll try again next time.
            } catch (ParseException e) {
                // The date string is actually malformed, so give up.
                zimDate = new Date(zimFile.lastModified());
            }
        }
        return zimDate != null ? zimDate : new Date(zimFile.lastModified());
    }

    public String getRandomTitle() throws IOException {
        int first = getFirstArticleTitleIndex();
        int last = getLastArticleTitleIndex();
        int index = first + new Random().nextInt(last - first);
        DirectoryEntry entry = resolveRedirect(getDirectoryEntryAtTitlePosition(index));
        return entry.getTitle();
    }

    public String getMainPageTitle() throws IOException {
        if (zimFile.getMainPage() < 0) {
            throw new IOException("The ZIM file does not contain a main page.");
        }
        DirectoryEntry entry = getDirectoryEntryAtUrlPosition(zimFile.getMainPage());
        return entry.getTitle();
    }

    public List<String> searchByPrefix(String prefix, int maxResults) throws IOException {
        List<String> results = new ArrayList<>();
        prefix = Util.capitalize(prefix);
        DirectoryEntry entry = binarySearchByTitle(NAMESPACE_ARTICLE, prefix, true);
        if (entry == null) {
            return results;
        }
        int index = entry.getTitleListIndex();
        if (index < 0) {
            index = 0;
        }

        for (int i = 0; i < maxResults; i++) {
            entry = getDirectoryEntryAtTitlePosition(index);

            if (entry.getTitle().startsWith(prefix)) {
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
        DirectoryEntry entry = binarySearchByTitle(NAMESPACE_ARTICLE, Util.capitalize(title), false);
        if (entry == null) {
            return null;
        }
        return resolveRedirect(entry).getTitle();
    }

    public ByteArrayOutputStream getDataForUrl(String url) throws IOException {
        String[] urlParts = url.split("/");
        if (urlParts.length > 0 && urlParts[0].length() > 0 && url.length() > (urlParts[0].length() + 1)) {
            return getData(binarySearchByUrl(urlParts[0].charAt(0),
                    url.substring(urlParts[0].length() + 1), false));
        } else {
            return getData(binarySearchByUrl(NAMESPACE_ARTICLE, url, false));
        }
    }

    public ByteArrayOutputStream getDataForTitle(String title) throws IOException {
        return getData(binarySearchByTitle(NAMESPACE_ARTICLE, title, false));
    }

    private ByteArrayOutputStream getDataForMetaTag(String title) throws IOException {
        return getData(binarySearchByTitle(NAMESPACE_META, title, false));
    }

    private synchronized ByteArrayOutputStream getData(DirectoryEntry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        entry = resolveRedirect(entry);

        int clusterNumber = ((ArticleEntry) entry).getClusterNumber();
        int blobNumber = ((ArticleEntry) entry).getBlobNumber();

        inputStream.seek(zimFile.getClusterPtrPos() + (long)clusterNumber * BYTES_PER_LONG);

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
                    throw new IOException("Blob number greater than total blobs.");
                }

                if (blobNumber == 0) {
                    // The first offset is what we read earlier
                    offset1 = firstOffset;
                } else {
                    location = (blobNumber - 1) * BYTES_PER_INT;
                    Util.skipFully(inputStream, location);
                    offset1 = inputStream.readIntLe();
                }

                offset2 = inputStream.readIntLe();

                differenceOffset = offset2 - offset1;
                buffer = new byte[differenceOffset];

                Util.skipFully(inputStream, (offset1 - (long)BYTES_PER_INT * (blobNumber + 2)));

                inputStream.read(buffer, 0, differenceOffset);
                outStream.write(buffer, 0, differenceOffset);
                break;

            case COMPRESSION_TYPE_LZMA:

                SingleXZInputStream.setLZMA2DictSize(lzmaDictSize);
                xzReader = new SingleXZInputStream(inputStream);

                buffer = new byte[BYTES_PER_INT];
                xzReader.read(buffer);

                firstOffset = Util.getIntLe(buffer);
                numberOfBlobs = firstOffset / BYTES_PER_INT;

                if (blobNumber >= numberOfBlobs) {
                    throw new IOException("Blob number greater than total blobs.");
                }

                if (blobNumber == 0) {
                    offset1 = firstOffset;
                } else {
                    location = (blobNumber - 1) * BYTES_PER_INT;
                    Util.skipFully(xzReader, location);
                    xzReader.read(buffer);
                    offset1 = Util.getIntLe(buffer);
                }

                xzReader.read(buffer);
                offset2 = Util.getIntLe(buffer);

                differenceOffset = offset2 - offset1;
                buffer = new byte[differenceOffset];

                Util.skipFully(xzReader, (offset1 - (long)BYTES_PER_INT * (blobNumber + 2)));

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

    private DirectoryEntry binarySearchByUrl(char namespace, String url, boolean getClosest) throws IOException {
        DirectoryEntry entry = null;
        int beginIndex = 0, endIndex = beginIndex + zimFile.getArticleCount(), midIndex;

        while (beginIndex <= endIndex) {
            midIndex = beginIndex + ((endIndex - beginIndex) / 2);
            entry = getDirectoryEntryAtUrlPosition(midIndex);
            if (entry == null) {
                return null;
            }

            if (namespace < entry.getNamespace()) {
                endIndex = midIndex - 1;
                continue;
            } else if (namespace > entry.getNamespace()) {
                beginIndex = midIndex + 1;
                continue;
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

    private DirectoryEntry binarySearchByTitle(char namespace, String title, boolean getClosest) throws IOException {
        DirectoryEntry entry = null;
        int beginIndex = 0, endIndex = beginIndex + zimFile.getArticleCount(), midIndex;

        while (beginIndex <= endIndex) {
            midIndex = beginIndex + ((endIndex - beginIndex) / 2);
            entry = getDirectoryEntryAtTitlePosition(midIndex);
            if (entry == null) {
                return null;
            }

            if (namespace < entry.getNamespace()) {
                endIndex = midIndex - 1;
                continue;
            } else if (namespace > entry.getNamespace()) {
                beginIndex = midIndex + 1;
                continue;
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

    private synchronized DirectoryEntry getDirectoryEntryAtTitlePosition(int position) throws IOException {
        if (entryByTitleCache.get(position) != null) {
            return entryByTitleCache.get(position);
        }
        inputStream.seek(zimFile.getTitlePtrPos() + (long)BYTES_PER_INT * position);
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
        inputStream.seek(zimFile.getUrlPtrPos() + (long)BYTES_PER_LONG * position);

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
}
