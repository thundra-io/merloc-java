package io.thundra.merloc.common.utils;

/**
 * Utility class for providing instance/object related stuff.
 *
 * @author serkan
 */
public final class ClassUtils {

    public static final String CLASS_FILE_EXTENSION = "class";

    private ClassUtils() {
    }

    public static <T> Class<T> getClass(String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static <T> Class<T> getClassWithException(String className) throws ClassNotFoundException {
        return (Class<T>) Class.forName(className);
    }

    public static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static <T> Class<T> getClass(ClassLoader classLoader, String className) {
        try {
            return (Class<T>) Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static <T> Class<T> getClass(ClassLoader classLoader, String className, boolean initialize) {
        try {
            return (Class<T>) Class.forName(className, initialize, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static <T> Class<T> getClassWithException(ClassLoader classLoader, String className, boolean initialize)
            throws ClassNotFoundException {
        return (Class<T>) Class.forName(className, initialize, classLoader);
    }

    public static <T> Class<T> getBootstrapClassWithException(String className, boolean initialize)
            throws ClassNotFoundException {
        return (Class<T>) Class.forName(className, initialize, null);
    }

    public static boolean hasClass(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String toClassFilePath(String className) {
        return className.replace(".", "/") + "." + CLASS_FILE_EXTENSION;
    }

}
