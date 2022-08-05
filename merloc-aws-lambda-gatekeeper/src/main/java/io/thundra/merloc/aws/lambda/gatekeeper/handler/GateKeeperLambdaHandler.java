package io.thundra.merloc.aws.lambda.gatekeeper.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import io.thundra.merloc.broker.client.BrokerClient;
import io.thundra.merloc.broker.client.BrokerClientFactory;
import io.thundra.merloc.broker.client.BrokerConstants;
import io.thundra.merloc.broker.client.BrokerCredentials;
import io.thundra.merloc.broker.client.BrokerMessage;
import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.aws.lambda.core.handler.HandlerHelper;
import io.thundra.merloc.aws.lambda.core.handler.WrapperLambdaHandler;
import io.thundra.merloc.aws.lambda.core.utils.LambdaUtils;
import io.thundra.merloc.aws.lambda.gatekeeper.config.ConfigNames;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ClassUtils;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.IOUtils;
import io.thundra.merloc.common.utils.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author serkan
 */
public class GateKeeperLambdaHandler extends WrapperLambdaHandler {

    private static final String MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME = "MERLOC_AWS_LAMBDA_HANDLER";
    private static final String AWS_REGION_ENV_VAR_NAME = "AWS_REGION";
    private static final String AWS_LAMBDA_FUNCTION_NAME_ENV_VAR_NAME = "AWS_LAMBDA_FUNCTION_NAME";
    private static final String AWS_EXECUTION_ENV_ENV_VAR_NAME = "AWS_EXECUTION_ENV";
    private static final String AWS_EXECUTION_ENV_PREFIX = "AWS_Lambda_";

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
    
    private static final boolean ENABLE =
            ConfigManager.getBooleanConfig(ConfigNames.ENABLE, true);
    private static final String BROKER_HOST =
            ConfigManager.getConfig(ConfigNames.BROKER_HOST_CONFIG_NAME);
    private static final String BROKER_CONNECTION_NAME =
            ConfigManager.getConfig(
                    ConfigNames.BROKER_CONNECTION_NAME_CONFIG_NAME,
                    LambdaUtils.getEnvVar(AWS_LAMBDA_FUNCTION_NAME_ENV_VAR_NAME));
    private static final int CLIENT_ACCESS_INTERVAL_ON_FAILURE =
            ConfigManager.getIntegerConfig(
                    ConfigNames.CLIENT_ACCESS_INTERVAL_ON_FAILURE, 0);

    private final PojoSerializer<ClientContext> clientContextSerializer =
            HandlerHelper.getSerializer(ClientContext.class);
    private final PojoSerializer<CognitoIdentity> cognitoIdentitySerializer =
            HandlerHelper.getSerializer(CognitoIdentity.class);

    private long latestClientAccessFailTime = -1;

    @Override
    protected Future<RequestStreamHandler> getProxyLambdaHandler(Supplier<RequestStreamHandler> handlerSupplier) {
        return CompletableFuture.supplyAsync(handlerSupplier);
    }

    private static BrokerClient createBrokerClient() {
        StdLogger.debug(String.format(
                "Creating broker client to %s with connection name %s",
                BROKER_HOST,
                BROKER_CONNECTION_NAME));
        try {
            return BrokerClientFactory.createWebSocketClient(
                    BROKER_HOST,
                    new BrokerCredentials().
                            withConnectionName(BROKER_CONNECTION_NAME),
                    null, null, null);
        } catch (Exception e) {
            StdLogger.error("Unable to create broker client", e);
            return null;
        }
    }

