package io.thundra.merloc.aws.lambda.runtime.embedded;

/**
 * @author serkan
 */
public enum LambdaRuntimeConcurrencyMode {

    REJECT,
    WAIT,
    PER_FUNCTION;

    public static LambdaRuntimeConcurrencyMode of(String modeValue) {
        for (LambdaRuntimeConcurrencyMode mode : LambdaRuntimeConcurrencyMode.values()) {
            if (mode.name().equalsIgnoreCase(modeValue)) {
                return mode;
            }
        }
        return null;
    }

}
