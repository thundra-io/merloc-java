package io.thundra.merloc.common.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Utilities for {@link sun.misc.Unsafe} related stuff.
 */
public final class UnsafeUtils {

    private static final Unsafe unsafe;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Unsafe", e);
        }
    }

    private UnsafeUtils() {
    }

    public static Unsafe unsafe() {
        return unsafe;
    }

    public static void disableIllegalAccessWarning() {
        try {
            Class illegalAccessLoggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field loggerField = illegalAccessLoggerClass.getDeclaredField("logger");
            unsafe.putObjectVolatile(illegalAccessLoggerClass, unsafe.staticFieldOffset(loggerField), null);
        } catch (Throwable t) {
        }
    }

}
