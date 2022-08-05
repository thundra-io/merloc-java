package io.thundra.merloc.common.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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

    public static void setObjectField(Object obj, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);

        Class clazz = obj.getClass();
        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                int modifiers = field.getModifiers();
                modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
                try {
                    field.setAccessible(true);
                    field.set(obj, value);
                } finally {
                    modifiersField.setInt(field, modifiers);
                }
                break;
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static void setClassField(Class clazz, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);

        while (clazz != Object.class) {
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Throwable t) {
            }
            if (field != null) {
                int modifiers = field.getModifiers();
                modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
                try {
                    field.setAccessible(true);
                    field.set(null, value);
                } finally {
                    modifiersField.setInt(field, modifiers);
                }
                break;
            }
            clazz = clazz.getSuperclass();
        }
    }

}
