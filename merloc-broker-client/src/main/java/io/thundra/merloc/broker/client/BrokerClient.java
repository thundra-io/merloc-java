package io.thundra.merloc.broker.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author serkan
 */
public interface BrokerClient {

    boolean isConnected();
    boolean waitUntilConnected();
    boolean waitUntilConnected(long timeout, TimeUnit unit);

    void send(BrokerMessage brokerMessage) throws IOException;
    BrokerMessage sendAndGetResponse(BrokerMessage brokerMessage,
                                     long timeout, TimeUnit timeUnit) throws IOException;

    void sendCloseMessage(int code, String reason) throws IOException;
    void close();

    boolean isClosed();
    boolean waitUntilClosed();
    boolean waitUntilClosed(long timeout, TimeUnit unit);

    void destroy();

}
