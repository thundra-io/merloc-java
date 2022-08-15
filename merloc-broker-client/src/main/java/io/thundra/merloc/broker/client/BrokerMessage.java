package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public class BrokerMessage {

    private String id;
    private String responseOf;
    private String connectionName;
    private String sourceConnectionId;
    private String sourceConnectionType;
    private String targetConnectionId;
    private String targetConnectionType;
    private String type;
    private Data data;
    private Error error;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BrokerMessage withId(String id) {
        this.id = id;
        return this;
    }

    public String getResponseOf() {
        return responseOf;
    }

    public void setResponseOf(String responseOf) {
        this.responseOf = responseOf;
    }

    public BrokerMessage withResponseOf(String responseOf) {
        this.responseOf = responseOf;
        return this;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public BrokerMessage withConnectionName(String connectionName) {
        this.connectionName = connectionName;
        return this;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public BrokerMessage withSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
        return this;
    }

    public String getSourceConnectionType() {
        return sourceConnectionType;
    }

    public void setSourceConnectionType(String sourceConnectionType) {
        this.sourceConnectionType = sourceConnectionType;
    }

    public BrokerMessage withSourceConnectionType(String sourceConnectionType) {
        this.sourceConnectionType = sourceConnectionType;
        return this;
    }

    public String getTargetConnectionId() {
        return targetConnectionId;
    }

    public void setTargetConnectionId(String targetConnectionId) {
        this.targetConnectionId = targetConnectionId;
    }

    public BrokerMessage withTargetConnectionId(String targetConnectionId) {
        this.targetConnectionId = targetConnectionId;
        return this;
    }

    public String getTargetConnectionType() {
        return targetConnectionType;
    }

    public void setTargetConnectionType(String targetConnectionType) {
        this.targetConnectionType = targetConnectionType;
    }

    public BrokerMessage withTargetConnectionType(String targetConnectionType) {
        this.targetConnectionType = targetConnectionType;
        return this;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BrokerMessage withType(String type) {
        this.type = type;
        return this;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public BrokerMessage withData(Data data) {
        this.data = data;
        return this;
    }

    public BrokerMessage withDataAttribute(String name, Object value) {
        if (data == null) {
            data = new Data();
        }
        data.put(name, value);
        return this;
    }

    public <T> T getDataAttribute(String name) {
        if (data == null) {
            return null;
        }
        return (T) data.get(name);
    }

    public <T> T getDataAttribute(String name, T defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        return (T) data.getOrDefault(name, defaultValue);
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public BrokerMessage withError(Error error) {
        this.error = error;
        return this;
    }

    @Override
    public String toString() {
        return "BrokerMessage{" +
                "connectionName='" + connectionName + '\'' +
                ", sourceConnectionId='" + sourceConnectionId + '\'' +
                ", sourceConnectionType='" + sourceConnectionType + '\'' +
                ", targetConnectionId='" + targetConnectionId + '\'' +
                ", targetConnectionType='" + targetConnectionType + '\'' +
                ", type='" + type + '\'' +
                ", data=" + data +
                ", error=" + error +
                '}';
    }

}
