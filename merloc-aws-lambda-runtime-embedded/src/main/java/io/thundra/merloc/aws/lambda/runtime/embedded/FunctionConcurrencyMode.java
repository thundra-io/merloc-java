package io.thundra.merloc.aws.lambda.runtime.embedded;

/**
 * @author serkan
 */
public enum FunctionConcurrencyMode {

    REJECT,
    WAIT;

    public static FunctionConcurrencyMode of(String modeValue) {
        for (FunctionConcurrencyMode mode : FunctionConcurrencyMode.values()) {
            if (mode.name().equalsIgnoreCase(modeValue)) {
                return mode;
            }
        }
        return null;
    }

}
