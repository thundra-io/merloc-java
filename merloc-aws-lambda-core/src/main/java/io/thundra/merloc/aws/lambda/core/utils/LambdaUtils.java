package io.thundra.merloc.aws.lambda.core.utils;

import com.amazonaws.services.lambda.runtime.Context;

import java.util.Map;

/**
 * @author serkan
 */
public final class LambdaUtils {

    private LambdaUtils() {
    }

    public static String getRegion() {
        return getEnvVar("AWS_REGION");
    }

    public static Integer getMemorySize() {
        String memorySizeValue = getEnvVar("AWS_LAMBDA_FUNCTION_MEMORY_SIZE");
        if (StringUtils.isNullOrEmpty(memorySizeValue)) {
            return -1;
        }
        return Integer.parseInt(memorySizeValue);
    }

    public static String getFunctionName() {
        return getEnvVar("AWS_LAMBDA_FUNCTION_NAME");
    }

    public static String getFunctionVersion(Context context) {
        try {
            return context.getFunctionVersion();
        } catch (NoSuchMethodError error) {
            return getFunctionVersion();
        }
    }

    public static String getFunctionVersion() {
        String functionVersion = getEnvVar("AWS_LAMBDA_FUNCTION_VERSION");
        if (functionVersion == null) {
            String logStreamName = getLogStreamName();
            if (logStreamName != null) {
                int versionStart = logStreamName.indexOf("[");
                int versionEnd = logStreamName.indexOf("]", versionStart);
                if (versionStart > 0 && versionEnd > 0) {
                    functionVersion = logStreamName.substring(versionStart + 1, versionEnd);
                }
            }
        }
        return functionVersion;
    }

    public static String getInvokedFunctionArn(Context context) {
        try {
            return context.getInvokedFunctionArn();
        } catch (NoSuchMethodError error) {
            return null;
        }
    }

    public static String getLogGroupName() {
        return getEnvVar("AWS_LAMBDA_LOG_GROUP_NAME");
    }

    public static String getLogStreamName() {
        return getEnvVar("AWS_LAMBDA_LOG_STREAM_NAME");
    }

    public static String getInstanceId() {
        String logStreamName = getLogStreamName();
        if (!StringUtils.hasValue(logStreamName)) {
            return null;
        }
        int logStreamNameStart = logStreamName.lastIndexOf("]");
        if (logStreamNameStart > 0) {
            return logStreamName.substring(logStreamNameStart + 1);
        } else {
            return logStreamName;
        }
    }

    public static String getAccountId(Context context) {
        if (context != null && StringUtils.hasValue(getInvokedFunctionArn(context))) {
            String[] arnParts = getInvokedFunctionArn(context).split(":");
            if (arnParts.length >= 5) {
                return arnParts[4];
            }
        }
        return null;
    }

    public static String getAlias(Context context) {
        if (context != null && StringUtils.hasValue(getInvokedFunctionArn(context))) {
            String[] arnParts = getInvokedFunctionArn(context).split(":");
            if (arnParts.length == 8) {
                return arnParts[7];
            }
        }
        return null;
    }

    public static String getEnvVar(String name) {
        return EnvironmentVariableUtils.get(name);
    }

    public static Map<String, String> getEnvVars() {
        return EnvironmentVariableUtils.getAll();
    }

    public static String getEnvVarCaseInsensitive(String name, String defaultValue) {
        for (Map.Entry<String, String> e : EnvironmentVariableUtils.getAll().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return defaultValue;
    }

    public static String getEnvVarCaseInsensitive(String name) {
        return getEnvVarCaseInsensitive(name, null);
    }

    public static String getInstanceId(Context context) {
        String logStreamName = context.getLogStreamName();
        if (!StringUtils.hasValue(logStreamName)) {
            return null;
        }
        int logStreamNameStart = logStreamName.lastIndexOf("]");
        if (logStreamNameStart > 0) {
            return logStreamName.substring(logStreamNameStart + 1);
        } else {
            return logStreamName;
        }
    }

}
