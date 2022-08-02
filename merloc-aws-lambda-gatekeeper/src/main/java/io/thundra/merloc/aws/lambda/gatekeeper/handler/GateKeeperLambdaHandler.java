package io.thundra.merloc.aws.lambda.gatekeeper.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.thundra.merloc.aws.lambda.core.config.ConfigManager;
import io.thundra.merloc.aws.lambda.core.handler.HandlerHelper;
import io.thundra.merloc.aws.lambda.core.handler.WrapperLambdaHandler;
import io.thundra.merloc.aws.lambda.core.logger.StdLogger;
import io.thundra.merloc.aws.lambda.core.utils.ExceptionUtils;
import io.thundra.merloc.aws.lambda.core.utils.IOUtils;
import io.thundra.merloc.aws.lambda.core.utils.LambdaUtils;
import io.thundra.merloc.aws.lambda.core.utils.StringUtils;
import io.thundra.merloc.aws.lambda.gatekeeper.config.ConfigNames;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author serkan
 */
public class GateKeeperLambdaHandler extends WrapperLambdaHandler {

    private static final boolean ENABLE =
            ConfigManager.getBooleanConfig(ConfigNames.ENABLE, true);
    private static final String LAMBDA_RUNTIME_URL =
            ConfigManager.getConfig(ConfigNames.LAMBDA_RUNTIME_URL);
    private static final int LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE =
            ConfigManager.getIntegerConfig(ConfigNames.LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE, 0);

    private static final String AWS_REGION_HEADER_NAME = "X-Amz-Region";
    private static final String AWS_REQUEST_ID_HEADER_NAME = "X-Amz-Request-Id";
    private static final String AWS_HANDLER_HEADER_NAME = "X-Amz-Handler";
    private static final String AWS_FUNCTION_ARN_HEADER_NAME = "X-Amz-Function-ARN";
    private static final String AWS_FUNCTION_NAME_HEADER_NAME = "X-Amz-Function-Name";
    private static final String AWS_FUNCTION_VERSION_HEADER_NAME = "X-Amz-Function-Version";
    private static final String AWS_RUNTIME_HEADER_NAME = "X-Amz-Runtime";
    private static final String AWS_TIMEOUT_HEADER_NAME = "X-Amz-Timeout";
    private static final String AWS_MEMORY_SIZE_HEADER_NAME = "X-Amz-Memory-Size";
    private static final String AWS_LOG_GROUP_NAME_HEADER_NAME = "X-Amz-Log-Group-Name";
    private static final String AWS_LOG_STREAM_NAME_HEADER_NAME = "X-Amz-Log-Stream-Name";
    private static final String AWS_ENV_VARS_HEADER_NAME = "X-Amz-Env-Vars";
    private static final String AWS_CLIENT_CONTEXT_HEADER_NAME = "X-Amz-Client-Context";
    private static final String AWS_COGNITO_IDENTITY_HEADER_NAME = "X-Amz-Cognito-Identity";

    private static final String MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME = "MERLOC_AWS_LAMBDA_HANDLER";
    private static final String AWS_REGION_ENV_VAR_NAME = "AWS_REGION";
    private static final String AWS_EXECUTION_ENV_ENV_VAR_NAME = "AWS_EXECUTION_ENV";

    private static final String CONTENT_TYPE_JSON_HEADER_VALUE = "'application/json";
    private static final String AWS_EXECUTION_ENV_PREFIX = "AWS_Lambda_";

