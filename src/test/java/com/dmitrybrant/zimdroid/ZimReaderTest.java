package com.dmitrybrant.zimdroid;

import android.util.LruCache;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ZimReaderTest {
    private static final String RAW_DIR = "src/test/res/raw/";
    private static final String TEST_ZIM_FILE = "wikipedia_en_ray_charles_2015-06.zim";

    @Mock LruCache<Integer, DirectoryEntry> mockCache;

    @Test
    public void testZimReader() throws Exception {

        when(mockCache.get(any(Integer.TYPE))).thenReturn(null);

        ZimReader reader = new ZimReader(new ZimFile(RAW_DIR + TEST_ZIM_FILE), mockCache, mockCache);
        try {

            assertTrue(reader.getRandomTitle().length() > 0);

            assertEquals(reader.getZimTitle(), "Wikipedia");
            assertEquals(reader.getZimDescription(), "From Wikipedia, the free encyclopedia");

            List<String> results = reader.searchByPrefix("R", 2);
            assertEquals(results.size(), 2);
            assertEquals(results.get(0), "Raelette");

            String normalizedTitle = reader.getNormalizedTitle("ray charles");
            assertEquals(normalizedTitle, "Ray Charles");

            String mainTitle = reader.getMainPageTitle();
            assertEquals(mainTitle, "Summary");

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


}