package io.thundra.merloc.aws.lambda.runtime.embedded.handler.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.thundra.merloc.aws.lambda.runtime.embedded.InvocationExecutor;
import io.thundra.merloc.aws.lambda.runtime.embedded.domain.ErrorResponse;
import io.thundra.merloc.aws.lambda.runtime.embedded.exception.FunctionInUseException;
import io.thundra.merloc.aws.lambda.runtime.embedded.exception.RuntimeInUseException;
import io.thundra.merloc.aws.lambda.runtime.embedded.handler.InvocationHandler;
import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.ExecutorUtils;
import io.thundra.merloc.common.utils.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author serkan
 */
public class HttpInvocationHandler implements InvocationHandler, HttpHandler {

    private static final String RUNTIME_PORT_CONFIG_NAME =
            "merloc.runtime.aws.lambda.runtime.http.port";
    private static final int DEFAULT_RUNTIME_PORT = 8080;

    private static final String PING_PATH = "/ping";
    private static final byte[] PING_RESPONSE = "pong".getBytes(StandardCharsets.UTF_8);

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
    private static final String AWS_LAST_MODIFIED = "X-Amz-Last-Modified";

    private static final String AWS_FUNCTION_ERROR_HEADER_NAME = "X-Amz-Function-Error";
    private static final String AWS_LOG_RESULT_HEADER_NAME = "X-Amz-Log-Result";
    private static final String AWS_EXECUTED_VERSION_HEADER_NAME = "X-Amz-Executed-Version";

    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    private static final String CONTENT_TYPE_JSON_HEADER_VALUE = "'application/json";
    private static final String CONTENT_TYPE_TEXT_HEADER_VALUE = "'text/plain";

    private static final int STATUS_CODE_SUCCESS = 200;
    private static final int STATUS_CODE_FAIL = 500;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_TOO_MANY_REQUESTS = 429;

    private static final Map<Class<? extends Throwable>, Integer> exceptionStatusCodeMapping =
            new HashMap<Class<? extends Throwable>, Integer>() {{
                put(InvalidHttpMethodException.class, STATUS_BAD_REQUEST);
                put(InvalidHttpMethodException.class, STATUS_METHOD_NOT_ALLOWED);
                put(RuntimeInUseException.class, STATUS_TOO_MANY_REQUESTS);
                put(FunctionInUseException.class, STATUS_TOO_MANY_REQUESTS);
            }};

    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InvocationExecutor invocationExecutor;
    private HttpServer server;

    public HttpInvocationHandler(InvocationExecutor invocationExecutor) {
        this.invocationExecutor = invocationExecutor;
    }

    private String getHeader(HttpExchange httpExchange, String headerName) {
        return getHeader(httpExchange, headerName, null);
    }

    private String getHeader(HttpExchange httpExchange, String headerName, String defaultValue) {
        Headers headers = httpExchange.getRequestHeaders();
        for (String name : headers.keySet()) {
            if (name.equalsIgnoreCase(headerName)) {
                return headers.getFirst(name);
            }
        }
        return defaultValue;
    }

    private int getExceptionStatusCode(Throwable error) {
        return exceptionStatusCodeMapping.getOrDefault(error.getClass(), STATUS_CODE_FAIL);
    }

