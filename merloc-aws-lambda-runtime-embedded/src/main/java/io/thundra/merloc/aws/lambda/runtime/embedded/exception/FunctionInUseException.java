package io.thundra.merloc.aws.lambda.runtime.embedded.exception;

/**
 * @author serkan
 */
public class FunctionInUseException extends Exception {

    public FunctionInUseException(String message) {
        super(message);
    }

}
