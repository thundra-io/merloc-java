package io.thundra.merloc.aws.lambda.runtime.embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.thundra.merloc.aws.lambda.core.logger.StdLogger;
import io.thundra.merloc.aws.lambda.core.utils.ExceptionUtils;
import io.thundra.merloc.aws.lambda.core.utils.IOUtils;
import io.thundra.merloc.aws.lambda.core.utils.StringUtils;
import io.thundra.merloc.aws.lambda.runtime.embedded.function.FunctionEnvironmentInitializer;
import io.thundra.merloc.aws.lambda.runtime.embedded.function.FunctionEnvironmentManager;
import io.thundra.merloc.aws.lambda.runtime.embedded.io.ManagedOutputStream;
import io.thundra.merloc.aws.lambda.runtime.embedded.utils.ClassLoaderUtils;
import io.thundra.merloc.aws.lambda.runtime.embedded.watcher.ClassPathChangeListener;
import io.thundra.merloc.aws.lambda.runtime.embedded.watcher.ClassPathWatcher;
import io.thundra.merloc.aws.lambda.runtime.embedded.domain.ErrorResponse;
import io.thundra.merloc.aws.lambda.runtime.embedded.function.FunctionEnvironment;
import io.thundra.merloc.aws.lambda.runtime.embedded.watcher.FileChangeEvent;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author serkan
 */
public class InvocationHandler implements HttpHandler, Closeable {

    private static final String TODAY = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
    private static final String CONTAINER_ID = UUID.randomUUID().toString();

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

    private static final String MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME = "MERLOC_LAMBDA_HANDLER";
    private static final String MERLOC_LAMBDA_HANDLER_CLASS_NAME =
            "io.thundra.merloc.aws.lambda.core.handler.WrapperLambdaHandler";

    private static final String DEFAULT_VERSION = "$LATEST";
    private static final int DEFAULT_TIMEOUT = -1;
    private static final int DEFAULT_MEMORY_SIZE = 512;
    private static final long DEFAULT_LAST_MODIFIED = -1;

    private static final int STATUS_CODE_SUCCESS = 200;
    private static final int STATUS_CODE_FAIL = 500;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_TOO_MANY_REQUESTS = 429;

    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";

    private static final int STATE_RUNNING = 0;
    private static final int STATE_CLOSING = 1;
    private static final int STATE_CLOSED = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger state = new AtomicInteger(STATE_RUNNING);
    private final ReadWriteLock handlerLock = new ReentrantReadWriteLock();

    private final Collection<URL> urls;
    private final Collection<URL> directoryUrls;
    private final FunctionEnvironmentManager funcEnvManager;
    private final LambdaRuntimeConcurrencyMode lambdaRuntimeConcurrencyMode;
    private final Lock lambdaRuntimeLock;
    private final FunctionConcurrencyMode functionConcurrencyMode;
    private final List<byte[]> serializedInitializers =
            new CopyOnWriteArrayList<>();
    private final ClassPathWatcher classPathWatcher;

    public InvocationHandler(ClassLoader appClassLoader,
                             ThreadGroup mainThreadGroup,
                             ManagedEnvironmentVariables managedEnvVars,
                             ManagedSystemProperties managedSysProps,
                             ManagedOutputStream managedStdOutStream,
                             ManagedOutputStream managedStdErrStream,
                             LambdaRuntimeConcurrencyMode lambdaRuntimeConcurrencyMode,
                             FunctionConcurrencyMode functionConcurrencyMode) throws IOException {
        this.urls = filterUrls(ClassLoaderUtils.fromClassLoader(appClassLoader));
        this.directoryUrls = filterDirectoryUrls(urls);
        this.funcEnvManager = new FunctionEnvironmentManager(
                mainThreadGroup,
                managedEnvVars, managedSysProps,
                managedStdOutStream, managedStdErrStream,
                serializedInitializers, appClassLoader, urls);
        this.lambdaRuntimeConcurrencyMode = lambdaRuntimeConcurrencyMode;
        this.lambdaRuntimeLock =
                (lambdaRuntimeConcurrencyMode == LambdaRuntimeConcurrencyMode.REJECT ||
                        lambdaRuntimeConcurrencyMode == LambdaRuntimeConcurrencyMode.WAIT)
                    ? new ReentrantLock()
                    : null;
        this.functionConcurrencyMode = functionConcurrencyMode;
        this.classPathWatcher = new ClassPathWatcher(directoryUrls, new FunctionEnvironmentReloader(), true);
        this.classPathWatcher.start();
    }

