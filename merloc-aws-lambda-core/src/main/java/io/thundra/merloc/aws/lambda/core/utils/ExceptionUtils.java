package io.thundra.merloc.aws.lambda.core.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for exception related stuffs.
 *
 * @author serkan
 */
public final class ExceptionUtils {

    private static final int MAX_COMPRESSED_EXCEPTION_STACK_TRACE_DEPTH =
            Integer.getInteger("merloc.exception.maxcompressedexceptionstacktracedepth", 20);
    private static final boolean DISABLE_COMPRESSED_EXCEPTION =
            Boolean.getBoolean("merloc.exception.disablecompressedexception");

    private ExceptionUtils() {
    }

    public static String toString(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }

    public static String toCompressedString(Throwable error) {
        if (DISABLE_COMPRESSED_EXCEPTION) {
            return toString(error);
        }
        StringBuilder sb = new StringBuilder(4096);
        sb.append(error.toString()).append("\n");
        StackTraceElement[] trace = error.getStackTrace();
        for (int i = 0; i < trace.length && i < MAX_COMPRESSED_EXCEPTION_STACK_TRACE_DEPTH; i++) {
            StackTraceElement ste = trace[i];
            sb.append("\tat ").append(compressStackTraceElement(ste)).append("\n");
        }
        return sb.toString();
    }

    private static String compressPackageName(String packageName) {
        StringBuilder sb = new StringBuilder();
        String[] packageNameParts = packageName.split("\\.");
        for (int i = 0; i < packageNameParts.length; i++) {
            if (i < packageNameParts.length - 1) {
                sb.append(packageNameParts[i].charAt(0)).append(".");
            } else {
                sb.append(packageNameParts[i]);
            }
        }
        return sb.toString();
    }

    private static String compressStackTraceElement(StackTraceElement ste) {
        return serializeStackTraceElement(ste, true);
    }

    public static String serializeStackTraceElement(StackTraceElement ste) {
        return serializeStackTraceElement(ste, false);
    }

    public static String serializeStackTraceElement(StackTraceElement ste, boolean compress) {
        String fullClassName = ste.getClassName();
        int classNameSeparator = fullClassName.lastIndexOf(".");
        String packageName = classNameSeparator > 0 ? fullClassName.substring(0, classNameSeparator) : null;
        String className = classNameSeparator > 0 ? fullClassName.substring(classNameSeparator + 1) : fullClassName;
        String methodName = ste.getMethodName();
        String fileName = ste.getFileName();
        boolean isNativeMethod = ste.isNativeMethod();
        int lineNumber = ste.getLineNumber();

        StringBuilder sb = new StringBuilder();

        if (packageName != null && compress) {
            packageName = compressPackageName(packageName);
        }
        if (packageName != null) {
            sb.append(packageName).append(".");
        }
        sb.append(className).append(".").append(methodName);

        if (isNativeMethod) {
            sb.append("(Native Method)");
        } else {
            if (fileName != null && lineNumber >= 0) {
                sb.append("(").append(fileName).append(":").append(lineNumber).append(")");
            } else {
                if (fileName != null) {
                    sb.append("(").append(fileName).append(")");
                } else {
                    sb.append("(Unknown Source)");
                }
            }
        }

        return sb.toString();
    }

    public static <T> T sneakyThrow(Throwable t) {
        ExceptionUtils.<RuntimeException>sneakyThrowInternal(t);
        return (T) t;
    }

    private static <T extends Throwable> void sneakyThrowInternal(Throwable t) throws T {
        throw (T) t;
    }

}
