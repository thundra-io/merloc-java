package io.thundra.merloc.aws.lambda.runtime.embedded.function;

import io.thundra.merloc.aws.lambda.runtime.embedded.ManagedEnvironmentVariables;
import io.thundra.merloc.aws.lambda.runtime.embedded.ManagedSystemProperties;
import io.thundra.merloc.aws.lambda.runtime.embedded.exception.HandlerExecutionException;
import io.thundra.merloc.aws.lambda.runtime.embedded.io.ManagedOutputStream;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ExceptionUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author serkan
 */
public class FunctionEnvironment {

    final ManagedOutputStream managedStdOutStream;
    final ManagedOutputStream managedStdErrStream;
    final ManagedEnvironmentVariables managedEnvVars;
    final ManagedSystemProperties managedSysProps;
    final String functionArn;
    final String functionName;
    final String functionVersion;
    final int functionMemorySize;
    final String originalHandlerName;
    final long lastModified;
    final FunctionEnvironmentClassLoader classLoader;
    final Object handler;
    final Method handleRequestMethod;
    final Method contextFactoryMethod;
    final ExecutorService executorService;
    final Map<String, String> envVars;
    final Properties sysProps;
    final AtomicReference<String> currentRequestId;
    int maxMemoryUsed = -1;

    public FunctionEnvironment(ManagedOutputStream managedStdOutStream,
                               ManagedOutputStream managedStdErrStream,
                               ManagedEnvironmentVariables managedEnvVars,
                               ManagedSystemProperties managedSysProps,
                               String functionArn, String functionName,
                               String functionVersion, int functionMemorySize,
                               String originalHandlerName, long lastModified,
                               FunctionEnvironmentClassLoader classLoader, Object handler,
                               Method handleRequestMethod, Method contextFactoryMethod,
                               ExecutorService executorService,
                               Map<String, String> envVars, Properties sysProps,
                               AtomicReference<String> currentRequestId) {
        this.managedStdOutStream = managedStdOutStream;
        this.managedStdErrStream = managedStdErrStream;
        this.managedEnvVars = managedEnvVars;
        this.managedSysProps = managedSysProps;
        this.functionArn = functionArn;
        this.functionName = functionName;
        this.functionVersion = functionVersion;
        this.functionMemorySize = functionMemorySize;
        this.originalHandlerName = originalHandlerName;
        this.lastModified = lastModified;
        this.classLoader = classLoader;
        this.handler = handler;
        this.handleRequestMethod = handleRequestMethod;
        this.contextFactoryMethod = contextFactoryMethod;
        this.executorService = executorService;
        this.envVars = envVars;
        this.sysProps = sysProps;
        this.currentRequestId = currentRequestId;
    }

