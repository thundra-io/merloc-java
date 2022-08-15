package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public class BrokerCredentials {

    private String connectionName;

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public BrokerCredentials withConnectionName(String connectionName) {
        this.connectionName = connectionName;
        return this;
    }

}
