package io.thundra.merloc.aws.lambda.runtime.embedded.handler.ws;

import io.thundra.merloc.aws.lambda.runtime.embedded.exception.ErrorCoded;
import io.thundra.merloc.aws.lambda.runtime.embedded.exception.HandlerExecutionException;
import io.thundra.merloc.broker.client.BrokerConstants;
import io.thundra.merloc.broker.client.BrokerCredentials;
import io.thundra.merloc.broker.client.BrokerMessageCallback;
import io.thundra.merloc.broker.client.Error;
import io.thundra.merloc.aws.lambda.runtime.embedded.InvocationExecutor;
import io.thundra.merloc.aws.lambda.runtime.embedded.handler.InvocationHandler;
import io.thundra.merloc.broker.client.BrokerClient;
import io.thundra.merloc.broker.client.BrokerClientFactory;
import io.thundra.merloc.broker.client.BrokerMessage;
import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author serkan
 */
public class WebSocketInvocationHandler implements InvocationHandler {

    private static final String BROKER_HOST_CONFIG_NAME =
            "merloc.broker.host";
    private static final String BROKER_CONNECTION_NAME_CONFIG_NAME =
            "merloc.broker.connection.name";
    private static final int BROKER_NORMAL_CLOSE_CODE = 1000;
    private static final String BROKER_NORMAL_CLOSE_REASON = "Bye";

    private static final String AWS_LAMBDA_REGION_ATTRIBUTE_NAME = "region";
    private static final String AWS_LAMBDA_REQUEST_ID_ATTRIBUTE_NAME = "requestId";
    private static final String AWS_LAMBDA_HANDLER_ATTRIBUTE_NAME = "handler";
    private static final String AWS_LAMBDA_FUNCTION_ARN_ATTRIBUTE_NAME = "functionArn";
    private static final String AWS_LAMBDA_FUNCTION_NAME_ATTRIBUTE_NAME = "functionName";
    private static final String AWS_LAMBDA_FUNCTION_VERSION_ATTRIBUTE_NAME = "functionVersion";
    private static final String AWS_LAMBDA_RUNTIME_ATTRIBUTE_NAME = "runtime";
    private static final String AWS_LAMBDA_TIMEOUT_ATTRIBUTE_NAME = "timeout";
    private static final String AWS_LAMBDA_MEMORY_SIZE_ATTRIBUTE_NAME = "memorySize";
    private static final String AWS_LAMBDA_LOG_GROUP_NAME_ATTRIBUTE_NAME = "logGroupName";
    private static final String AWS_LAMBDA_LOG_STREAM_NAME_ATTRIBUTE_NAME = "logStreamName";
    private static final String AWS_LAMBDA_ENV_VARS_ATTRIBUTE_NAME = "envVars";
    private static final String AWS_LAMBDA_CLIENT_CONTEXT_ATTRIBUTE_NAME = "clientContext";
    private static final String AWS_LAMBDA_COGNITO_IDENTITY_ATTRIBUTE_NAME = "cognitoIdentity";
    private static final String AWS_LAMBDA_REQUEST_ATTRIBUTE_NAME = "request";

    private final InvocationExecutor invocationExecutor;
    private BrokerClient brokerClient;

    public WebSocketInvocationHandler(InvocationExecutor invocationExecutor) {
        this.invocationExecutor = invocationExecutor;
    }

    private static String getBrokerHost() {
        return ConfigManager.getConfig(BROKER_HOST_CONFIG_NAME);
    }

    private static String getBrokerConnectionName() {
        return ConfigManager.getConfig(
                BROKER_CONNECTION_NAME_CONFIG_NAME,
                BrokerConstants.DEFAULT_CLIENT_BROKER_CONNECTION_NAME);
    }

    @Override
    public void start() throws IOException {
        String host = getBrokerHost();
        String connectionName = getBrokerConnectionName();

        if (StringUtils.isNullOrEmpty(host)) {
            throw new IllegalArgumentException("Broker host is not configured");
        }

        if (StringUtils.isNullOrEmpty(connectionName)) {
            throw new IllegalArgumentException("Connection name is not configured");
        }

        BrokerCredentials credentials =
                new BrokerCredentials().
                        withConnectionName(BrokerConstants.CLIENT_CONNECTION_NAME_PREFIX + connectionName);

        CompletableFuture connectedFuture = new CompletableFuture();
        connectedFuture.whenComplete((val, error) -> {
            if (error == null) {
                StdLogger.debug(String.format("Connected to broker at %s", host));
            } else {
                StdLogger.error(String.format("Unable to connect to broker at %s", host), (Throwable) error);
            }
        });
        CompletableFuture closedFuture = new CompletableFuture();
        closedFuture.whenComplete((val, error) -> {
            if (error == null) {
                StdLogger.debug(String.format("Closed connection to broker at %s", host));
            } else {
                StdLogger.error(String.format("Unable to close connection to broker at %s", host), (Throwable) error);
            }
        });
        try {
            brokerClient =
                    BrokerClientFactory.createWebSocketClient(
                            host, credentials,
                            new BrokerMessageHandler(), connectedFuture, closedFuture);
            brokerClient.waitUntilConnected();
        } catch (Exception e) {
            throw new IOException("Unable to connect to broker");
        }
    }

