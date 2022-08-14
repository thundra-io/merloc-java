package io.thundra.merloc.broker.client;

import io.thundra.merloc.broker.client.impl.OkHttpWebSocketBrokerClient;

import java.util.concurrent.CompletableFuture;

/**
 * @author serkan
 */
public final class BrokerClientFactory {

    private BrokerClientFactory() {
    }

    public static BrokerClient createWebSocketClient(String host,
                                                     BrokerCredentials brokerCredentials,
                                                     BrokerMessageCallback brokerMessageCallback,
                                                     CompletableFuture connectedFuture,
                                                     CompletableFuture closedFuture) throws Exception {
        return new OkHttpWebSocketBrokerClient(
                host, brokerCredentials,
                brokerMessageCallback, null, connectedFuture, closedFuture);
    }

}
