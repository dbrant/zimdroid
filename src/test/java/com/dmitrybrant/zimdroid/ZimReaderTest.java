package com.dmitrybrant.zimdroid;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ZimReaderTest {
    private static final String RAW_DIR = "src/test/res/raw/";
    private static final String TEST_ZIM_FILE = "wikipedia_en_ray_charles_2015-06.zim";

    @Test
    public void testZimReader() throws Exception {

        ZIMReader reader = new ZIMReader(new ZIMFile(RAW_DIR + TEST_ZIM_FILE));
        try {

            assertTrue(reader.getRandomTitle().length() > 0);

            assertEquals(reader.getZimTitle(), "Wikipedia");
            assertEquals(reader.getZimDescription(), "From Wikipedia, the free encyclopedia");

            List<String> results = reader.searchByPrefix("R", 2);
            assertEquals(results.size(), 2);
            assertEquals(results.get(0), "Raelette");

            String normalizedTitle = reader.getNormalizedTitle("Ray charles");
            assertEquals(normalizedTitle, "Ray Charles");

            String html = reader.getDataForTitle(normalizedTitle).toString("utf-8");
            assertTrue(html.startsWith("<html>"));

        } finally {
            reader.close();
        }
    }
}