package io.thundra.merloc.common.utils;

import io.thundra.merloc.common.logger.StdLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

/**
 * Utility class for providing I/O related stuff.
 *
 * @author serkan
 */
public final class IOUtils {

    private IOUtils() {
    }

    public static byte[] readAll(InputStream is) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
            return os.toByteArray();
        }
    }

    public static String readAllAsString(InputStream is) throws IOException {
        return new String(readAll(is), StandardCharsets.UTF_8);
    }

    public static String calculateMD5(byte[] data) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            StdLogger.error("Unable to get MD5 message digest", e);
            return null;
        }
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static String calculateMD5(String data) {
        try {
            return calculateMD5(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            StdLogger.error("Unable to encode data", e);
            return null;
        }
    }

    public static boolean isCollectorResponseErroneous(int responseCode) {
        // We take non-2xx response codes (except 429) as erroneous.
        // We skip 429 status code as it is used for rate/usage limit errors
        // and it is a kind of expected error
        return (responseCode / 100) != 2 && responseCode != 429;
    }

    public static InputStream getResourceAsStream(ClassLoader classLoader, String name) {
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return classLoader.getResourceAsStream(name);
    }

    public static Enumeration<URL> getResources(ClassLoader classLoader, String name) throws IOException {
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return classLoader.getResources(name);
    }

}
