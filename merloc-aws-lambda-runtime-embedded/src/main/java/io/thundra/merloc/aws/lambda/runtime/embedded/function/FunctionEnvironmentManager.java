package io.thundra.merloc.aws.lambda.runtime.embedded.function;

import io.thundra.merloc.aws.lambda.runtime.embedded.ManagedEnvironmentVariables;
import io.thundra.merloc.aws.lambda.runtime.embedded.ManagedSystemProperties;
import io.thundra.merloc.aws.lambda.runtime.embedded.domain.FunctionEnvironmentInfo;
import io.thundra.merloc.aws.lambda.runtime.embedded.io.ManagedOutputStream;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ClassUtils;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.ExecutorUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * @author serkan
 */
public class FunctionEnvironmentManager {

    private static final String LAMBDA_WRAPPER_HANDLER_CLASS_NAME =
            "io.thundra.merloc.aws.lambda.core.handler.WrapperLambdaHandler";
    private static final String LAMBDA_CONTEXT_FACTORY_CLASS_NAME =
            "io.thundra.merloc.aws.lambda.core.handler.LambdaContextFactory";
    private static final String LAMBDA_CONTEXT_CLASS_NAME =
            "com.amazonaws.services.lambda.runtime.Context";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

    private static final long RELOAD_DELAY_TIME = 1000;

    static final List<String> ENV_VARS_TO_REMOVE =
            Arrays.asList(
                    "LD_LIBRARY_PATH",
                    "PATH",
                    "SHLVL",
                    "AWS_XRAY_DAEMON_ADDRESS",
                    "AWS_XRAY_CONTEXT_MISSING",
                    "_AWS_XRAY_DAEMON_PORT",
                    "_AWS_XRAY_DAEMON_ADDRESS",
                    "AWS_LAMBDA_RUNTIME_API",
                    "LAMBDA_RUNTIME_DIR",
                    "TZ",
                    "LANG"
            );
    static final List<String> ENV_VARS_TO_UPDATE =
            Arrays.asList(
                    "AWS_ACCESS_KEY",
                    "AWS_ACCESS_KEY_ID",
                    "AWS_SECRET_KEY",
                    "AWS_SESSION_TOKEN",
                    "_X_AMZN_TRACE_ID"
            );

    private final ThreadGroup mainThreadGroup;
    private final ManagedEnvironmentVariables managedEnvVars;
    private final ManagedSystemProperties managedSysProps;
    private final ManagedOutputStream managedStdOutStream;
    private final ManagedOutputStream managedStdErrStream;
    private final List<byte[]> serializedInitializers;
    private final ClassLoader sysClassLoader;
    private final Collection<URL> urls;
    private final Map<String, FunctionEnvironment> functionEnvMap = new ConcurrentHashMap<>();
    private final Map<String, Lock> functionEnvLockMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reloaderExecutorService =
            ExecutorUtils.newScheduledExecutorService(4, "lambda-runtime-reloader");
    private final Map<String, Reloader> functionReloaderMap = new ConcurrentHashMap<>();

    public FunctionEnvironmentManager(ThreadGroup mainThreadGroup,
                                      ManagedEnvironmentVariables managedEnvVars,
                                      ManagedSystemProperties managedSysProps,
                                      ManagedOutputStream managedStdOutStream,
                                      ManagedOutputStream managedStdErrStream,
                                      List<byte[]> serializedInitializers,
                                      ClassLoader sysClassLoader,
                                      Collection<URL> urls) {
        this.mainThreadGroup = mainThreadGroup;
        this.managedEnvVars = managedEnvVars;
        this.managedSysProps = managedSysProps;
        this.managedStdOutStream = managedStdOutStream;
        this.managedStdErrStream = managedStdErrStream;
        this.serializedInitializers = serializedInitializers;
        this.sysClassLoader = sysClassLoader;
        this.urls = urls;
    }

    private static class FunctionEnvironmentInfoProxy {

        private final String functionArn;
        private final String functionName;
        private final Map<String, String> envVars;
        private final Properties sysProps;
        private final Class funcEnvInitializerClass;
        private final Class funcEnvInfoClass;
        private final Object funcEnvInfo;

