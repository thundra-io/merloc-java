package io.thundra.merloc.aws.lambda.runtime.embedded.exception;

/**
 * @author serkan
 */
public class RuntimeInUseException extends Exception {

    public RuntimeInUseException(String message) {
        super(message);
    }

}
