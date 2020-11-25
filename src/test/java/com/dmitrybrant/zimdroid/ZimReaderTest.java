package com.dmitrybrant.zimdroid;

import android.util.LruCache;

import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class ZimReaderTest {
    private static final String RAW_DIR = "src/test/res/raw/";
    private static final String TEST_ZIM_FILE = "wikipedia_en_ray_charles_2015-06.zim";
    private static final String ZIM_ZERO_BYTES = "zero_bytes.zim";
    private static final String ZIM_CORRUPT_HEADER = "corrupt_header.zim";
    private static final String ZIM_MALFORMED_HEADER = "malformed_header.zim";
    private static final String ZIM_NO_CONTENT_AFTER_HEADER = "cut_off_after_header.zim";

    @Mock LruCache<Integer, DirectoryEntry> mockCache = mock(LruCache.class);

    @Test
    public void testZimReaderMetadata() throws Exception {
        when(mockCache.get(any(Integer.TYPE))).thenReturn(null);
        ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + TEST_ZIM_FILE), mockCache, mockCache);
        try {

            assertEquals(reader.getZimTitle(), "Wikipedia");
            assertEquals(reader.getZimDescription(), "From Wikipedia, the free encyclopedia");
            assertEquals(reader.getZimDate(), new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse("2015-06-02"));

            assertTrue(reader.getRandomTitle().length() > 0);

            String mainTitle = reader.getMainPageTitle();
            assertEquals(mainTitle, "Summary");

        } finally {
            reader.close();
        }
    }

    @Test
    public void testZimReaderPrefixSearch() throws Exception {
        when(mockCache.get(any(Integer.TYPE))).thenReturn(null);
        ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + TEST_ZIM_FILE), mockCache, mockCache);
        try {

            List<String> results = reader.searchByPrefix("R", 2);
            assertEquals(results.size(), 2);
            assertEquals(results.get(0), "Raelette");

        } finally {
            reader.close();
        }
    }

    @Test
    public void testZimReaderGetArticleContent() throws Exception {
        when(mockCache.get(any(Integer.TYPE))).thenReturn(null);
        ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + TEST_ZIM_FILE), mockCache, mockCache);
        try {

            String normalizedTitle = reader.getNormalizedTitle("A Fool for You");
            assertEquals(normalizedTitle, "A Fool for You");

            normalizedTitle = reader.getNormalizedTitle("ray charles");
            assertEquals(normalizedTitle, "Ray Charles");

            String html = reader.getDataForTitle(normalizedTitle).toString("utf-8");
            assertTrue(html.startsWith("<html>"));
            assertTrue(html.endsWith("</html>"));

            reader.setLzmaDictSize(2 * 1024 * 1024);

            html = reader.getDataForTitle(normalizedTitle).toString("utf-8");
            assertTrue(html.startsWith("<html>"));
            assertTrue(html.endsWith("</html>"));

        } finally {
            reader.close();
        }
    }

    @Test
    public void testZimReaderGetMediaContent() throws Exception {
        when(mockCache.get(any(Integer.TYPE))).thenReturn(null);
        ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + TEST_ZIM_FILE), mockCache, mockCache);
        try {

            reader.setLzmaDictSize(2 * 1024 * 1024);

            byte[] bytes = reader.getDataForUrl("I/m/Ray_C._Geor.jpg").toByteArray();
            assertEquals(bytes[0], (byte) 0xFF);
            assertEquals(bytes[bytes.length - 1], (byte) 0xD9);

        } finally {
            reader.close();
        }
    }

    @Test
    public void testZimReaderZeroLength() throws Exception {
        try {
            new ZimReader(new ZimFile(RAW_DIR + ZIM_ZERO_BYTES), mockCache, mockCache);
            assertTrue("Should not reach this point.", false);
        } catch (IOException e){
            //
        }
    }

    @Test
    public void testZimReaderMalformedHeader() throws Exception {
        try {
            new ZimReader(new ZimFile(RAW_DIR + ZIM_MALFORMED_HEADER), mockCache, mockCache);
            assertTrue("Should not reach this point.", false);
        } catch (IOException e){
            //
        }
    }

    @Test
    public void testZimReaderCorruptHeader() throws Exception {
        try {
            new ZimReader(new ZimFile(RAW_DIR + ZIM_CORRUPT_HEADER), mockCache, mockCache);
            assertTrue("Should not reach this point.", false);
        } catch (IOException e){
            //
        }
    }

    @Test
    public void testZimReaderNoContentAfterHeader() throws Exception {
        try {
            when(mockCache.get(any(Integer.TYPE))).thenReturn(null);
            ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + ZIM_NO_CONTENT_AFTER_HEADER), mockCache, mockCache);

            reader.getZimTitle();
            assertTrue("Should not reach this point.", false);
        } catch (IOException e){
            //
        }
    }
}