    @Override
    public void stop() throws IOException {
        if (brokerClient != null) {
            brokerClient.sendCloseMessage(BROKER_NORMAL_CLOSE_CODE, BROKER_NORMAL_CLOSE_REASON);
            brokerClient.waitUntilClosed();
        }

        invocationExecutor.close();
    }

    private class BrokerMessageHandler implements BrokerMessageCallback {

        @Override
        public void onMessage(BrokerClient brokerClient, BrokerMessage message) {
            handleMessage(brokerClient, message);
        }

        private void sendPingResponse(BrokerClient brokerClient, String functionName,
                                      BrokerMessage brokerResponseMessage) {
            brokerResponseMessage.setType(BrokerConstants.CLIENT_PONG_MESSAGE_TYPE);

            try {
                brokerClient.send(brokerResponseMessage);
            } catch (Throwable err) {
                StdLogger.error(
                        String.format(
                                "Failed sending pong for invocation of function %s",
                                functionName),
                        err);
            }
        }

        private void sendClientResponse(BrokerClient brokerClient, String functionName,
                                        BrokerMessage brokerResponseMessage, String response) {
            brokerResponseMessage.
                    withType(BrokerConstants.CLIENT_RESPONSE_MESSAGE_TYPE).
                    withDataAttribute("response", response);

            try {
                brokerClient.send(brokerResponseMessage);
            } catch (Throwable err) {
                StdLogger.error(
                        String.format(
                                "Failed sending response for invocation of function %s",
                                functionName),
                        err);
            }
        }

        private void sendErrorResponse(BrokerClient brokerClient, String functionName,
                                       BrokerMessage brokerResponseMessage, Throwable err) {
            Throwable effectiveError = err;
            boolean handlerExecutionException = false;
            Integer errorCode = null;

            if (err instanceof HandlerExecutionException) {
                effectiveError = err.getCause();
                handlerExecutionException = true;
            }

            if (effectiveError instanceof ErrorCoded) {
                errorCode = ((ErrorCoded) err).code();
            }

            Error error =
                    new Error().
                            withType(effectiveError.getClass().getName()).
                            withMessage(effectiveError.getMessage()).
                            withStackTrace(extractStackTrace(effectiveError));

            if (errorCode != null) {
                error = error.withCode(errorCode);
            }
            if (!handlerExecutionException) {
                error = error.withInternal(true);
            }

            brokerResponseMessage.
                    withType(BrokerConstants.CLIENT_ERROR_MESSAGE_TYPE).
                    withError(error);

            try {
                brokerClient.send(brokerResponseMessage);
            } catch (Throwable t) {
                StdLogger.error(
                        String.format(
                                "Failed sending error response for invocation of function %s",
                                functionName),
                        t);
            }
        }

        private String[] extractStackTrace(Throwable error) {
            StackTraceElement[] stackTraceElements = error.getStackTrace();
            String[] stackTrace = new String[stackTraceElements.length];
            for (int i = 0; i < stackTraceElements.length; i++) {
                StackTraceElement ste = stackTraceElements[i];
                stackTrace[i] = ExceptionUtils.serializeStackTraceElement(ste);
            }
            return stackTrace;
        }

        private void handlePingRequest(BrokerClient brokerClient,
                                       BrokerMessage brokerRequestMessage,
                                       BrokerMessage brokerResponseMessage) {
            sendPingResponse(brokerClient, null, brokerResponseMessage);
        }

