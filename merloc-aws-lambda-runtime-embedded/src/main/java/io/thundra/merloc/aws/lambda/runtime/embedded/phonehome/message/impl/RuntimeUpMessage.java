package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.impl;

/**
 * @author serkan
 */
public class RuntimeUpMessage extends BasePhoneHomeMessage {

    public static final String TYPE = "runtime.up";

    private final long startTime;

    private RuntimeUpMessage(String machineHash, String osName, int jvmVersion,
                             long startTime) {
        super(TYPE, machineHash, osName, jvmVersion);
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BasePhoneHomeMessage.Builder<RuntimeUpMessage, Builder> {

        private long startTime;

        private Builder() {
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        @Override
        public RuntimeUpMessage build() {
            return new RuntimeUpMessage(machineHash, osName, jvmVersion, startTime);
        }

    }

}
