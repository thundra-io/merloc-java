package io.thundra.merloc.aws.lambda.core.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for providing string related stuff.
 *
 * @author serkan
 */
public final class StringUtils {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private StringUtils() {
    }

    public static boolean hasValue(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean isNullOrEmpty(String str) {
        return !hasValue(str);
    }

    public static String compressAndEncode(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length() / 8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data.getBytes("UTF-8"));
            gzip.finish();
            gzip.flush();
            return ENCODER.encodeToString(baos.toByteArray());
        }
    }

    public static String decodeAndDecompress(String data) throws IOException {
        byte[] decodedData = DECODER.decode(data);
        ByteArrayInputStream bais = new ByteArrayInputStream(decodedData);
        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            StringBuilder sb = new StringBuilder(data.length() * 16);
            for (int i = gzip.read(); i >= 0; i = gzip.read()) {
                sb.append((char) i);
            }
            return sb.toString();
        }
    }

    public static String toLowerCase(String str) {
        return str.toLowerCase(Locale.ROOT);
    }

    public static String toUpperCase(String str) {
        return str.toUpperCase(Locale.ROOT);
    }

}