        private void handleClientRequest(BrokerClient brokerClient,
                                         BrokerMessage brokerRequestMessage,
                                         BrokerMessage brokerResponseMessage) {
            String request =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_REQUEST_ATTRIBUTE_NAME);
            String region =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_REGION_ATTRIBUTE_NAME);
            String requestId =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_REQUEST_ID_ATTRIBUTE_NAME);
            String handler =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_HANDLER_ATTRIBUTE_NAME);
            String functionArn =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_FUNCTION_ARN_ATTRIBUTE_NAME);
            String functionName =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_FUNCTION_NAME_ATTRIBUTE_NAME);
            String functionVersion =
                    brokerRequestMessage.getDataAttribute(
                            AWS_LAMBDA_FUNCTION_VERSION_ATTRIBUTE_NAME, InvocationExecutor.DEFAULT_VERSION);
            String runtime =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_RUNTIME_ATTRIBUTE_NAME);
            int timeout =
                    brokerRequestMessage.getDataAttribute(
                            AWS_LAMBDA_TIMEOUT_ATTRIBUTE_NAME, InvocationExecutor.DEFAULT_TIMEOUT);
            int memorySize =
                    brokerRequestMessage.getDataAttribute(
                            AWS_LAMBDA_MEMORY_SIZE_ATTRIBUTE_NAME, InvocationExecutor.DEFAULT_MEMORY_SIZE);
            String logGroupName =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_LOG_GROUP_NAME_ATTRIBUTE_NAME);
            String logStreamName =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_LOG_STREAM_NAME_ATTRIBUTE_NAME);
            Map<String, String> envVars =
                    brokerRequestMessage.getDataAttribute(
                            AWS_LAMBDA_ENV_VARS_ATTRIBUTE_NAME, Collections.EMPTY_MAP);
            String clientContext =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_CLIENT_CONTEXT_ATTRIBUTE_NAME);
            String cognitoIdentity =
                    brokerRequestMessage.getDataAttribute(AWS_LAMBDA_COGNITO_IDENTITY_ATTRIBUTE_NAME);

            try {
                String response = invocationExecutor.execute(
                        request, region, requestId, handler,
                        functionArn, functionName, functionVersion,
                        runtime, timeout, memorySize,
                        logGroupName, logStreamName,
                        envVars, clientContext, cognitoIdentity,
                        InvocationExecutor.DEFAULT_LAST_MODIFIED);

                sendClientResponse(brokerClient, functionName, brokerResponseMessage, response);
            } catch (Throwable error) {
                sendErrorResponse(brokerClient, functionName, brokerResponseMessage, error);
            }
        }

        private void handleClientConnectionOverrideMessage(BrokerClient brokerClient,
                                                           BrokerMessage brokerRequestMessage,
                                                           BrokerMessage brokerResponseMessage) {
            StdLogger.warn(String.format(
                    "Your client connection (name=%s) has been overridden by another connection",
                    brokerRequestMessage.getConnectionName()));
        }

        private void handleBrokerErrorMessage(BrokerClient brokerClient,
                                              BrokerMessage brokerRequestMessage,
                                              BrokerMessage brokerResponseMessage) {
            StdLogger.error(String.format(
                    "Broker sent error message to your client connection (name=%s): type=%s, message=%s",
                    brokerRequestMessage.getConnectionName(),
                    (brokerRequestMessage.getError() != null ? brokerRequestMessage.getError().getType() : ""),
                    (brokerRequestMessage.getError() != null ? brokerRequestMessage.getError().getMessage() : "")));
        }

        private void handleMessage(BrokerClient brokerClient, BrokerMessage brokerRequestMessage) {
            try {
                BrokerMessage brokerResponseMessage =
                        new BrokerMessage().
                                withId(UUID.randomUUID().toString()).
                                withResponseOf(brokerRequestMessage.getId()).
                                withConnectionName(brokerRequestMessage.getConnectionName()).
                                withSourceConnectionId(brokerRequestMessage.getTargetConnectionId()).
                                withSourceConnectionType(BrokerConstants.CLIENT_CONNECTION_TYPE).
                                withTargetConnectionId(brokerRequestMessage.getSourceConnectionId()).
                                withTargetConnectionType(brokerRequestMessage.getSourceConnectionType());

                try {
                    if (BrokerConstants.CLIENT_PING_MESSAGE_TYPE.
                            equalsIgnoreCase(brokerRequestMessage.getType())) {
                        handlePingRequest(brokerClient, brokerRequestMessage, brokerResponseMessage);
                    } else if (BrokerConstants.CLIENT_REQUEST_MESSAGE_TYPE.
                            equalsIgnoreCase(brokerRequestMessage.getType())) {
                        handleClientRequest(brokerClient, brokerRequestMessage, brokerResponseMessage);
                    } else if (BrokerConstants.CLIENT_CONNECTION_OVERRIDE_MESSAGE_TYPE.
                            equalsIgnoreCase(brokerRequestMessage.getType())) {
                        handleClientConnectionOverrideMessage(
                                brokerClient, brokerRequestMessage, brokerResponseMessage);
                    } else if (BrokerConstants.BROKER_ERROR_MESSAGE_TYPE.
                            equalsIgnoreCase(brokerRequestMessage.getType())) {
                        handleBrokerErrorMessage(
                                brokerClient, brokerRequestMessage, brokerResponseMessage);
                    } else {
                        throw new UnsupportedOperationException(
                                String.format("Unsupported message type: %s", brokerRequestMessage.getType()));
                    }
                } catch (Throwable error) {
                    StdLogger.error("Error occurred while handling message", error);
                    try {
                        sendErrorResponse(brokerClient, null, brokerResponseMessage, error);
                    } catch (Throwable error2) {
                        StdLogger.error("Unable to send client response", error2);
                    }
                }
            } catch (Throwable error) {
                StdLogger.error("Unable to handle message", error);
            }
        }
    }

}
