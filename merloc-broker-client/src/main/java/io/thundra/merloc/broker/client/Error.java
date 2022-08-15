package io.thundra.merloc.broker.client;

import java.util.Arrays;

/**
 * @author serkan
 */
public class Error {

    private String type;
    private String message;
    private String stackTrace[];
    private Integer code;
    private boolean internal;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Error withType(String type) {
        this.type = type;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Error withMessage(String message) {
        this.message = message;
        return this;
    }

    public String[] getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String[] stackTrace) {
        this.stackTrace = stackTrace;
    }

    public Error withStackTrace(String[] stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Error withCode(Integer code) {
        this.code = code;
        return this;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public Error withInternal(boolean internal) {
        this.internal = internal;
        return this;
    }

    @Override
    public String toString() {
        return "Error{" +
                "type='" + type + '\'' +
                ", message='" + message + '\'' +
                ", stackTrace=" + Arrays.toString(stackTrace) +
                ", code=" + code +
                ", internal=" + internal +
                '}';
    }

}