    private static Collection<URL> filterUrls(Collection<URL> urls) {
        List<URL> filteredURLs = new ArrayList<>();
        for (URL url : urls) {
            String fileName = url.getFile();
            int idx = fileName.lastIndexOf("/");
            if (idx > 0) {
                fileName = fileName.substring(idx + 1);
            }
            // Skip other MerLoc dependencies,
            // because all the required dependencies are exist in "merloc-aws-lambda-runtime.jar"
            if (fileName.startsWith("merloc-") && !fileName.startsWith("merloc-aws-lambda-runtime")) {
                continue;
            }
            filteredURLs.add(url);
        }
        return filteredURLs;
    }

    private static Collection<URL> filterDirectoryUrls(Collection<URL> urls) {
        List<URL> filteredURLs = new ArrayList<>();
        for (URL url : urls) {
            File file = new File(url.getFile());
            if (file.exists() && file.isDirectory()) {
                filteredURLs.add(url);
            }
        }
        return filteredURLs;
    }

    private class FunctionEnvironmentReloader implements ClassPathChangeListener {

        private static final String CLASS_FILE_EXT = ".class";

        @Override
        public void onChange(FileChangeEvent fileChangeEvent) {
            File file = fileChangeEvent.getFile();
            String filePath = file.getAbsolutePath();
            String fileName = file.getName();

            StdLogger.debug(String.format("Received file change event for file %s", filePath));

            if (file.isDirectory() || !fileName.endsWith(CLASS_FILE_EXT)) {
                StdLogger.debug(String.format(
                        "Ignored file change event for file %s " +
                        "as it is either directory nor not a class file",
                        filePath));
                return;
            }

            StdLogger.debug(String.format("Handling file change event for file %s ...", filePath));

            for (URL u : urls) {
                String classPathDir = u.getFile();
                if (filePath.startsWith(classPathDir)) {
                    String className = filePath.substring(classPathDir.length());
                    className = className.replace("/", ".");
                    className = className.substring(0, className.length() - CLASS_FILE_EXT.length());
                    StdLogger.debug(String.format("Detected change for class %s", className));

                    Collection<FunctionEnvironment> funcEnvs =
                            funcEnvManager.getEffectedFunctionEnvironments(className);
                    StdLogger.debug(String.format("Function environments will be reloaded: %s ...", funcEnvs));
                    for (FunctionEnvironment funcEnv : funcEnvs) {
                        try {
                            StdLogger.debug(String.format("Function environment will be reloaded: %s ...", funcEnv));
                            funcEnvManager.reloadFunctionEnvironment(funcEnv);
                            StdLogger.debug(String.format("Function environment has been reloaded: %s", funcEnv));
                        } catch (Throwable t) {
                            StdLogger.error(String.format("Unable to reload function environment: %s", funcEnv, t));
                        }
                    }
                    StdLogger.debug(String.format("Function environments has been reloaded: %s", funcEnvs));
                }
            }

            StdLogger.debug(String.format("Handled file change event for file %s ", filePath));
        }

    }