    private static final int PING_RESPONSE_SUCCESS_CODE = 200;
    private static final int LAMBDA_RUNTIME_IS_IN_USE_CODE = 429;
    private static final int LAMBDA_RUNTIME_IS_NOT_LIVE_CODE = 502;
    private static final String PING_RESPONSE_SUCCESS_MESSAGE = "pong";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PojoSerializer<ClientContext> clientContextSerializer =
            HandlerHelper.getSerializer(ClientContext.class);
    private final PojoSerializer<CognitoIdentity> cognitoIdentitySerializer =
            HandlerHelper.getSerializer(CognitoIdentity.class);
    private final OkHttpClient lambdaRuntimeMessageClient =
            new OkHttpClient.Builder().
                connectTimeout(10, TimeUnit.SECONDS).
                writeTimeout(30, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.MINUTES).
                build();
    private final OkHttpClient lambdaRuntimePingClient =
            lambdaRuntimeMessageClient.
                    newBuilder().
                    connectTimeout(3, TimeUnit.SECONDS).
                    writeTimeout(3, TimeUnit.SECONDS).
                    readTimeout(3, TimeUnit.SECONDS).
                    build();
    private long latestPingFailTime = -1;

    @Override
    protected Future<RequestStreamHandler> getProxyLambdaHandler(Supplier<RequestStreamHandler> handlerSupplier) {
        return CompletableFuture.supplyAsync(handlerSupplier);
    }

    private Map<String, String> createRequestHeaders(Context context) throws IOException {
        Map<String, String> requestHeaders = new HashMap<>();

        requestHeaders.put(AWS_REGION_HEADER_NAME, LambdaUtils.getEnvVar(AWS_REGION_ENV_VAR_NAME));
        requestHeaders.put(AWS_REQUEST_ID_HEADER_NAME, context.getAwsRequestId());
        requestHeaders.put(AWS_HANDLER_HEADER_NAME, LambdaUtils.getEnvVar(MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME));
        requestHeaders.put(AWS_FUNCTION_ARN_HEADER_NAME, context.getInvokedFunctionArn());
        requestHeaders.put(AWS_FUNCTION_NAME_HEADER_NAME, context.getFunctionName());
        requestHeaders.put(AWS_FUNCTION_VERSION_HEADER_NAME, context.getFunctionVersion());
        requestHeaders.put(AWS_RUNTIME_HEADER_NAME,
                LambdaUtils.getEnvVar(AWS_EXECUTION_ENV_ENV_VAR_NAME).substring(AWS_EXECUTION_ENV_PREFIX.length()));
        requestHeaders.put(AWS_TIMEOUT_HEADER_NAME, String.valueOf(context.getRemainingTimeInMillis()));
        requestHeaders.put(AWS_MEMORY_SIZE_HEADER_NAME, String.valueOf(context.getMemoryLimitInMB()));
        requestHeaders.put(AWS_LOG_GROUP_NAME_HEADER_NAME, context.getLogGroupName());
        requestHeaders.put(AWS_LOG_STREAM_NAME_HEADER_NAME, context.getLogStreamName());

        requestHeaders.put(AWS_ENV_VARS_HEADER_NAME, objectMapper.writeValueAsString(LambdaUtils.getEnvVars()));

        ClientContext clientContext = context.getClientContext();
        if (clientContext != null) {
            ByteArrayOutputStream clientContextOutputStream = new ByteArrayOutputStream();
            clientContextSerializer.toJson(clientContext, clientContextOutputStream);
            requestHeaders.put(AWS_CLIENT_CONTEXT_HEADER_NAME, new String(clientContextOutputStream.toByteArray()));
        }

        CognitoIdentity cognitoIdentity = context.getIdentity();
        if (cognitoIdentity != null) {
            ByteArrayOutputStream cognitoIdentityOutputStream = new ByteArrayOutputStream();
            cognitoIdentitySerializer.toJson(cognitoIdentity, cognitoIdentityOutputStream);
            requestHeaders.put(AWS_COGNITO_IDENTITY_HEADER_NAME, new String(cognitoIdentityOutputStream.toByteArray()));
        }

        return requestHeaders;
    }

