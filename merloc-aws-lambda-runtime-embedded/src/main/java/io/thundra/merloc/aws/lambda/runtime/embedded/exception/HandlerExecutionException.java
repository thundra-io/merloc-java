package io.thundra.merloc.aws.lambda.runtime.embedded.exception;

/**
 * @author serkan
 */
public class HandlerExecutionException extends Exception {

    public HandlerExecutionException() {
    }

    public HandlerExecutionException(String message) {
        super(message);
    }

    public HandlerExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public HandlerExecutionException(Throwable cause) {
        super(cause);
    }

}
