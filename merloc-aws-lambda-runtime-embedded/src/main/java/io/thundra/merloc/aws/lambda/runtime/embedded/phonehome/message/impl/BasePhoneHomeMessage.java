package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.impl;

import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.PhoneHomeMessage;
import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.PhoneHomeMessageBuilder;

/**
 * @author serkan
 */
public abstract class BasePhoneHomeMessage implements PhoneHomeMessage {

    protected final String type;
    protected final String machineHash;
    protected final String osName;
    protected final int jvmVersion;

    protected BasePhoneHomeMessage(String type, String machineHash,
                                   String osName, int jvmVersion) {
        this.type = type;
        this.machineHash = machineHash;
        this.osName = osName;
        this.jvmVersion = jvmVersion;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getMachineHash() {
        return machineHash;
    }

    @Override
    public String getOsName() {
        return osName;
    }

    @Override
    public int getJvmVersion() {
        return jvmVersion;
    }

    protected static abstract class Builder
            <M extends PhoneHomeMessage, B extends PhoneHomeMessageBuilder<M>>
            implements PhoneHomeMessageBuilder<M> {

        protected String type;
        protected String machineHash;
        protected String osName;
        protected int jvmVersion;

        protected Builder() {
        }

        public B type(String type) {
            this.type = type;
            return (B) this;
        }

        public B machineHash(String machineHash) {
            this.machineHash = machineHash;
            return (B) this;
        }

        public B osName(String osName) {
            this.osName = osName;
            return (B) this;
        }

        public B jvmVersion(int jvmVersion) {
            this.jvmVersion = jvmVersion;
            return (B) this;
        }

    }

}
