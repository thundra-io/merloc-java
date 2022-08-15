package io.thundra.merloc.aws.lambda.runtime.embedded.exception;

/**
 * @author serkan
 */
public class InvalidRequestException extends Exception {

    public InvalidRequestException(String message) {
        super(message);
    }

}
