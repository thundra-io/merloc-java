package io.thundra.merloc.aws.lambda.core.config;

import io.thundra.merloc.aws.lambda.core.utils.StringUtils;

/**
 * Class for providing configurations.
 *
 * @author serkan
 */
public final class ConfigManager {

    private ConfigManager() {
    }

    public static String getSystemPropertyName(String configName) {
        return configName;
    }

    public static String getEnvironmentVariableName(String configName) {
        return StringUtils.toUpperCase(configName).replace(".", "_");
    }

    public static String getConfig(String configName) {
        String sysPropName = getSystemPropertyName(configName);
        String sysPropValue = System.getProperty(sysPropName);
        if (StringUtils.hasValue(sysPropValue)) {
            return sysPropValue;
        }

        String envVarName = getEnvironmentVariableName(configName);
        String envVarValue = System.getenv(envVarName);
        if (StringUtils.hasValue(envVarValue)) {
            return envVarValue;
        }

        return null;
    }

    public static String getConfig(String configName, String defaultValue) {
        String configValue = getConfig(configName);
        if (configValue != null) {
            return configValue;
        }
        return defaultValue;
    }

    public static Boolean getBooleanConfig(String configName) {
        String configValue = getConfig(configName);
        if (configValue != null) {
            return Boolean.parseBoolean(configValue);
        }
        return null;
    }

    public static Boolean getBooleanConfig(String configName, boolean defaultValue) {
        Boolean configValue = getBooleanConfig(configName);
        if (configValue != null) {
            return configValue;
        }
        return defaultValue;
    }

    public static Integer getIntegerConfig(String configName) {
        String configValue = getConfig(configName);
        if (configValue != null) {
            return Integer.parseInt(configValue);
        }
        return null;
    }

    public static Integer getIntegerConfig(String configName, int defaultValue) {
        Integer configValue = getIntegerConfig(configName);
        if (configValue != null) {
            return configValue;
        }
        return defaultValue;
    }

    public static Long getLongConfig(String configName) {
        String configValue = getConfig(configName);
        if (configValue != null) {
            return Long.parseLong(configValue);
        }
        return null;
    }

    public static Long getLongConfig(String configName, long defaultValue) {
        Long configValue = getLongConfig(configName);
        if (configValue != null) {
            return configValue;
        }
        return defaultValue;
    }

}
