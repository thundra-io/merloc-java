package io.thundra.merloc.aws.lambda.runtime.embedded.domain;

/**
 * @author serkan
 */
public class ErrorResponse {

    private final String errorType;
    private final String errorMessage;
    private final String[] stackTrace;

    public ErrorResponse(String errorType, String errorMessage, String[] stackTrace) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String[] getStackTrace() {
        return stackTrace;
    }

}