    byte[] serializeFunctionEnvironmentInitializer(FunctionEnvironmentInitializer initializer)
            throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(initializer);
        }
        return byteArrayOutputStream.toByteArray();
    }

    void registerFunctionEnvironmentInitializer(FunctionEnvironmentInitializer initializer) throws IOException {
        byte[] data = serializeFunctionEnvironmentInitializer(initializer);
        serializedInitializers.add(data);
    }

    void clearFunctionEnvironmentInitializers() {
        serializedInitializers.clear();
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        InputStream is = httpExchange.getRequestBody();
        OutputStream os = httpExchange.getResponseBody();
        if (state.get() != STATE_RUNNING) {
            sendErrorResponse(httpExchange, os, new IllegalStateException("Not in running state"), false, null);
            return;
        }
        Lock handleLock = handlerLock.readLock();
        handleLock.lock();
        try {
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
                String functionVersion = getHeader(httpExchange, AWS_FUNCTION_VERSION_HEADER_NAME, DEFAULT_VERSION);
                String runtime = getHeader(httpExchange, AWS_RUNTIME_HEADER_NAME);
                int timeout = Integer.parseInt(getHeader(httpExchange, AWS_TIMEOUT_HEADER_NAME,
                        String.valueOf(DEFAULT_TIMEOUT)));
                int memorySize = Integer.parseInt(getHeader(httpExchange, AWS_MEMORY_SIZE_HEADER_NAME,
                        String.valueOf(DEFAULT_MEMORY_SIZE)));
                String logGroupName = getHeader(httpExchange, AWS_LOG_GROUP_NAME_HEADER_NAME);
                String logStreamName = getHeader(httpExchange, AWS_LOG_STREAM_NAME_HEADER_NAME);
                Map<String, String> envVars =
                        (Map<String, String>) (Map) new JSONObject(getHeader(httpExchange, AWS_ENV_VARS_HEADER_NAME)).toMap();
                String clientContext = getHeader(httpExchange, AWS_CLIENT_CONTEXT_HEADER_NAME);
                String cognitoIdentity = getHeader(httpExchange, AWS_COGNITO_IDENTITY_HEADER_NAME);
                long lastModified = Long.parseLong(getHeader(httpExchange, AWS_LAST_MODIFIED,
                        String.valueOf(DEFAULT_LAST_MODIFIED)));

                if (StdLogger.DEBUG_ENABLE) {
                    StdLogger.debug(String.format(
                            "Received request data for the invocation function %s: %s",
                            functionName, request));
                }

                StdLogger.debug(String.format(
                        "Received invocation request: " +
                                "region=%s, requestId=%s, handler=%s, functionArn=%s, functionName=%s, " +
                                "functionVersion=%s, runtime=%s, timeout=%d, memorySize=%s, " +
                                "logGroupName=%s, logStreamName=%s, envVars=%s, " +
                                "clientContext=%s, cognitoIdentity=%s, lastModified=%d",
                        region, requestId, handler, functionArn, functionName, functionVersion,
                        runtime, timeout, memorySize, logGroupName, logStreamName, envVars,
                        clientContext, cognitoIdentity, lastModified));

                validateRequest(
                        request, region, requestId, handler,
                        functionArn, functionName, functionVersion,
                        runtime, timeout, memorySize,
                        logGroupName, logStreamName,
                        envVars, clientContext, cognitoIdentity, lastModified);

                httpExchange.getResponseHeaders().add(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_JSON_HEADER_VALUE);
                httpExchange.getResponseHeaders().add(AWS_EXECUTED_VERSION_HEADER_NAME, functionVersion);

                try {
                    if (lambdaRuntimeLock != null) {
                        StdLogger.debug(String.format(
                                "Getting runtime lock of function environment for function %s ...", functionName));
                        if (lambdaRuntimeConcurrencyMode == LambdaRuntimeConcurrencyMode.REJECT) {
                            boolean locked = lambdaRuntimeLock.tryLock();
                            if (!locked) {
                                StdLogger.debug(String.format(
                                        "Unable to lock runtime for function %s as it is in use", functionName));
                                throw new RuntimeInUseException(String.format(
                                        "Unable to lock runtime for function %s as it is in use", functionName));
                            }
                        } else {
                            lambdaRuntimeLock.lock();
                        }
                        StdLogger.debug(String.format(
                                "Got runtime lock of function environment for function %s", functionName));
                    }
                    byte[] responseData;
                    try {
                        responseData = executeHandler(
                                request, region, requestId, handler,
                                functionArn, functionName, functionVersion,
                                runtime, timeout, memorySize,
                                logGroupName, logStreamName,
                                envVars, clientContext, cognitoIdentity, lastModified);
                    } finally {
                        if (lambdaRuntimeLock != null) {
                            StdLogger.debug(String.format(
                                    "Releasing runtime lock of function environment for function %s ...", functionName));
                            lambdaRuntimeLock.unlock();
                            StdLogger.debug(String.format(
                                    "Released runtime lock of function environment for function %s", functionName));
                        }
                    }

                    // TODO Return logs by "AWS_LOG_RESULT_HEADER_NAME" response header

                    httpExchange.sendResponseHeaders(STATUS_CODE_SUCCESS, responseData.length);

                    if (StdLogger.DEBUG_ENABLE) {
                        StdLogger.debug(String.format(
                                "Writing response data for the invocation function %s: %s",
                                functionName, new String(responseData)));
                    }
                    os.write(responseData);
                } catch (Throwable error) {
                    sendErrorResponse(httpExchange, os, error, true, functionName);
                }
            } catch (Throwable error) {
                sendErrorResponse(httpExchange, os, error, false, functionName);
            } finally {
                os.flush();
                os.close();
            }
        } finally {
            handleLock.unlock();
        }
    }

    @Override
    public void close() {
        if (state.compareAndSet(STATE_RUNNING, STATE_CLOSING)) {
            StdLogger.debug("Closing invocation handler");
            Lock closeLock = handlerLock.writeLock();
            closeLock.lock();
            try {
                classPathWatcher.stop();
                funcEnvManager.closeFunctionEnvironments();
            } finally {
                state.set(STATE_CLOSED);
                closeLock.unlock();
                StdLogger.debug("Closed invocation handler");
            }
        } else {
            StdLogger.debug("Skipping closing invocation handler because it is not in running state");
        }
    }

    private Map<String, String> buildFunctionEnvVars(
            String region, String requestId, String handler,
            String functionArn, String functionName, String functionVersion,
            String runtime, int timeout, int memorySize,
            String logGroupName, String logStreamName,
            Map<String, String> envVars) throws Exception {
        /*
         Reserved environment variables:
            _HANDLER:
                The handler location configured on the function.
            _X_AMZN_TRACE_ID:
                The X-Ray tracing header.
            AWS_REGION:
                The AWS Region where the Lambda function is executed.
            AWS_EXECUTION_ENV:
                The runtime identifier, prefixed by AWS_Lambda_—for example, AWS_Lambda_java8.
            AWS_LAMBDA_FUNCTION_NAME:
                The name of the function.
            AWS_LAMBDA_FUNCTION_MEMORY_SIZE:
                The amount of memory available to the function in MB.
            AWS_LAMBDA_FUNCTION_VERSION:
                The version of the function being executed.
            AWS_LAMBDA_INITIALIZATION_TYPE:
                The initialization type of the function, which is either on-demand or provisioned-concurrency.
                For information, see Configuring provisioned concurrency.
            AWS_LAMBDA_LOG_GROUP_NAME, AWS_LAMBDA_LOG_STREAM_NAME:
                The name of the Amazon CloudWatch Logs group and stream for the function.
            AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN:
                The access keys obtained from the function's execution role.
            AWS_LAMBDA_RUNTIME_API:
                (Custom runtime) The host and port of the runtime API.
            LAMBDA_TASK_ROOT:
                The path to your Lambda function code.
            LAMBDA_RUNTIME_DIR:
                The path to runtime libraries.
            TZ:
                The environment's time zone (UTC). The execution environment uses NTP to synchronize the system clock.
         */

        Map<String, String> functionEnvVars = new HashMap<>();

        if (StringUtils.isNullOrEmpty(logGroupName)) {
            logGroupName = String.format("/aws/lambda/%s", functionName);
        }
        if (StringUtils.isNullOrEmpty(logStreamName)) {
            logStreamName = String.format("%s[%s]%s", TODAY, functionVersion, CONTAINER_ID);
        }

        functionEnvVars.put(MERLOC_LAMBDA_HANDLER_ENV_VAR_NAME, handler);
        if (envVars != null) {
            for (Map.Entry<String, String> e : envVars.entrySet()) {
                functionEnvVars.put(e.getKey(), e.getValue());
            }
        }

        functionEnvVars.put(
                LambdaEnvironmentVariables.HANDLER_ENV_VAR_NAME,
                MERLOC_LAMBDA_HANDLER_CLASS_NAME);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_TRACE_ID_ENV_VAR_NAME,
                UUID.randomUUID().toString());
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_REGION_ENV_VAR_NAME,
                region);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_FUNCTION_INVOKED_ARN_ENV_VAR_NAME,
                functionArn);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME_ENV_VAR_NAME,
                functionName);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_FUNCTION_MEMORY_SIZE_ENV_VAR_NAME,
                String.valueOf(memorySize));
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_FUNCTION_VERSION_ENV_VAR_NAME,
                functionVersion);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_LOG_GROUP_NAME_ENV_VAR_NAME,
                logGroupName);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_LOG_STREAM_NAME_ENV_VAR_NAME,
                logStreamName);
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_LAMBDA_INITIALIZATION_TYPE_ENV_VAR_NAME,
                "on-demand");
        functionEnvVars.put(
                LambdaEnvironmentVariables.AWS_EXECUTION_ENV_ENV_VAR_NAME,
                "AWS_Lambda_" + runtime);

        StdLogger.debug(String.format("Setup environment with environment variables for function %s: %s",
                functionName, envVars));

        return functionEnvVars;
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

    private void validateRequest(String request, String region, String requestId, String handlerName,
                                 String functionArn, String functionName, String functionVersion,
                                 String runtime, int timeout, int memorySize,
                                 String logGroupName, String logStreamName,
                                 Map<String, String> envVars, String clientContext, String cognitoIdentity,
                                 long lastModified) throws InvalidRequestException {
        StdLogger.debug(String.format("Validating invocation for function %s ...", functionName));

        boolean validationFailed = false;
        StringBuilder validationErrorBuilder = new StringBuilder("Request validation failed!");

        if (StringUtils.isNullOrEmpty(region)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Region cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(requestId)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Request id cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(handlerName)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Handler name cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(functionArn)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Function ARN cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(functionName)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Function name cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(functionVersion)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Function version cannot be empty").append("\n");
        }
        if (StringUtils.isNullOrEmpty(runtime)) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Runtime cannot be empty").append("\n");
        }
        if (envVars == null) {
            validationFailed = true;
            validationErrorBuilder.append("\t-").append("Environment variables cannot be empty").append("\n");
        }

        if (validationFailed) {
            throw new InvalidRequestException(validationErrorBuilder.toString());
        }
    }

    private Object createContext(FunctionEnvironment functionEnvironment,
                                 String functionArn, String requestId, int timeout,
                                 String clientContextJson, String cognitoIdentityJson) {
        StdLogger.debug(String.format("Creating context for function %s ...", functionEnvironment.getFunctionName()));
        try {
            Object context = functionEnvironment.createContext(
                    requestId, timeout, clientContextJson, cognitoIdentityJson);
            StdLogger.debug(String.format(
                    "Created context for function %s: %s",
                    functionEnvironment.getFunctionName(),
                    context));
            return context;
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                t = ((InvocationTargetException) t).getTargetException();
            }
            StdLogger.error(String.format(
                    "Failed to create context for function %s",
                    functionEnvironment.getFunctionName(), t));
            ExceptionUtils.sneakyThrow(t);
            return null;
        }
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
            if (StdLogger.DEBUG_ENABLE) {
                StdLogger.debug(String.format(
                        "Sending error (handled=%b) response for invocation of function %s: %s",
                        handled, functionName, new String(responseData)));
            }
            int statusCode = STATUS_CODE_FAIL;
            if (error instanceof StatusCodeAwareException) {
                statusCode = ((StatusCodeAwareException) error).getStatusCode();
            }
            httpExchange.sendResponseHeaders(statusCode, responseData.length);
            os.write(responseData);
        } catch (Throwable err) {
            byte[] responseData = error.toString().getBytes();
            StdLogger.debug(String.format(
                    "Sending error (failed on error response) response for invocation of function %s: %s",
                    handled, functionName, new String(responseData)));
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

    private byte[] executeHandler(String request, String region, String requestId, String handlerName,
                                  String functionArn, String functionName, String functionVersion,
                                  String runtime, int timeout, int memorySize,
                                  String logGroupName, String logStreamName,
                                  Map<String, String> envVars, String clientContext, String cognitoIdentity,
                                  long lastModified) throws Exception {
        Lock lock = funcEnvManager.getFunctionEnvironmentLock(functionArn);
        StdLogger.debug(String.format("Locking function environment for function %s ...", functionName));
        if (functionConcurrencyMode == FunctionConcurrencyMode.REJECT) {
            boolean locked = lock.tryLock();
            if (!locked) {
                StdLogger.debug(String.format(
                        "Unable to lock function environment for function %s as it is in use", functionName));
                throw new FunctionInUseException(String.format(
                        "Unable to lock function environment for function %s as it is in use", functionName));
            }
        } else {
            lock.lock();
        }
        StdLogger.debug(String.format("Locked function environment for function %s", functionName));
        try {
            StdLogger.debug(String.format("Executing handler for function %s ...", functionName));

            Callable<Map<String, String>> functionEnvVarsBuilder = () -> buildFunctionEnvVars(
                    region, requestId, handlerName,
                    functionArn, functionName, functionVersion,
                    runtime, timeout, memorySize,
                    logGroupName, logStreamName,
                    envVars);
            FunctionEnvironment functionEnvironment =
                    funcEnvManager.getOrCreateFunctionEnvironment(
                            functionEnvVarsBuilder,
                            functionArn, functionName, functionVersion, memorySize,
                            handlerName, lastModified);

            InputStream requestStream = new ByteArrayInputStream(request.getBytes());
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            Object context = createContext(
                    functionEnvironment, functionArn, requestId, timeout,
                    clientContext, cognitoIdentity);

            StdLogger.debug(String.format("Executing function environment for function %s ...", functionName));
            functionEnvironment.execute(requestStream, responseStream, context, requestId, envVars);
            StdLogger.debug(String.format("Executed function environment for function %s", functionName));

            byte[] response = responseStream.toByteArray();
            if (StdLogger.DEBUG_ENABLE) {
                StdLogger.debug(String.format(
                        "Executed handler for function %s and received response: %s",
                        functionName, new String(response)));
            }
            return response;
        } catch (Throwable t) {
            StdLogger.error(String.format(
                    "Failed execution of handler for function %s",
                    functionName), t);
            throw t;
        } finally {
            StdLogger.debug(String.format("Unlocking function environment for function %s ...", functionName));
            lock.unlock();
            StdLogger.debug(String.format("Unlocked function environment for function %s", functionName));
        }
    }

    private static abstract class StatusCodeAwareException extends Exception {

        public StatusCodeAwareException() {
        }

        public StatusCodeAwareException(String message) {
            super(message);
        }

        public StatusCodeAwareException(String message, Throwable cause) {
            super(message, cause);
        }

        public StatusCodeAwareException(Throwable cause) {
            super(cause);
        }

        public StatusCodeAwareException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public abstract int getStatusCode();

    }

    private static class InvalidRequestException extends StatusCodeAwareException {

        public InvalidRequestException(String message) {
            super(message);
        }

        @Override
        public int getStatusCode() {
            return STATUS_BAD_REQUEST;
        }

    }

    private static class InvalidHttpMethodException extends StatusCodeAwareException {

        public InvalidHttpMethodException(String message) {
            super(message);
        }

        @Override
        public int getStatusCode() {
            return STATUS_METHOD_NOT_ALLOWED;
        }

    }

    private static class RuntimeInUseException extends StatusCodeAwareException {

        public RuntimeInUseException(String message) {
            super(message);
        }

        @Override
        public int getStatusCode() {
            return STATUS_TOO_MANY_REQUESTS;
        }

    }

    private static class FunctionInUseException extends StatusCodeAwareException {

        public FunctionInUseException(String message) {
            super(message);
        }

        @Override
        public int getStatusCode() {
            return STATUS_TOO_MANY_REQUESTS;
        }

    }

}
