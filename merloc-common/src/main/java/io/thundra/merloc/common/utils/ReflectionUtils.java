package io.thundra.merloc.common.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Utility class for providing reflection related stuff.
 *
 * @author serkan
 */
public final class ReflectionUtils {

    private ReflectionUtils() {
    }

    public static <T> T getObjectField(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Class clazz = obj.getClass();
        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                field.setAccessible(true);
                return (T) field.get(obj);
            }
            clazz = clazz.getSuperclass();
        }
        throw new NoSuchFieldException("No such field: " + fieldName);
    }

    public static <T> T getClassField(Class clazz, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                field.setAccessible(true);
                return (T) field.get(null);
            }
            clazz = clazz.getSuperclass();
        }
        throw new NoSuchFieldException("No such field: " + fieldName);
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        Unsafe unsafe = UnsafeUtils.unsafe();

        Class clazz = obj.getClass();
        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                long fieldOffset = unsafe.objectFieldOffset(field);
                unsafe.putObject(value, fieldOffset, value);
                break;
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static void setClassField(Class clazz, String fieldName, Object value) {
        Unsafe unsafe = UnsafeUtils.unsafe();

        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                Object fieldBase = unsafe.staticFieldBase(field);
                long fieldOffset = unsafe.staticFieldOffset(field);
                unsafe.putObject(fieldBase, fieldOffset, value);
                break;
            }
            clazz = clazz.getSuperclass();
        }
    }

}
