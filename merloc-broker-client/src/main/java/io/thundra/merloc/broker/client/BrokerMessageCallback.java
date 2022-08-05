package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public interface BrokerMessageCallback {

    void onMessage(BrokerClient brokerClient, BrokerMessage brokerMessage);

}
