package io.thundra.merloc.broker.client;

/**
 * @author serkan
 */
public interface BrokerConstants {

    String BROKER_CONNECTION_TYPE = "broker";
    String CLIENT_CONNECTION_TYPE = "client";
    String GATEKEEPER_CONNECTION_TYPE = "gatekeeper";

    String CONNECTION_TYPE_SEPARATOR = "::";
    String CONNECTION_API_KEY_SEPARATOR = "##";
    String CLIENT_CONNECTION_NAME_PREFIX = "client" + CONNECTION_TYPE_SEPARATOR;
    String GATEKEEPER_CONNECTION_NAME_PREFIX = "gatekeeper" + CONNECTION_TYPE_SEPARATOR;

    String DEFAULT_CLIENT_BROKER_CONNECTION_NAME = "default";

    String CLIENT_PING_MESSAGE_TYPE = "client.ping";
    String CLIENT_PONG_MESSAGE_TYPE = "client.pong";
    String CLIENT_REQUEST_MESSAGE_TYPE = "client.request";
    String CLIENT_RESPONSE_MESSAGE_TYPE = "client.response";
    String CLIENT_DISCONNECT_MESSAGE_TYPE = "client.disconnect";
    String CLIENT_ERROR_MESSAGE_TYPE = "client.error";
    String CLIENT_CONNECTION_OVERRIDE_MESSAGE_TYPE = "client.connectionOverride";
    String BROKER_ERROR_MESSAGE_TYPE = "broker.error";

}