        private FunctionEnvironmentInfoProxy(String functionArn, String functionName,
                                             Map<String, String> envVars, Properties sysProps,
                                             Class funcEnvInitializerClass, Class funcEnvInfoClass, Object funcEnvInfo) {
            this.functionArn = functionArn;
            this.functionName = functionName;
            this.envVars = envVars;
            this.sysProps = sysProps;
            this.funcEnvInitializerClass = funcEnvInitializerClass;
            this.funcEnvInfoClass = funcEnvInfoClass;
            this.funcEnvInfo = funcEnvInfo;
        }

        private Object beforeInit(Object initializer) throws Exception {
            Method beforeInitMethod =
                    funcEnvInitializerClass.getMethod("beforeInit", funcEnvInfoClass);
            StdLogger.debug(String.format(
                    "Calling before-init of environment initializer for function %s: %s ...",
                    functionName, initializer));
            Object context = beforeInitMethod.invoke(initializer, funcEnvInfo);
            StdLogger.debug(String.format(
                    "Called before-init of environment initializer for function %s: %s ...",
                    functionName, initializer));
            return context;
        }

        private void afterInit(Object initializer, Object context) throws Exception {
            Method afterInitMethod =
                    funcEnvInitializerClass.getMethod("afterInit", funcEnvInfoClass, Object.class);
            StdLogger.debug(String.format(
                    "Calling after-init of environment initializer for function %s: %s ...",
                    functionName, initializer));
            afterInitMethod.invoke(initializer, funcEnvInfo, context);
            StdLogger.debug(String.format(
                    "Called after-init of environment initializer for function %s: %s",
                    functionName, initializer));
        }

        private Map<String, String> getEnvironmentVariables() {
            return envVars;
        }

        private Properties getSystemProperties() {
            return sysProps;
        }

    }

    private FunctionEnvironmentClassLoader createFunctionEnvironmentClassLoader() {
        // Application classloader and system classloader must be sibling.
        // System classloader must not be parent of application classloader.
        FunctionEnvironmentClassLoader classLoader =
                new FunctionEnvironmentClassLoader(urls.toArray(new URL[0]), sysClassLoader.getParent());
        StdLogger.debug(String.format(
                "Created classloader (%s) for the function environment with the URLs: %s",
                classLoader, urls));
        return classLoader;
    }

    private FunctionEnvironment doCreateFunctionEnvironment(
            ManagedOutputStream managedStdOutStream,
            ManagedOutputStream managedStdErrStream,
            ManagedEnvironmentVariables managedEnvVars,
            ManagedSystemProperties managedSysProps,
            FunctionEnvironmentClassLoader classLoader,
            ExecutorService executorService, String functionArn,
            String functionName, String functionVersion, int functionMemorySize,
            String originalHandlerName, long lastModified,
            Map<String, String> envVars, Properties sysProps,
            AtomicReference<String> currentRequestId) throws Exception {
        // Init thread context classloader
        Thread.currentThread().setContextClassLoader(classLoader);

        Class handlerClass = classLoader.loadClass(LAMBDA_WRAPPER_HANDLER_CLASS_NAME);
        Object handler = handlerClass.newInstance();

        Class contextClass = classLoader.loadClass(LAMBDA_CONTEXT_CLASS_NAME);
        Method handleRequestMethod =
                handlerClass.getMethod("handleRequest", InputStream.class, OutputStream.class, contextClass);

        Class contextFactoryClass = classLoader.loadClass(LAMBDA_CONTEXT_FACTORY_CLASS_NAME);
        Method contextFactoryMethod =
                contextFactoryClass.getMethod(
                        "create", String.class, String.class, int.class,
                        String.class, String.class);

        return new FunctionEnvironment(
                managedStdOutStream, managedStdErrStream,
                managedEnvVars, managedSysProps,
                functionArn, functionName, functionVersion, functionMemorySize,
                originalHandlerName, lastModified,
                classLoader, handler,
                handleRequestMethod, contextFactoryMethod,
                executorService,
                envVars, sysProps,
                currentRequestId);
    }