    private BrokerMessage createClientRequest(Context context, String requestData) throws IOException {
        BrokerMessage.Data data = new BrokerMessage.Data();

        data.put(AWS_LAMBDA_REGION_ATTRIBUTE_NAME, LambdaUtils.getEnvVar(AWS_REGION_ENV_VAR_NAME));
        data.put(AWS_LAMBDA_REQUEST_ID_ATTRIBUTE_NAME, context.getAwsRequestId());
        data.put(AWS_LAMBDA_HANDLER_ATTRIBUTE_NAME, LambdaUtils.getEnvVar(MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME));
        data.put(AWS_LAMBDA_FUNCTION_ARN_ATTRIBUTE_NAME, context.getInvokedFunctionArn());
        data.put(AWS_LAMBDA_FUNCTION_NAME_ATTRIBUTE_NAME, context.getFunctionName());
        data.put(AWS_LAMBDA_FUNCTION_VERSION_ATTRIBUTE_NAME, context.getFunctionVersion());
        data.put(AWS_LAMBDA_RUNTIME_ATTRIBUTE_NAME,
                LambdaUtils.getEnvVar(AWS_EXECUTION_ENV_ENV_VAR_NAME).substring(AWS_EXECUTION_ENV_PREFIX.length()));
        data.put(AWS_LAMBDA_TIMEOUT_ATTRIBUTE_NAME, context.getRemainingTimeInMillis());
        data.put(AWS_LAMBDA_MEMORY_SIZE_ATTRIBUTE_NAME, context.getMemoryLimitInMB());
        data.put(AWS_LAMBDA_LOG_GROUP_NAME_ATTRIBUTE_NAME, context.getLogGroupName());
        data.put(AWS_LAMBDA_LOG_STREAM_NAME_ATTRIBUTE_NAME, context.getLogStreamName());

        data.put(AWS_LAMBDA_ENV_VARS_ATTRIBUTE_NAME, LambdaUtils.getEnvVars());

        ClientContext clientContext = context.getClientContext();
        if (clientContext != null) {
            ByteArrayOutputStream clientContextOutputStream = new ByteArrayOutputStream();
            clientContextSerializer.toJson(clientContext, clientContextOutputStream);
            data.put(AWS_LAMBDA_CLIENT_CONTEXT_ATTRIBUTE_NAME, new String(clientContextOutputStream.toByteArray()));
        }

        CognitoIdentity cognitoIdentity = context.getIdentity();
        if (cognitoIdentity != null) {
            ByteArrayOutputStream cognitoIdentityOutputStream = new ByteArrayOutputStream();
            cognitoIdentitySerializer.toJson(cognitoIdentity, cognitoIdentityOutputStream);
            data.put(AWS_LAMBDA_COGNITO_IDENTITY_ATTRIBUTE_NAME, new String(cognitoIdentityOutputStream.toByteArray()));
        }

        data.put(AWS_LAMBDA_REQUEST_ATTRIBUTE_NAME, requestData);

        return new BrokerMessage().
                withId(UUID.randomUUID().toString()).
                withType(BrokerConstants.CLIENT_REQUEST_MESSAGE_TYPE).
                withConnectionName(BROKER_CONNECTION_NAME).
                withSourceConnectionType(BrokerConstants.GATEKEEPER_CONNECTION_TYPE).
                withTargetConnectionType(BrokerConstants.CLIENT_CONNECTION_TYPE).
                withData(data);
    }

