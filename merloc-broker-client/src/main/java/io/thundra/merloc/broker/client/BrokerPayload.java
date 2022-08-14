package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public class BrokerPayload {

    private Data data;
    private Error error;

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public BrokerPayload withData(Data data) {
        this.data = data;
        return this;
    }

    public BrokerPayload withDataAttribute(String name, Object value) {
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

    public BrokerPayload withError(Error error) {
        this.error = error;
        return this;
    }

    @Override
    public String toString() {
        return "BrokerPayload{" +
                "data=" + data +
                ", error=" + error +
                '}';
    }

}
