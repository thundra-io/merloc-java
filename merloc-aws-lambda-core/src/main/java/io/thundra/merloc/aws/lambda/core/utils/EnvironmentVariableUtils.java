package io.thundra.merloc.aws.lambda.core.utils;

import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for environment variable related stuff.
 *
 * @author serkan
 */
public class EnvironmentVariableUtils {

    private EnvironmentVariableUtils() {
    }

    /**
     * Gets a value of an environment variable.
     *
     * @param name  name of the environment variable
     */
    public static String get(String name) {
        return System.getenv(name);
    }

    /**
     * Gets all environment variables.
     *
     * @return the all environment variables
     */
    public static Map<String, String> getAll() {
        return System.getenv();
    }

    /**
     * Set a value of an environment variable.
     *
     * @param name  name of the environment variable
     * @param value value of the environment variable
     *
     * @return value of the existing environment variable,
     *         <code>null</code> if not exist
     */
    public static String set(String name, String value) {
        return modifyEnvironmentVariables(map -> map.put(name, value));
    }

    /**
     * Sets the all environment variable to the given ones, removes non-existing ones.
     *
     * @param envVars the given environment variable to be set
     */
    public static void setAll(Map<String, String> envVars) {
        modifyEnvironmentVariables(map -> {
            map.clear();
            map.putAll(envVars);
            return null;
        });
    }

    /**
     * Clear an environment variable.
     *
     * @param name name of the environment variable
     *
     * @return value of the existing environment variable,
     *         <code>null</code> if not exist
     */
    public static String remove(String name) {
        return modifyEnvironmentVariables(map -> map.remove(name));
    }

    private static String modifyEnvironmentVariables(Function<Map<String, String>, String> function) {
        try {
            return setInProcessEnvironmentClass(function);
        } catch (ReflectiveOperationException ex) {
            try {
                return setInSystemEnvClass(function);
            } catch (ReflectiveOperationException ex2) {
                ex.addSuppressed(ex2);
                throw new UnsupportedOperationException("Could not modify environment variables", ex);
            }
        }
    }

    /*
     * Works on Windows
     */
    private static String setInProcessEnvironmentClass(Function<Map<String, String>, String> function)
            throws ReflectiveOperationException {
        Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");

        // The order of operations is critical here: On some operating systems, theEnvironment is present but
        // theCaseInsensitiveEnvironment is not present. In such cases, this method must throw a
        // ReflectiveOperationException without modifying theEnvironment. Otherwise, the contents of theEnvironment will
        // be corrupted. For this reason, both fields are fetched by reflection before either field is modified.
        Map<String, String> theEnvironment =
                ReflectionUtils.getClassField(processEnvironmentClass, "theEnvironment");
        Map<String, String> theCaseInsensitiveEnvironment =
                ReflectionUtils.getClassField(processEnvironmentClass, "theCaseInsensitiveEnvironment");

        String value = function.apply(theEnvironment);
        function.apply(theCaseInsensitiveEnvironment);

        return value;
    }

    /*
     * Works on Linux and OSX
     */
    private static String setInSystemEnvClass(Function<Map<String, String>, String> function)
            throws ReflectiveOperationException {
        Map<String, String> env = System.getenv();
        return function.apply(ReflectionUtils.getObjectField(env, "m"));
    }

}
