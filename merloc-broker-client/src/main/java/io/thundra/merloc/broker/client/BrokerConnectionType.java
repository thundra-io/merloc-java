package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public enum BrokerConnectionType {

    CLIENT(BrokerConstants.CLIENT_CONNECTION_NAME_PREFIX),
    GATEKEEPER(BrokerConstants.GATEKEEPER_CONNECTION_NAME_PREFIX);

    private final String connectionNamePrefix;

    BrokerConnectionType(String connectionNamePrefix) {
        this.connectionNamePrefix = connectionNamePrefix;
    }

    public String getConnectionNamePrefix() {
        return connectionNamePrefix;
    }

}
