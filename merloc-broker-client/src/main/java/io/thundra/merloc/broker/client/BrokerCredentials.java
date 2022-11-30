package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public class BrokerCredentials {

    private String connectionName;
    private String apiKey;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public BrokerCredentials withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

}
