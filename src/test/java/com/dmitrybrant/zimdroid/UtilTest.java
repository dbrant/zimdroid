package com.dmitrybrant.zimdroid;

import org.junit.Test;

import static org.junit.Assert.*;

public class UtilTest {

    @Test
    public void testCapitalize() throws Exception {
        assertEquals(Util.capitalize("wikipedia"), "Wikipedia");
        assertEquals(Util.capitalize("a"), "A");
        assertEquals(Util.capitalize("Z"), "Z");
    }

    @Test
    @SuppressWarnings("checkstyle:magicnumber")
    public void testGetIntLe() throws Exception {
        byte[] bytes = new byte[] { (byte)0xAA, (byte)0xBB, (byte)0xCC, (byte)0xDD };
        assertEquals(Util.getIntLe(bytes), 0xDDCCBBAA);
    }
}