/*
 * LZMA2Decoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

class LZMA2Decoder extends LZMA2Coder implements FilterDecoder {

    private static int DEFAULT_DICT_SIZE = 0;

    public static void setDefaultDictSize(int dictSize) {
        DEFAULT_DICT_SIZE = dictSize;
    }

    private int dictSize;

    LZMA2Decoder(byte[] props) throws UnsupportedOptionsException {
        // Up to 1.5 GiB dictionary is supported. The bigger ones
        // are too big for int.
        if (props.length != 1 || (props[0] & 0xFF) > 37)
            throw new UnsupportedOptionsException(
                    "Unsupported LZMA2 properties");

        if (DEFAULT_DICT_SIZE > 0) {
            dictSize = DEFAULT_DICT_SIZE;
        } else {
            dictSize = 2 | (props[0] & 1);
            dictSize <<= (props[0] >>> 1) + 11;
        }
    }

    public int getMemoryUsage() {
        return LZMA2InputStream.getMemoryUsage(dictSize);
    }

    public InputStream getInputStream(InputStream in) {
        return new LZMA2InputStream(in, dictSize);
    }
}