    private FunctionEnvironmentInfoProxy createFunctionEnvironmentInfo(
            ClassLoader classLoader,
            String functionArn, String functionName,
            Map<String, String> envVars, Properties sysProps) throws Exception {
        Class funcEnvInitializerClass = classLoader.loadClass(FunctionEnvironmentInitializer.class.getName());
        Class funcEnvInfoClass = classLoader.loadClass(FunctionEnvironmentInfo.class.getName());
        Constructor funcEnvInfoCtor = funcEnvInfoClass.getConstructor(
                String.class, String.class, Map.class, Properties.class);
        Object funcEnvInfo = funcEnvInfoCtor.newInstance(functionArn, functionName, envVars, sysProps);
        return new FunctionEnvironmentInfoProxy(
                functionArn, functionName, envVars, sysProps,
                funcEnvInitializerClass, funcEnvInfoClass, funcEnvInfo);
    }

    public Object deserializeFunctionEnvironmentInitializer(ClassLoader classLoader, byte[] data)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(data)) {
            @Override
            public Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(desc.getName(), true, classLoader);
                } catch (Exception e) {
                }

                // Fall back (e.g. for primClasses)
                return super.resolveClass(desc);
            }
        }) {
            return inputStream.readObject();
        }
    }

    private List<Object> createInitializers(ClassLoader classLoader, String functionName) throws Exception {
        List<Object> initializers = new ArrayList<>(serializedInitializers.size());

        // Instantiate initializers
        for (byte[] data : serializedInitializers) {
            Object initializer =
                    deserializeFunctionEnvironmentInitializer(classLoader, data);
            StdLogger.debug(String.format(
                    "Created function environment initializer for function %s: %s",
                    functionName, initializer));
            initializers.add(initializer);
        }

        return initializers;
    }

    private String extractHandlerRootPath(FunctionEnvironmentClassLoader classLoader,
                                          String originalHandlerName) {
        String originalHandlerClassFileName = ClassUtils.toClassFilePath(originalHandlerName);
        URL originalHandlerRootPathURL = classLoader.getResource(originalHandlerClassFileName);
        if (originalHandlerRootPathURL != null) {
            String originalHandlerRootPath = originalHandlerRootPathURL.getPath();

            int idx1 = originalHandlerRootPath.indexOf(":/");
            if (idx1 >= 0) {
                // "idx1 + 1" (instead of "idx1 + 2") because we want to keep "/" after ":" in the root path
                originalHandlerRootPath = originalHandlerRootPath.substring(idx1 + 1);
            }

            int idx2 = originalHandlerRootPath.indexOf("!");
            if (idx2 >= 0) {
                originalHandlerRootPath = originalHandlerRootPath.substring(0, idx2);
            }

            if (originalHandlerRootPath.endsWith(originalHandlerClassFileName)) {
                originalHandlerRootPath =
                        originalHandlerRootPath.substring(0,
                                originalHandlerRootPath.length() - originalHandlerClassFileName.length());
            }

            File originalHandlerRootFile = new File(originalHandlerRootPath);
            if (!originalHandlerRootFile.exists()) {
                return null;
            }

            if (originalHandlerRootFile.isFile()) {
                originalHandlerRootPath = originalHandlerRootFile.getParent();
            }

            if (originalHandlerRootPath.endsWith(File.separator)) {
                originalHandlerRootPath = originalHandlerRootPath.substring(0, originalHandlerRootPath.length() - 1);
            }

            return originalHandlerRootPath;
        }
        return null;
    }

    private void normalizeEnvVars(Map<String, String> envVars,
                                  FunctionEnvironmentClassLoader classLoader, String originalHandlerName) {
        ENV_VARS_TO_REMOVE.forEach((e -> envVars.remove(e)));
        String originalHandlerRootPath = extractHandlerRootPath(classLoader, originalHandlerName);
        if (originalHandlerRootPath != null) {
            envVars.put("LAMBDA_TASK_ROOT", originalHandlerRootPath);
            envVars.put("PWD", originalHandlerRootPath);
        }
    }

    private FunctionEnvironment createFunctionEnvironment(
            Map<String, String> functionEnvVars, String functionArn,
            String functionName, String functionVersion, int functionMemorySize,
            String originalHandlerName, long lastModified) throws Exception {
        String threadGroupName = "lambda-runtime-tg-for-" + functionName;
        String executorThreadNamePrefix = "lambda-runtime-executor-for-" + functionName;
        ThreadGroup threadGroup = new ThreadGroup(mainThreadGroup, threadGroupName);
        ExecutorService executorService = ExecutorUtils.newFixedExecutorService(
                threadGroup, 1, executorThreadNamePrefix);
        StdLogger.debug(String.format(
                "Creating function environment for function %s (thread group name=%s, executor thread name prefix=%s)...",
                functionName, threadGroupName, executorThreadNamePrefix));
        try {
            Future<FunctionEnvironment> future = executorService.submit(() -> {
                FunctionEnvironmentClassLoader classLoader = createFunctionEnvironmentClassLoader();

                AtomicReference<String> currentRequestId = new AtomicReference<>();

                normalizeEnvVars(functionEnvVars, classLoader, originalHandlerName);

                Properties sysProps = new Properties();

                FunctionEnvironmentInfoProxy funcEnvInfoProxy =
                        createFunctionEnvironmentInfo(classLoader, functionArn, functionName, functionEnvVars, sysProps);

                List<Object> initializers = createInitializers(classLoader, functionName);

                // Call pre-inits
                Map<Object, Object> initializerContextMap = new HashMap<>(initializers.size());
                for (Object initializer : initializers) {
                    Object context = funcEnvInfoProxy.beforeInit(initializer);
                    if (context != null) {
                        initializerContextMap.put(initializer, context);
                    }
                }

                // TODO Find a better way to handle isolated env vars (maybe by inheritable thread local)
                // Init environment variables
                managedEnvVars.setThreadGroupAwareEnvVars(funcEnvInfoProxy.getEnvironmentVariables());
                StdLogger.debug(String.format(
                        "Set thread group aware environment variables before creating environment for function %s: %s",
                        functionName, functionEnvVars));

                // TODO Find a better way to handle isolated sys props (maybe by inheritable thread local)
                // Init system properties
                managedSysProps.setThreadGroupAwareSysProps(funcEnvInfoProxy.getSystemProperties());
                StdLogger.debug(String.format("Set thread group aware system properties for function %s: %s",
                        functionName, managedSysProps.getThreadGroupAwareSysProps()));

                Supplier<String> newLineHeaderSupplier = () -> {
                    LocalDateTime now = LocalDateTime.now();
                    return String.format(
                            "[MERLOC] %-20s | %36s %25s - ",
                            functionName,
                            currentRequestId.get() != null ? currentRequestId.get() : "",
                            now.format(DATE_TIME_FORMATTER));
                };

                // TODO Find a better way to handle isolated stdout (maybe by inheritable thread local)
                // Create function specific stdout stream which appends function name to each line
                OutputStream functionStdOutStream =
                        new FunctionEnvironmentOutputStream(
                                managedStdOutStream.getOriginal(),
                                newLineHeaderSupplier);
                managedStdOutStream.setThreadGroupAwareOutputStream(functionStdOutStream);

                // TODO Find a better way to handle isolated stderr (maybe by inheritable thread local)
                // Create function specific stderr stream which appends function name to each line
                OutputStream functionStdErrStream =
                        new FunctionEnvironmentOutputStream(
                                managedStdErrStream.getOriginal(),
                                newLineHeaderSupplier);
                managedStdErrStream.setThreadGroupAwareOutputStream(functionStdErrStream);

                try {
                    FunctionEnvironment functionEnvironment =
                            doCreateFunctionEnvironment(
                                    managedStdOutStream, managedStdErrStream,
                                    managedEnvVars, managedSysProps,
                                    classLoader, executorService,
                                    functionArn, functionName, functionVersion, functionMemorySize,
                                    originalHandlerName, lastModified,
                                    functionEnvVars, sysProps, currentRequestId);

                    // Call post-inits
                    for (Object initializer : initializers) {
                        Object context = initializerContextMap.get(initializer);
                        funcEnvInfoProxy.afterInit(initializer, context);
                    }

                    return functionEnvironment;
                } catch (Throwable t) {
                    ExceptionUtils.sneakyThrow(t);
                    return null;
                } finally {
                    // No need to clear/reset
                    // - environment variables
                    // - system properties
                    // - thread context classloader
                    // because each function runs on its own dedicated thread
                }
            });
            try {
                FunctionEnvironment functionEnvironment = future.get();
                StdLogger.debug(String.format(
                        "Created function environment for function %s (thread group name=%s, executor thread name prefix=%s)...",
                        functionName, threadGroupName, executorThreadNamePrefix));
                return functionEnvironment;
            } catch (Throwable t) {
                if (t instanceof ExecutionException) {
                    t = t.getCause();
                }
                StdLogger.error(String.format(
                        "Failed creation of function environment in sandbox for function %s (thread group name=%s, executor thread name prefix=%s)...",
                        functionName, threadGroupName, executorThreadNamePrefix));
                ExceptionUtils.sneakyThrow(t);
            }
        } catch (Throwable t) {
            StdLogger.error(String.format(
                    "Failed creation of function environment for function %s (thread group name=%s, executor thread name prefix=%s)...",
                    functionName, threadGroupName, executorThreadNamePrefix));
            executorService.shutdownNow();
            ExceptionUtils.sneakyThrow(t);
        }
        return null;
    }

    public Lock getFunctionEnvironmentLock(String functionArn) {
        Lock lock;
        Lock newLock = new ReentrantLock();
        lock = functionEnvLockMap.putIfAbsent(functionArn, newLock);
        if (lock == null) {
            lock = newLock;
        }
        return lock;
    }

    public FunctionEnvironment getOrCreateFunctionEnvironment(
            Callable<Map<String, String>> functionEnvVarsBuilder, String functionArn,
            String functionName, String functionVersion, int functionMemorySize,
            String originalHandlerName, long lastModified) throws Exception {
        StdLogger.debug(String.format("Getting or create function environment for function %s ...", functionName));

        Lock lock = getFunctionEnvironmentLock(functionArn);
        StdLogger.debug(String.format("Locking function environment for function %s ...", functionName));
        lock.lock();
        StdLogger.debug(String.format("Locked function environment for function %s", functionName));

        FunctionEnvironment functionEnv;
        try {
            functionEnv = functionEnvMap.get(functionArn);
            if (functionEnv != null && lastModified > functionEnv.getLastModified()) {
                StdLogger.debug(String.format("Detected stale function environment for function %s", functionName));
                functionEnvMap.remove(functionArn);
                functionEnv.close();
                functionEnv = null;
            }
            if (functionEnv == null) {
                StdLogger.debug(String.format("Creating new function environment for function %s ...", functionName));
                functionEnv = createFunctionEnvironment(
                        functionEnvVarsBuilder.call(),
                        functionArn, functionName, functionVersion, functionMemorySize,
                        originalHandlerName, lastModified);
                StdLogger.debug(String.format("Created new function environment for function %s", functionName));
                functionEnvMap.put(functionArn, functionEnv);
            } else {
                StdLogger.debug(String.format("Using existing function environment for function %s", functionName));
            }
        } finally {
            StdLogger.debug(String.format("Unlocking function environment for function %s ...", functionName));
            lock.unlock();
            StdLogger.debug(String.format("Unlocked function environment for function %s", functionName));
        }

        StdLogger.debug(String.format("Got or create function environment for function %s", functionName));

        return functionEnv;
    }

    public Collection<FunctionEnvironment> getEffectedFunctionEnvironments(String className) {
        List<FunctionEnvironment> functionEnvironments = new ArrayList<>();
        for (FunctionEnvironment fe : functionEnvMap.values()) {
            if (fe.hasLoadedClass(className)) {
                functionEnvironments.add(fe);
            }
        }
        return functionEnvironments;
    }

    public void reloadFunctionEnvironment(FunctionEnvironment functionEnv) throws Exception {
        long reloadDelayTime = System.currentTimeMillis() + RELOAD_DELAY_TIME;
        Reloader reloader = new Reloader(functionEnv, reloadDelayTime);
        Reloader existingReloader = functionReloaderMap.putIfAbsent(functionEnv.getFunctionArn(), reloader);
        if (existingReloader != null) {
            // Postpone reload start time as there are still new events coming.
            // Here we want to prevent reload event flood in case of rebuild project.
            existingReloader.reloadStartTime = reloadDelayTime;
        } else {
            StdLogger.debug(String.format(
                    "Scheduling reloading function environment for function %s",
                    functionEnv.getFunctionName()));
            reloaderExecutorService.schedule(reloader, RELOAD_DELAY_TIME, TimeUnit.MILLISECONDS);
        }
    }

    private class Reloader implements Runnable {

        private final FunctionEnvironment functionEnv;
        private volatile long reloadStartTime;

        public Reloader(FunctionEnvironment functionEnv, long reloadStartTime) {
            this.functionEnv = functionEnv;
            this.reloadStartTime = reloadStartTime;
        }

        @Override
        public void run() {
            long currentTime;
            // Wait until current time has passed reload start time
            while ((currentTime = System.currentTimeMillis()) < reloadStartTime) {
                try {
                    Thread.sleep(reloadStartTime - currentTime + 1);
                } catch (InterruptedException e) {
                }
            }

            String functionName = functionEnv.getFunctionName();
            String functionArn = functionEnv.getFunctionArn();
            String functionVersion = functionEnv.getFunctionVersion();
            int functionMemorySize = functionEnv.getFunctionMemorySize();
            String originalHandlerName = functionEnv.getOriginalHandlerName();
            long lastModified = System.currentTimeMillis();
            StdLogger.info(String.format("Reloading function environment for function %s ...", functionName));

            Lock lock = getFunctionEnvironmentLock(functionArn);
            StdLogger.debug(String.format("Locking function environment for function %s ...", functionName));
            lock.lock();
            StdLogger.debug(String.format("Locked function environment for function %s", functionName));
            try {
                functionEnvMap.remove(functionEnv.getFunctionArn(), functionEnv);
                functionEnv.close();

                StdLogger.debug(String.format("Creating new function environment for function %s ...", functionName));
                FunctionEnvironment newFunctionEnv =
                        createFunctionEnvironment(
                            functionEnv.getEnvironmentVariables(),
                            functionArn, functionName, functionVersion, functionMemorySize,
                            originalHandlerName, lastModified);
                StdLogger.debug(String.format("Created new function environment for function %s ...", functionName));
                functionEnvMap.put(functionEnv.getFunctionArn(), newFunctionEnv);

                StdLogger.info(String.format("Reloaded function environment for function %s", functionName));
            } catch (Throwable t) {
                StdLogger.error(String.format("Unable to reload function environment for function %s", functionName), t);
            } finally {
                StdLogger.debug(String.format("Unlocking function environment for function %s ...", functionName));
                lock.unlock();
                StdLogger.debug(String.format("Unlocked function environment for function %s", functionName));

                functionReloaderMap.remove(functionArn);
            }
        }

    }

    public void closeFunctionEnvironments() {
        Iterator<Map.Entry<String, FunctionEnvironment>> iter = functionEnvMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, FunctionEnvironment> e = iter.next();
            String functionName = e.getKey();
            FunctionEnvironment functionEnvironment = e.getValue();
            StdLogger.debug(String.format("Closing environment for function %s ...", functionName));
            try {
                functionEnvironment.close();
                StdLogger.debug(String.format("Closed environment for function %s ...", functionName));
            } catch (Throwable t) {
                StdLogger.error(String.format(
                        "Failed to close of environment for function %s ...", functionName), t);
            }
            iter.remove();
        }
    }

}
