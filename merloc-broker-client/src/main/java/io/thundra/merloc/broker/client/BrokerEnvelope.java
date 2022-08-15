package io.thundra.merloc.broker.client;

import java.util.Objects;

/**
 * @author serkan
 */
public class BrokerEnvelope {

    private String id;
    private String responseOf;
    private String connectionName;
    private String sourceConnectionId;
    private String sourceConnectionType;
    private String targetConnectionId;
    private String targetConnectionType;
    private String type;
    private String payload;
    private boolean fragmented;
    private int fragmentNo = -1;
    private int fragmentCount = -1;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BrokerEnvelope withId(String id) {
        this.id = id;
        return this;
    }

    public String getResponseOf() {
        return responseOf;
    }

    public void setResponseOf(String responseOf) {
        this.responseOf = responseOf;
    }

    public BrokerEnvelope withResponseOf(String responseOf) {
        this.responseOf = responseOf;
        return this;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public BrokerEnvelope withPayload(String payload) {
        this.payload = payload;
        return this;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public BrokerEnvelope withConnectionName(String connectionName) {
        this.connectionName = connectionName;
        return this;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public BrokerEnvelope withSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
        return this;
    }

    public String getSourceConnectionType() {
        return sourceConnectionType;
    }

    public void setSourceConnectionType(String sourceConnectionType) {
        this.sourceConnectionType = sourceConnectionType;
    }

    public BrokerEnvelope withSourceConnectionType(String sourceConnectionType) {
        this.sourceConnectionType = sourceConnectionType;
        return this;
    }

    public String getTargetConnectionId() {
        return targetConnectionId;
    }

    public void setTargetConnectionId(String targetConnectionId) {
        this.targetConnectionId = targetConnectionId;
    }

    public BrokerEnvelope withTargetConnectionId(String targetConnectionId) {
        this.targetConnectionId = targetConnectionId;
        return this;
    }

    public String getTargetConnectionType() {
        return targetConnectionType;
    }

    public void setTargetConnectionType(String targetConnectionType) {
        this.targetConnectionType = targetConnectionType;
    }

    public BrokerEnvelope withTargetConnectionType(String targetConnectionType) {
        this.targetConnectionType = targetConnectionType;
        return this;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BrokerEnvelope withType(String type) {
        this.type = type;
        return this;
    }

    public boolean isFragmented() {
        return fragmented;
    }

    public void setFragmented(boolean fragmented) {
        this.fragmented = fragmented;
    }

    public BrokerEnvelope withFragmented(boolean fragmented) {
        this.fragmented = fragmented;
        return this;
    }

    public int getFragmentNo() {
        return fragmentNo;
    }

    public void setFragmentNo(int fragmentNo) {
        this.fragmentNo = fragmentNo;
    }

    public BrokerEnvelope withFragmentNo(int fragmentNo) {
        this.fragmentNo = fragmentNo;
        return this;
    }

    public int getFragmentCount() {
        return fragmentCount;
    }

    public void setFragmentCount(int fragmentCount) {
        this.fragmentCount = fragmentCount;
    }

    public BrokerEnvelope withFragmentCount(int fragmentCount) {
        this.fragmentCount = fragmentCount;
        return this;
    }

    @Override
    public String toString() {
        return "BrokerEnvelope{" +
                "id='" + id + '\'' +
                ", responseOf='" + responseOf + '\'' +
                ", connectionName='" + connectionName + '\'' +
                ", sourceConnectionId='" + sourceConnectionId + '\'' +
                ", sourceConnectionType='" + sourceConnectionType + '\'' +
                ", targetConnectionId='" + targetConnectionId + '\'' +
                ", targetConnectionType='" + targetConnectionType + '\'' +
                ", type='" + type + '\'' +
                ", payload='" + payload + '\'' +
                ", fragmented=" + fragmented +
                ", fragmentNo=" + fragmentNo +
                ", fragmentCount=" + fragmentCount +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fragmentNo);
    }

}
