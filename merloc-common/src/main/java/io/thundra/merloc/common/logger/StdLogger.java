package io.thundra.merloc.common.logger;

import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.common.utils.ExceptionUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Standard logger implementation which prints to <code>stdout</code> or <code>stderr</code>.
 *
 * @author serkan
 */
public final class StdLogger {

    private static final String MERLOC_PREFIX = "[MERLOC] ";
    private static final String DEBUG_LEVEL = "DEBUG ";
    private static final String INFO_LEVEL = "INFO  ";
    private static final String WARN_LEVEL = "WARN  ";
    private static final String ERROR_LEVEL = "ERROR ";
    private static final String DEBUG_ENABLE_ENV_VAR_NAME = "MERLOC_DEBUG_ENABLE";
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static final boolean DEBUG_ENABLED = isDebugEnabled();

    private StdLogger() {
    }

    private static boolean isDebugEnabled() {
        return ConfigManager.getBooleanConfig(DEBUG_ENABLE_ENV_VAR_NAME, false);
    }

    private static String getTime() {
        return TIME_FORMAT.format(new Date());
    }

    private static String getLogPrefix(String level) {
        return MERLOC_PREFIX + level + getTime() + " [" + Thread.currentThread().getName() + "] " + ": ";
    }

    public static void debug(String message) {
        if (DEBUG_ENABLED) {
            System.out.println(getLogPrefix(DEBUG_LEVEL) + message);
        }
    }

    public static void debug(String message, Throwable error) {
        if (DEBUG_ENABLED) {
            System.out.println(getLogPrefix(DEBUG_LEVEL) + message);
            System.err.println(ExceptionUtils.toString(error));
        }
    }

    public static void info(String message) {
        System.out.println(getLogPrefix(INFO_LEVEL) + message);
    }

    public static void warn(String message) {
        System.err.println(getLogPrefix(WARN_LEVEL) + message);
    }

    public static void error(String message) {
        System.err.println(getLogPrefix(ERROR_LEVEL) + message);
    }

    public static void error(String message, Throwable error) {
        System.err.println(getLogPrefix(ERROR_LEVEL) + message);
        System.err.println(ExceptionUtils.toString(error));
    }

}