    public String getFunctionArn() {
        return functionArn;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getFunctionVersion() {
        return functionVersion;
    }

    public int getFunctionMemorySize() {
        return functionMemorySize;
    }

    public String getOriginalHandlerName() {
        return originalHandlerName;
    }

    public long getLastModified() {
        return lastModified;
    }

    public Map<String, String> getEnvironmentVariables() {
        return envVars;
    }

    public Properties getSystemProperties() {
        return sysProps;
    }

    public boolean hasLoadedClass(String className) {
        return classLoader.hasLoadedClass(className);
    }

    public String getCurrentRequestId() {
        return currentRequestId.get();
    }

    public void close() {
        StdLogger.debug(String.format("Closing function environment for function %s ...", functionName));

        if (handler instanceof Closeable) {
            try {
                StdLogger.debug(String.format("Closing handler (%s) for function %s ...", handler, functionName));
                ((Closeable) handler).close();
                StdLogger.debug(String.format("Closed handler (%s) for function %s", handler, functionName));
            } catch (Throwable t) {
                StdLogger.error(
                        String.format("Unable to close handler (%s) for function %s", handler, functionName), t);
            }
        }

        StdLogger.debug(String.format("Shutting down executor service for function %s ...", functionName));
        executorService.shutdown();
        StdLogger.debug(String.format("Shut down executor service for function %s", functionName));

        StdLogger.debug(String.format("Closed function environment for function %s", functionName));
    }

    public Object createContext(String requestId, int timeout,
                                String clientContextJson, String cognitoIdentityJson) throws Exception {
        return contextFactoryMethod.invoke(
                null, functionArn, requestId, timeout, clientContextJson, cognitoIdentityJson);
    }

    private void updateEnvVars(Map<String, String> currentEnvVars) {
        Map<String, String> existingEnvVars = managedEnvVars.getThreadGroupAwareEnvVars();
        if (existingEnvVars != null) {
            FunctionEnvironmentManager.ENV_VARS_TO_REMOVE.forEach((e -> existingEnvVars.remove(e)));
            FunctionEnvironmentManager.ENV_VARS_TO_REMOVE.forEach((e -> currentEnvVars.remove(e)));
            FunctionEnvironmentManager.ENV_VARS_TO_UPDATE.forEach((e -> {
                String value = currentEnvVars.get(e);
                if (value != null) {
                    existingEnvVars.put(e, value);
                } else {
                    existingEnvVars.remove(e);
                }
            }));
        }
    }

    public void execute(InputStream requestStream, OutputStream responseStream,
                        Object context, String requestId, Map<String, String> envVars) throws Exception {
        StdLogger.debug(String.format("Executing function %s ...", functionName));
        long start = System.nanoTime();
        Future future = executorService.submit(() -> {
            try {
                currentRequestId.set(requestId);
                updateEnvVars(envVars);

                try {
                    String startMessage = String.format("START RequestId: %s Version: %s\n", requestId, functionVersion);
                    managedStdOutStream.write(startMessage.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    StdLogger.error("Unable to print request start message", e);
                }

                StdLogger.debug(String.format("Invoking handler (%s) for function %s ...", handler, functionName));
                handleRequestMethod.invoke(handler, requestStream, responseStream, context);
                StdLogger.debug(String.format("Invoked handler (%s) for function %s", handler, functionName));
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    t = ((InvocationTargetException) t).getTargetException();
                }
                StdLogger.error(String.format(
                        "Failed invocation of handler (%s) for function %s", handler, functionName), t);
                ExceptionUtils.sneakyThrow(t);
            } finally {
                long finish = System.nanoTime();
                double duration = ((double) finish - (double) start) / 1_000_000;
                long billedDuration = (long) Math.ceil(duration);
                try {
                    String endMessage = String.format("END RequestId: %s\n", requestId, functionVersion);
                    managedStdOutStream.write(endMessage.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    StdLogger.error("Unable to print request end message", e);
                }

                try {
                    MemoryMXBean memBean = ManagementFactory.getMemoryMXBean() ;
                    MemoryUsage heapMemoryUsage = memBean.getHeapMemoryUsage();
                    int maxMemoryMB = (int) (heapMemoryUsage.getMax() / 1024 / 1024);
                    int usedMemoryMB = (int) (heapMemoryUsage.getUsed() / 1024 / 1024);
                    maxMemoryUsed = Math.max(maxMemoryUsed, usedMemoryMB);

                    String reportMessage =
                            String.format(
                                    "REPORT RequestId: %s Duration: %.2f ms" +
                                            "\tBilled Duration: %d ms" +
                                            "\tMemory Size: %d MB" +
                                            "\tMax Memory Used: %d MB\n",
                                    requestId, duration, billedDuration, maxMemoryMB, maxMemoryUsed);
                    managedStdOutStream.write(reportMessage.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    StdLogger.error("Unable to print request report message", e);
                }
            }
        });
        try {
            future.get();
            StdLogger.debug(String.format("Executed function %s", functionName));
        } catch (Throwable t) {
            if (t instanceof ExecutionException) {
                t = t.getCause();
            }
            StdLogger.error(String.format("Failed execution of function %s", functionName), t);
            throw new HandlerExecutionException(t);
        }
    }

    @Override
    public String toString() {
        return "FunctionEnvironment{" +
                "functionArn='" + functionArn + '\'' +
                ", functionName='" + functionName + '\'' +
                '}';
    }

}