    @Override
    protected boolean onRequest(InputStream requestStream, OutputStream responseStream, Context context) {
        if (!ENABLE) {
            StdLogger.debug("MerLoc is disabled, so forwarding request to the actual handler");
            return true;
        }
        if (StringUtils.isNullOrEmpty(LAMBDA_RUNTIME_URL)) {
            StdLogger.debug("Lambda runtime URL is empty so forwarding request to the actual handler");
            return true;
        }
        try {
            long currentTime = System.currentTimeMillis();
            if (LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE > 0 && latestPingFailTime > 0) {
                long passedSecondsFromLatestPingFail = (currentTime - latestPingFailTime) / 1000;
                StdLogger.debug(String.format(
                        "%d seconds have passed since latest ping fail", passedSecondsFromLatestPingFail));
                if (passedSecondsFromLatestPingFail < LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE) {
                    StdLogger.debug(String.format(
                            "Forwarding request to the actual handler because not enough time (%d secs < %d secs) has passed " +
                                    "since latest ping fail",
                            passedSecondsFromLatestPingFail, LAMBDA_RUNTIME_PING_INTERVAL_ON_FAILURE));
                    return true;
                }
            }
            if (isLambdaRuntimeUp()) {
                Map<String, String> requestHeaders = createRequestHeaders(context);
                if (StdLogger.DEBUG_ENABLE) {
                    StdLogger.debug(String.format("Created request headers: %s", requestHeaders));
                }

                String requestData = IOUtils.readAllAsString(requestStream);

                if (StdLogger.DEBUG_ENABLE) {
                    StdLogger.debug(String.format(
                            "Forwarding request to Lambda runtime: %s", requestData));
                }

                Request request =
                        new Request.Builder().
                                url(LAMBDA_RUNTIME_URL).
                                headers(Headers.of(requestHeaders)).
                                post(RequestBody.create(MediaType.get(CONTENT_TYPE_JSON_HEADER_VALUE), requestData)).
                                build();
                Response response = lambdaRuntimeMessageClient.newCall(request).execute();

                byte[] responseData = response.body().bytes();
                if (StdLogger.DEBUG_ENABLE) {
                    StdLogger.debug(String.format(
                            "Received response from Lambda runtime: %s", new String(responseData)));
                }
                responseStream.write(responseData);

                return false;
            } else {
                latestPingFailTime = currentTime;
                StdLogger.debug("Lambda runtime is not up");
            }
        } catch (Throwable t) {
            StdLogger.error("Lambda runtime failed to handle request", t);
        }
        return true;
    }

    private boolean isLambdaRuntimeUp() {
        StdLogger.debug("Checking whether Lambda runtime is up ...");
        try {
            Request request =
                    new Request.Builder().
                            url(LAMBDA_RUNTIME_URL + "/ping").
                            get().
                            build();
            Response response = lambdaRuntimePingClient.newCall(request).execute();
            if (response.code() == PING_RESPONSE_SUCCESS_CODE) {
                ResponseBody responseBody = response.body();
                String responseMessage = responseBody != null ? responseBody.string() : null;
                if (PING_RESPONSE_SUCCESS_MESSAGE.equals(responseMessage)) {
                    StdLogger.debug("Lambda runtime is up");
                    return true;
                } else {
                    StdLogger.debug(String.format("Unexpected Lambda runtime ping response message: %s", responseMessage));
                    return false;
                }
            } else if (response.code() == LAMBDA_RUNTIME_IS_NOT_LIVE_CODE) {
                StdLogger.debug("Lambda runtime is not running");
                return false;
            } else if (response.code() == LAMBDA_RUNTIME_IS_IN_USE_CODE) {
                StdLogger.debug("Lambda runtime is already handling another invocation of this function");
                return false;
            } else {
                StdLogger.debug(String.format("Unexpected Lambda runtime ping response code: %d", response.code()));
                return false;
            }
        } catch (Throwable t) {
            StdLogger.debug(String.format("Lambda runtime ping request failed: %s", t.getMessage()));
            StdLogger.debug(ExceptionUtils.toString(t));
            return false;
        }
    }

}
