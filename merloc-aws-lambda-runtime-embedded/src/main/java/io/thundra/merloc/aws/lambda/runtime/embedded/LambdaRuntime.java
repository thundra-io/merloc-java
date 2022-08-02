package io.thundra.merloc.aws.lambda.runtime.embedded;

import com.sun.net.httpserver.HttpServer;
import io.thundra.merloc.aws.lambda.core.logger.StdLogger;
import io.thundra.merloc.aws.lambda.core.utils.IOUtils;
import io.thundra.merloc.aws.lambda.core.utils.ReflectionUtils;
import io.thundra.merloc.aws.lambda.runtime.embedded.config.ConfigHelper;
import io.thundra.merloc.aws.lambda.runtime.embedded.function.FunctionEnvironmentInitializer;
import io.thundra.merloc.aws.lambda.runtime.embedded.io.ManagedOutputStream;
import io.thundra.merloc.aws.lambda.runtime.embedded.utils.ExecutorUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author serkan
 */
public class LambdaRuntime {

    private static final String RUNTIME_PORT_CONFIG_NAME =
            "merloc.runtime.aws.lambda.runtime.port";
    private static final int DEFAULT_RUNTIME_PORT = 8080;
    private static final String RUNTIME_CONCURRENCY_MODE_CONFIG_NAME =
            "merloc.runtime.aws.lambda.runtime.concurrency.mode";
    private static final String FUNCTION_CONCURRENCY_MODE_CONFIG_NAME =
            "merloc.runtime.aws.lambda.runtime.function.concurrency.mode";

    private static Map<String, String> originalEnvVars;
    private static Properties originalSysProps;
    private static PrintStream originalStdOutStream;
    private static PrintStream originalStdErrStream;
    private static LambdaRuntimeContext lambdaRuntimeContext;
    private static boolean initialized;

    private static HttpServer server;
    private static InvocationHandler invocationHandler;
    private static boolean started = false;

    public static void main(String[] args) throws Exception {
        start();
    }

    private static class LambdaRuntimeContext {

        private final ManagedEnvironmentVariables managedEnvVars;
        private final ManagedSystemProperties managedSysProps;
        private final ManagedOutputStream managedStdOutStream;
        private final ManagedOutputStream managedStdErrStream;

        private LambdaRuntimeContext(ManagedEnvironmentVariables managedEnvVars,
                                     ManagedSystemProperties managedSysProps,
                                     ManagedOutputStream managedStdOutStream,
                                     ManagedOutputStream managedStdErrStream) {
            this.managedEnvVars = managedEnvVars;
            this.managedSysProps = managedSysProps;
            this.managedStdOutStream = managedStdOutStream;
            this.managedStdErrStream = managedStdErrStream;
        }

    }

    private static synchronized void ensureInitialized() {
        if (!initialized) {
            originalEnvVars = System.getenv();
            originalSysProps = System.getProperties();
            originalStdOutStream = System.out;
            originalStdErrStream = System.err;
            initialized = true;
            StdLogger.debug("Initialized Lambda runtime");
        }
    }

    public static synchronized boolean start() throws Exception {
        ensureInitialized();

        if (started) {
            StdLogger.error("Lambda runtime has been already started");
            return false;
        }

        printBanner();

        StdLogger.debug("Starting Lambda runtime ...");

        ManagedEnvironmentVariables managedEnvVars = wrapEnvVars();
        ManagedSystemProperties managedSysProps = wrapSysProps();

        ManagedOutputStream managedStdOutStream = wrapStdOutStream();
        ManagedOutputStream managedStdErrStream = wrapStdErrStream();

        lambdaRuntimeContext =
                new LambdaRuntimeContext(
                        managedEnvVars, managedSysProps, managedStdOutStream, managedStdErrStream);

        int runtimePort = getRuntimePort();
        StdLogger.debug("Runtime port: " + runtimePort);

        StdLogger.debug("Creating runtime ...");
        server = HttpServer.create(new InetSocketAddress(runtimePort), 0);
        StdLogger.debug("Created runtime");

        server.setExecutor(ExecutorUtils.newCachedExecutorService("lambda-runtime"));

        StdLogger.debug("Starting runtime ...");
        server.start();
        StdLogger.debug("Started runtime");

        invocationHandler = createInvocationHandler(server, lambdaRuntimeContext);

        started = true;

        StdLogger.debug("Started Lambda runtime");

        return true;
    }

