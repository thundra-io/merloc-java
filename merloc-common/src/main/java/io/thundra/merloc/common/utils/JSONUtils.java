package io.thundra.merloc.common.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Utility class for providing JSON related stuff.
 *
 * @author serkan
 */
public final class JSONUtils {

    private JSONUtils() {
    }

    public static String getStringProperty(JSONObject obj, String propName, String defaultValue) {
        try {
            if (obj.has(propName)) {
                return obj.getString(propName);
            }
        } catch (JSONException e) {
        }
        return defaultValue;
    }

    public static String getStringProperty(JSONObject obj, String propName) {
        return getStringProperty(obj, propName, null);
    }

    public static Boolean getBooleanProperty(JSONObject obj, String propName, Boolean defaultValue) {
        try {
            if (obj.has(propName)) {
                return obj.getBoolean(propName);
            }
        } catch (JSONException e) {
        }
        return defaultValue;
    }

    public static Boolean getBooleanProperty(JSONObject obj, String propName) {
        return getBooleanProperty(obj, propName, null);
    }

    public static Integer getIntegerProperty(JSONObject obj, String propName, Integer defaultValue) {
        try {
            if (obj.has(propName)) {
                return obj.getInt(propName);
            }
        } catch (JSONException e) {
        }
        return defaultValue;
    }

    public static Integer getIntegerProperty(JSONObject obj, String propName) {
        return getIntegerProperty(obj, propName, null);
    }

    public static Long getLongProperty(JSONObject obj, String propName, Long defaultValue) {
        try {
            if (obj.has(propName)) {
                return obj.getLong(propName);
            }
        } catch (JSONException e) {
        }
        return defaultValue;
    }

    public static Long getLongProperty(JSONObject obj, String propName) {
        return getLongProperty(obj, propName, null);
    }

    public static Double getDoubleProperty(JSONObject obj, String propName, Double defaultValue) {
        try {
            if (obj.has(propName)) {
                return obj.getDouble(propName);
            }
        } catch (JSONException e) {
        }
        return defaultValue;
    }

    public static Double getDoubleProperty(JSONObject obj, String propName) {
        return getDoubleProperty(obj, propName, null);
    }

    public static JSONObject getObjectProperty(JSONObject obj, String propName) {
        try {
            if (obj.has(propName)) {
                return obj.getJSONObject(propName);
            }
        } catch (JSONException e) {
        }
        return null;
    }

    public static Map<String, Object> getObjectPropertyAsMap(JSONObject obj, String propName) {
        try {
            if (obj.has(propName)) {
                return obj.getJSONObject(propName).toMap();
            }
        } catch (JSONException e) {
        }
        return null;
    }

}
