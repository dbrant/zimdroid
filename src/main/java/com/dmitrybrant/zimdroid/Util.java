package com.dmitrybrant.zimdroid;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class Util {

    public static String capitalize(String str) {
        if (str.length() >= 1) {
            return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
        }
        return str;
    }

    public static void skipFully(InputStream stream, long bytes) throws IOException {
        while (bytes > 0) {
            bytes -= stream.skip(bytes);
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public static int getIntLe(byte[] buffer) {
        return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
    }

    private Util() {
    }
}