    private void sendErrorResponse(HttpExchange httpExchange, OutputStream os, Throwable error,
                                   boolean handled, String functionName) throws IOException {
        try {
            httpExchange.getResponseHeaders().add(AWS_FUNCTION_ERROR_HEADER_NAME, handled ? "Handled" : "Unhandled");
            // TODO Return logs by "AWS_LOG_RESULT_HEADER_NAME" response header

            ErrorResponse errorResponse =
                    new ErrorResponse(
                            error.getClass().getName(),
                            error.getMessage(),
                            extractStackTrace(error));
            byte[] responseData = objectMapper.writeValueAsBytes(errorResponse);
            if (StdLogger.DEBUG_ENABLED) {
                StdLogger.debug(String.format(
                        "Sending error (handled=%b) response for invocation of function %s: %s",
                        handled, functionName, new String(responseData)));
            }
            int statusCode = getExceptionStatusCode(error);
            httpExchange.sendResponseHeaders(statusCode, responseData.length);
            os.write(responseData);
        } catch (Throwable err) {
            byte[] responseData = error.toString().getBytes();
            StdLogger.debug(String.format(
                    "Sending error (failed on error response) response for invocation of function %s: %s",
                    functionName, new String(responseData)));
            httpExchange.sendResponseHeaders(STATUS_CODE_FAIL, responseData.length);
            os.write(responseData);
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

    private static int getRuntimePort() {
        return ConfigManager.getIntegerConfig(RUNTIME_PORT_CONFIG_NAME, DEFAULT_RUNTIME_PORT);
    }

    private static class InvalidHttpMethodException extends Exception {

        public InvalidHttpMethodException(String message) {
            super(message);
        }

    }

    @Override
    public void start() throws IOException {
        int runtimePort = getRuntimePort();
        StdLogger.debug("Runtime port: " + runtimePort);

        StdLogger.debug("Creating runtime ...");
        server = HttpServer.create(new InetSocketAddress(runtimePort), 0);
        StdLogger.debug("Created runtime");

        server.setExecutor(ExecutorUtils.newCachedExecutorService("lambda-runtime"));

        StdLogger.debug("Starting runtime ...");
        server.start();
        StdLogger.debug("Started runtime");

        server.createContext("/", this);
        StdLogger.debug("Registered invocation handler to the root path");
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        InputStream is = httpExchange.getRequestBody();
        OutputStream os = httpExchange.getResponseBody();
        String functionName = null;
        try {
            String path = httpExchange.getRequestURI().getPath();

            StdLogger.debug(String.format("Received invocation request at path %s", path));

            if (PING_PATH.equals(path)) {
                if (!httpExchange.getRequestMethod().equalsIgnoreCase(HTTP_METHOD_GET)) {
                    StdLogger.error(String.format(
                            "Ping requests can only be sent via '%s', but has been sent via '%s'",
                            HTTP_METHOD_GET, httpExchange.getRequestMethod()));
                    throw new InvalidHttpMethodException(String.format(
                            "Ping requests can only be sent via '%s', but has been sent via '%s'",
                            HTTP_METHOD_GET, httpExchange.getRequestMethod()));
                }
                httpExchange.getResponseHeaders().add(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_TEXT_HEADER_VALUE);
                httpExchange.sendResponseHeaders(STATUS_CODE_SUCCESS, PING_RESPONSE.length);

                os.write(PING_RESPONSE);
                os.flush();
                os.close();

                return;
            }

            if (!httpExchange.getRequestMethod().equalsIgnoreCase(HTTP_METHOD_POST)) {
                StdLogger.error(String.format(
                        "Invocation requests can only be sent via '%s', but has been sent via '%s'",
                        HTTP_METHOD_POST, httpExchange.getRequestMethod()));
                throw new InvalidHttpMethodException(String.format(
                        "Ping requests can only be sent via '%s', but has been sent via '%s'",
                        HTTP_METHOD_POST, httpExchange.getRequestMethod()));
            }

            // TODO Validate path
            // /2015-03-31/functions/${functionName}/invocations

            String request = IOUtils.readAllAsString(is);

            String region = getHeader(httpExchange, AWS_REGION_HEADER_NAME);
            String requestId = getHeader(httpExchange, AWS_REQUEST_ID_HEADER_NAME);
            String handler = getHeader(httpExchange, AWS_HANDLER_HEADER_NAME);
            String functionArn = getHeader(httpExchange, AWS_FUNCTION_ARN_HEADER_NAME);
            functionName = getHeader(httpExchange, AWS_FUNCTION_NAME_HEADER_NAME);
            String functionVersion = getHeader(httpExchange, AWS_FUNCTION_VERSION_HEADER_NAME,
                    InvocationExecutor.DEFAULT_VERSION);
            String runtime = getHeader(httpExchange, AWS_RUNTIME_HEADER_NAME);
            int timeout = Integer.parseInt(getHeader(httpExchange, AWS_TIMEOUT_HEADER_NAME,
                    String.valueOf(InvocationExecutor.DEFAULT_TIMEOUT)));
            int memorySize = Integer.parseInt(getHeader(httpExchange, AWS_MEMORY_SIZE_HEADER_NAME,
                    String.valueOf(InvocationExecutor.DEFAULT_MEMORY_SIZE)));
            String logGroupName = getHeader(httpExchange, AWS_LOG_GROUP_NAME_HEADER_NAME);
            String logStreamName = getHeader(httpExchange, AWS_LOG_STREAM_NAME_HEADER_NAME);
            Map<String, String> envVars =
                    (Map<String, String>) (Map) new JSONObject(
                            getHeader(httpExchange, AWS_ENV_VARS_HEADER_NAME)).toMap();
            String clientContext = getHeader(httpExchange, AWS_CLIENT_CONTEXT_HEADER_NAME);
            String cognitoIdentity = getHeader(httpExchange, AWS_COGNITO_IDENTITY_HEADER_NAME);
            long lastModified = Long.parseLong(getHeader(httpExchange, AWS_LAST_MODIFIED,
                    String.valueOf(InvocationExecutor.DEFAULT_LAST_MODIFIED)));

            httpExchange.getResponseHeaders().add(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_JSON_HEADER_VALUE);
            httpExchange.getResponseHeaders().add(AWS_EXECUTED_VERSION_HEADER_NAME, functionVersion);

            try {
                String response = invocationExecutor.execute(
                        request, region, requestId, handler,
                        functionArn, functionName, functionVersion,
                        runtime, timeout, memorySize,
                        logGroupName, logStreamName,
                        envVars, clientContext, cognitoIdentity, lastModified);

                // TODO Return logs by "AWS_LOG_RESULT_HEADER_NAME" response header

                httpExchange.sendResponseHeaders(STATUS_CODE_SUCCESS, response.length());

                os.write(response.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable error) {
                sendErrorResponse(httpExchange, os, error, true, functionName);
            }
        } catch (Throwable error) {
            sendErrorResponse(httpExchange, os, error, false, functionName);
        } finally {
            os.flush();
            os.close();
        }
    }

    @Override
    public void stop() throws IOException {
        server.removeContext("/");
        server.stop(0);

        invocationExecutor.close();
    }

}
