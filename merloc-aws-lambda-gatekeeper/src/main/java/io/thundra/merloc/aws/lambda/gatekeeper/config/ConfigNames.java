package io.thundra.merloc.aws.lambda.gatekeeper.config;

/**
 * @author serkan
 */
public interface ConfigNames extends io.thundra.merloc.aws.lambda.core.config.ConfigNames {

    String ENABLE = "merloc.enable";
    String BROKER_URL_CONFIG_NAME = "merloc.broker.url";
    String BROKER_CONNECTION_NAME_CONFIG_NAME = "merloc.broker.connection.name";
    String BROKER_REQUEST_WAIT_MARGIN_CONFIG_NAME = "merloc.broker.request.wait.margin";
    String API_KEY_CONFIG_NAME = "merloc.apikey";
    String CLIENT_ACCESS_INTERVAL_ON_FAILURE = "merloc.client.access.interval.on.failure";

}