    private static void printBanner() {
        try {
            System.out.println(IOUtils.readAllAsString(
                    LambdaRuntime.class.getClassLoader().getResourceAsStream("merloc-banner.txt")));
        } catch (Throwable t) {
            StdLogger.error("Unable to print banner", t);
        }
    }

    private static InvocationHandler createInvocationHandler(HttpServer server,
                                                             LambdaRuntimeContext lambdaRuntimeContext) throws Exception {
        LambdaRuntimeConcurrencyMode lambdaRuntimeConcurrencyMode = getLambdaRuntimeConcurrencyMode();
        StdLogger.debug("Runtime concurrency mode: " + lambdaRuntimeConcurrencyMode);

        FunctionConcurrencyMode functionConcurrencyMode = getFunctionConcurrencyMode();
        StdLogger.debug("Function concurrency mode: " + functionConcurrencyMode);

        StdLogger.debug("Creating invocation handler ...");
        InvocationHandler invocationHandler = new InvocationHandler(
                LambdaRuntime.class.getClassLoader(),
                Thread.currentThread().getThreadGroup(),
                lambdaRuntimeContext.managedEnvVars, lambdaRuntimeContext.managedSysProps,
                lambdaRuntimeContext.managedStdOutStream, lambdaRuntimeContext.managedStdErrStream,
                lambdaRuntimeConcurrencyMode, functionConcurrencyMode);
        StdLogger.debug("Created invocation handler");

        server.createContext("/", invocationHandler);
        StdLogger.debug("Registered invocation handler to the root path");

        return invocationHandler;
    }

    public static synchronized void reset() throws Exception {
        if (!started) {
            throw new IllegalStateException("Lambda runtime has not been started yet");
        }

        StdLogger.debug("Resetting Lambda runtime ...");

        server.removeContext("/");
        invocationHandler.close();

        invocationHandler = null;
        invocationHandler = createInvocationHandler(server, lambdaRuntimeContext);

        StdLogger.debug("Reset Lambda runtime");
    }

    public static synchronized void stop() throws Exception {
        if (!started) {
            throw new IllegalStateException("Lambda runtime has not been started yet");
        }

        StdLogger.debug("Closing Lambda runtime ...");

        try {
            invocationHandler.close();

            server.stop(0);

            unwrapEnvVars(originalEnvVars);
            unwrapSysProps(originalSysProps);

            unwrapStdOutStream(originalStdOutStream);
            unwrapStdErrStream(originalStdErrStream);
        } finally {
            invocationHandler = null;
            lambdaRuntimeContext = null;
            server = null;

            started = false;

            StdLogger.debug("Closed Lambda runtime");
        }
    }

    public static synchronized void registerFunctionEnvironmentInitializer(
            FunctionEnvironmentInitializer initializer) throws IOException {
        InvocationHandler invocationHandler = LambdaRuntime.invocationHandler;
        if (invocationHandler != null) {
            invocationHandler.registerFunctionEnvironmentInitializer(initializer);
        }
    }

    public static synchronized void clearFunctionEnvironmentInitializers() {
        InvocationHandler invocationHandler = LambdaRuntime.invocationHandler;
        if (invocationHandler != null) {
            invocationHandler.clearFunctionEnvironmentInitializers();
        }
    }

    private static int getRuntimePort() {
        return ConfigHelper.getIntegerConfig(RUNTIME_PORT_CONFIG_NAME, DEFAULT_RUNTIME_PORT);
    }