    @Override
    protected boolean onRequest(InputStream requestStream, OutputStream responseStream, Context context) {
        boolean throwError = false;
        if (!ENABLE) {
            StdLogger.debug("MerLoc is disabled, so forwarding request to the actual handler");
            return true;
        }
        if (StringUtils.isNullOrEmpty(BROKER_HOST)) {
            StdLogger.debug("Broker host is empty so forwarding request to the actual handler");
            return true;
        }
        try {
            long currentTime = System.currentTimeMillis();
            if (CLIENT_ACCESS_INTERVAL_ON_FAILURE > 0 && latestClientAccessFailTime > 0) {
                long passedSecondsFromLatestClientAccessFail = (currentTime - latestClientAccessFailTime) / 1000;
                StdLogger.debug(String.format(
                        "%d seconds have passed since latest client access fail", passedSecondsFromLatestClientAccessFail));
                if (passedSecondsFromLatestClientAccessFail < CLIENT_ACCESS_INTERVAL_ON_FAILURE) {
                    StdLogger.debug(String.format(
                            "Forwarding request to the actual handler because not enough time (%d secs < %d secs) has passed " +
                                    "since latest client access fail",
                            passedSecondsFromLatestClientAccessFail, CLIENT_ACCESS_INTERVAL_ON_FAILURE));
                    return true;
                }
            }

            BrokerClient brokerClient = createBrokerClient();
            if (brokerClient == null) {
                StdLogger.debug("Broker client could not be created so forwarding request to the actual handler");
                return true;
            }

            try {
                boolean connected = brokerClient.waitUntilConnected(3, TimeUnit.SECONDS);
                if (!connected) {
                    StdLogger.debug("Could not connect to broker so forwarding request to the actual handler");
                    return true;
                }

                String requestData = IOUtils.readAllAsString(requestStream);

                if (StdLogger.DEBUG_ENABLED) {
                    StdLogger.debug(String.format(
                            "Forwarding request to client: %s", requestData));
                }

                BrokerMessage clientRequest = createClientRequest(context, requestData);
                BrokerMessage clientResponse =
                        brokerClient.sendAndGetResponse(
                                clientRequest,
                                context.getRemainingTimeInMillis(), TimeUnit.MILLISECONDS);
                if (clientResponse == null) {
                    // No response neither from client nor from broker.
                    // So update the latest client access fail time.
                    latestClientAccessFailTime = currentTime;
                    StdLogger.debug(String.format("Couldn't get response to client request"));
                    return true;
                }
                BrokerMessage.Error error = clientResponse.getError();
                if (error != null) {
                    if (BrokerConstants.BROKER_CONNECTION_TYPE.equals(clientResponse.getSourceConnectionType())) {
                        // Response is coming from broker, not client.
                        // So update the latest client access fail time.
                        latestClientAccessFailTime = currentTime;
                    }
                    if (error.isInternal()) {
                        StdLogger.debug(String.format("Internal client request error: %s", error.getMessage()));
                        return true;
                    } else {
                        StdLogger.debug(String.format("Client request error: %s", error.getMessage()));
                        Throwable clientRequestError = createClientRequestError(error);
                        throwError = true;
                        ExceptionUtils.sneakyThrow(clientRequestError);
                        return false;
                    }
                }

                String responseData = clientResponse.getDataAttribute("response");
                if (StdLogger.DEBUG_ENABLED) {
                    StdLogger.debug(String.format(
                            "Received response from client: %s", responseData));
                }

                responseStream.write(responseData.getBytes(StandardCharsets.UTF_8));

                return false;
            } catch (Throwable t) {
                // Unexpected error.
                // So update the latest client access fail time.
                latestClientAccessFailTime = currentTime;
                if (throwError) {
                    ExceptionUtils.sneakyThrow(t);
                }
                StdLogger.error("Client access failed", t);
            } finally {
                try {
                    brokerClient.close();
                    brokerClient.waitUntilClosed(3, TimeUnit.SECONDS);
                    brokerClient.destroy();
                } catch (Throwable t) {
                    StdLogger.error("Couldn't close broker client", t);
                }
            }
        } catch (Throwable t) {
            StdLogger.error("Client failed to handle request", t);
            if (throwError) {
                StdLogger.error(String.format(
                        "Throwing client error (type=%s, message=%s)", t.getClass().getName(), t.getMessage()),
                        t);
                ExceptionUtils.sneakyThrow(t);
            }
        }
        return true;
    }

    private <T> Constructor<T> getConstructorSafe(Class<T> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Throwable createClientRequestError(BrokerMessage.Error error) {
        String errorType = error.getType();
        String errorMessage = error.getMessage();
        if (StringUtils.hasValue(errorType)) {
            Class<? extends Throwable> errorClass =
                    ClassUtils.getClass(getClass().getClassLoader(), errorType);
            if (errorClass != null) {
                Constructor<? extends Throwable> ctor = getConstructorSafe(errorClass, String.class);
                if (ctor != null) {
                    try {
                        return ctor.newInstance(errorMessage);
                    } catch (Exception e) {
                        StdLogger.error(String.format(
                                "Unable to create client request error: %s", errorType), e);
                    }
                } else {
                    ctor = getConstructorSafe(errorClass, String.class, Throwable.class);
                    if (ctor != null) {
                        try {
                            return ctor.newInstance(errorMessage, null);
                        } catch (Exception e) {
                            StdLogger.error(String.format(
                                    "Unable to create client request error: %s", errorType), e);
                        }
                    }
                }
            }
        }
        return new RuntimeException(errorMessage);
    }

}
