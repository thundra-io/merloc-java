package io.thundra.merloc.aws.lambda.gatekeeper.config;

/**
 * @author serkan
 */
public interface ConfigNames extends io.thundra.merloc.aws.lambda.core.config.ConfigNames {

    String ENABLE = "merloc.enable";
    String LAMBDA_RUNTIME_URL = "merloc.aws.lambda.runtime.url";
    String LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE = "merloc.aws.lambda.runtime.ping.interval.on.failure";

}
