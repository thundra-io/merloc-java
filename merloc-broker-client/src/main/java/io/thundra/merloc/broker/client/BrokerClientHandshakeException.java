package io.thundra.merloc.broker.client;

import java.io.IOException;

/**
 * @author serkan
 */
public class BrokerClientHandshakeException extends IOException {

    public BrokerClientHandshakeException() {
        super();
    }

    public BrokerClientHandshakeException(String message) {
        super(message);
    }

    public BrokerClientHandshakeException(String message, Throwable cause) {
        super(message, cause);
    }

}
