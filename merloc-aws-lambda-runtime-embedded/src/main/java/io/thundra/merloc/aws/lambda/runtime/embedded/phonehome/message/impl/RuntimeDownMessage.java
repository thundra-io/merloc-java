package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.impl;

/**
 * @author serkan
 */
public class RuntimeDownMessage extends BasePhoneHomeMessage {

    public static final String TYPE = "runtime.down";

    private final long startTime;
    private final long finishTime;
    private final long duration;

    private RuntimeDownMessage(String machineHash, String osName, int jvmVersion,
                               long startTime, long finishTime, long duration) {
        super(TYPE, machineHash, osName, jvmVersion);
        this.startTime = startTime;
        this.finishTime = finishTime;
        this.duration = duration;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getFinishTime() {
        return finishTime;
    }

    public long getDuration() {
        return duration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BasePhoneHomeMessage.Builder<RuntimeDownMessage, Builder> {

        private long startTime;
        private long finishTime;

        private Builder() {
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder finishTime(long finishTime) {
            this.finishTime = finishTime;
            return this;
        }

        @Override
        public RuntimeDownMessage build() {
            return new RuntimeDownMessage(
                    machineHash, osName, jvmVersion,
                    startTime, finishTime, finishTime > 0 ? (finishTime - startTime) : -1);
        }

    }

}