    private static LambdaRuntimeConcurrencyMode getLambdaRuntimeConcurrencyMode() {
        LambdaRuntimeConcurrencyMode lambdaRuntimeConcurrencyMode =
                LambdaRuntimeConcurrencyMode.of(ConfigHelper.getConfig(RUNTIME_CONCURRENCY_MODE_CONFIG_NAME));
        if (lambdaRuntimeConcurrencyMode == null) {
            // By default, runtime concurrency mode is "REJECT"
            lambdaRuntimeConcurrencyMode = LambdaRuntimeConcurrencyMode.REJECT;
        }
        return lambdaRuntimeConcurrencyMode;
    }

    private static FunctionConcurrencyMode getFunctionConcurrencyMode() {
        FunctionConcurrencyMode functionConcurrencyMode =
                FunctionConcurrencyMode.of(ConfigHelper.getConfig(FUNCTION_CONCURRENCY_MODE_CONFIG_NAME));
        if (functionConcurrencyMode == null) {
            // By default, function concurrency mode is "REJECT"
            functionConcurrencyMode = FunctionConcurrencyMode.REJECT;
        }
        return functionConcurrencyMode;
    }

    private static ManagedEnvironmentVariables wrapEnvVars() throws Exception {
        Map<String, String> envVars = new HashMap<>(LambdaRuntime.originalEnvVars);

        ManagedEnvironmentVariables managedEnvVars = new ManagedEnvironmentVariables(envVars);

        Class processingEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
        ReflectionUtils.setClassField(processingEnvironmentClass, "theUnmodifiableEnvironment", managedEnvVars);

        StdLogger.debug("Wrapped environment variables with managed environment variables");

        return managedEnvVars;
    }

    private static void unwrapEnvVars(Map<String, String> originalEnvVars) throws Exception {
        Class processingEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
        ReflectionUtils.setClassField(processingEnvironmentClass, "theUnmodifiableEnvironment", originalEnvVars);

        StdLogger.debug("Unwrapped environment variables to original values");
    }

    private static ManagedSystemProperties wrapSysProps() {
        Properties sysProps = new Properties();
        LambdaRuntime.originalSysProps.forEach((key, value) -> sysProps.setProperty((String) key, (String) value));

        ManagedSystemProperties managedSysProps = new ManagedSystemProperties(sysProps);

        System.setProperties(managedSysProps);

        StdLogger.debug("Wrapped system properties with managed system properties");

        return managedSysProps;
    }

    private static void unwrapSysProps(Properties originalSysProps) throws Exception {
        System.setProperties(originalSysProps);

        StdLogger.debug("Unwrapped system properties to original values");
    }

    private static ManagedOutputStream wrapStdOutStream() throws Exception {
        ManagedOutputStream managedStdOutStream = new ManagedOutputStream(LambdaRuntime.originalStdOutStream);
        System.setOut(new PrintStream(managedStdOutStream));
        StdLogger.debug("Wrapped stdout stream with managed stdout stream");

        return managedStdOutStream;
    }

    private static void unwrapStdOutStream(PrintStream originalStdOutStream) throws Exception {
        System.setOut(originalStdOutStream);
        StdLogger.debug("Unwrapped stdout stream to original value");
    }

    private static ManagedOutputStream wrapStdErrStream() throws Exception {
        ManagedOutputStream managedStdErrStream = new ManagedOutputStream(LambdaRuntime.originalStdErrStream);
        System.setErr(new PrintStream(managedStdErrStream));
        StdLogger.debug("Wrapped stderr stream with managed stderr stream");

        return managedStdErrStream;
    }

    private static void unwrapStdErrStream(PrintStream originalStdErrStream) throws Exception {
        System.setErr(originalStdErrStream);
        StdLogger.debug("Unwrapped stderr stream to original value");
    }

}
