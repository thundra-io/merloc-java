package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.aws.lambda.core.config.ConfigNames;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ClassUtils;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * @author serkan
 */
public class WrapperLambdaHandler implements RequestStreamHandler {

    private final ByteBuffer requestBuffer = ByteBuffer.allocate(3 * 1024 * 1024); // 3MB
    private final Future<RequestStreamHandler> proxyLambdaHandlerFuture;

    public WrapperLambdaHandler() {
        this.proxyLambdaHandlerFuture = getProxyLambdaHandler(() -> createProxyLambdaHandler());
    }

    protected Future<RequestStreamHandler> getProxyLambdaHandler(Supplier<RequestStreamHandler> handlerSupplier) {
        RequestStreamHandler handler = handlerSupplier.get();
        return CompletableFuture.completedFuture(handler);
    }

    private static RequestStreamHandler createProxyLambdaHandler() {
        String handlerName = ConfigManager.getConfig(ConfigNames.LAMBDA_HANDLER);
        if (StringUtils.isNullOrEmpty(handlerName)) {
            throw new IllegalArgumentException(
                    String.format(
                        "You need to specify your original handler by setting " +
                                "'%s' environment variable by its full classname",
                        ConfigManager.getEnvironmentVariableName(ConfigNames.LAMBDA_HANDLER)));
        }
        int methodIdx = handlerName.indexOf("::");
        String handlerClassName;
        String handlerMethodName;
        if (methodIdx > 0) {
            handlerClassName = handlerName.substring(0, methodIdx);
            handlerMethodName = handlerName.substring(methodIdx + 2);
        } else {
            handlerClassName = handlerName;
            handlerMethodName = null;
        }
        try {
            Class handlerClass = ClassUtils.getClassWithException(handlerClassName);
            Object handler = createHandler(handlerClass);
            if (handler instanceof RequestHandler) {
                return new ProxyLambdaRequestHandler(handler, null);
            } else if (handler instanceof RequestStreamHandler) {
                return new ProxyLambdaRequestStreamHandler(handler, null);
            } else {
                if (methodIdx > 0) {
                    List<Method> handlerMethods = new ArrayList<>();
                    for (Method method : handlerClass.getMethods()) {
                        if (method.getName().equals(handlerMethodName)) {
                            handlerMethods.add(method);
                            break;
                        }
                    }
                    StringBuilder errorBuilder = new StringBuilder();
                    for (Method handlerMethod : handlerMethods) {
                        try {
                            boolean hasReturn = !handlerMethod.getReturnType().equals(void.class);
                            if (hasReturn) {
                                return new ProxyLambdaRequestHandler(handler, handlerMethod);
                            } else {
                                return new ProxyLambdaRequestStreamHandler(handler, handlerMethod);
                            }
                        } catch (Throwable t) {
                            errorBuilder.append("- ").append(t.getMessage()).append("\n");
                        }
                    }
                    throw new IllegalArgumentException("No suitable handler method could be found\n" + errorBuilder.toString());
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Provided handler (%s) must only be sub-type one of the '%s' or '%s' interfaces",
                                    handlerClassName, RequestHandler.class.getName(), RequestStreamHandler.class.getName()));
                }
            }
        } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Unable to create request handler for given handler class " + handlerClassName, e);
        }
    }

    private static Object createHandler(Class handlerClass) throws IllegalAccessException, InstantiationException {
        return handlerClass.newInstance();
    }

    @Override
    public void handleRequest(InputStream requestStream,
                              OutputStream responseStream,
                              Context context) throws IOException {
        ByteArrayInputStream wrapperInputStream = wrapInputStream(requestStream);
        wrapperInputStream.mark(0);

        boolean continueRequest = true;
        try {
            continueRequest = onRequest(wrapperInputStream, responseStream, context);
        } catch (Throwable t) {
            StdLogger.debug("Error occurred while on request", t);
            ExceptionUtils.sneakyThrow(t);
        }

        if (continueRequest) {
            wrapperInputStream.reset();
            RequestStreamHandler proxyLambdaHandler = null;
            try {
                proxyLambdaHandler = proxyLambdaHandlerFuture.get();
            } catch (InterruptedException e) {
                throw new IOException("Unable to create handler", e);
            } catch (ExecutionException e) {
                throw new IOException("Unable to create handler", e.getCause());
            }
            if (proxyLambdaHandler != null) {
                proxyLambdaHandler.handleRequest(wrapperInputStream, responseStream, context);
            } else {
                throw new IOException("Unable to create handler");
            }
        }
    }

    private ByteArrayInputStream wrapInputStream(InputStream inputStream) throws IOException {
        requestBuffer.clear();
        while (inputStream.available() > 0) {
            requestBuffer.put((byte) inputStream.read());
        }
        byte[] data = new byte[requestBuffer.position()];
        requestBuffer.position(0);
        requestBuffer.get(data);
        return new ByteArrayInputStream(data);
    }

    protected boolean onRequest(InputStream requestStream, OutputStream responseStream, Context context) {
        return true;
    }

    public static class ProxyLambdaRequestHandler implements RequestStreamHandler {

        private final Object requestHandler;
        private final Method handlerMethod;
        private final int parameterCount;
        private final Type requestType;
        private final Type responseType;

        public ProxyLambdaRequestHandler(Object requestHandler, Method handlerMethod) {
            if (handlerMethod == null && !(requestHandler instanceof RequestHandler)) {
                throw new IllegalArgumentException(
                        "When no handler method is specified, handler must be a sub-type of " +
                                RequestHandler.class.getName());
            }
            this.requestHandler = requestHandler;
            this.handlerMethod = handlerMethod;
            this.parameterCount = HandlerHelper.checkRequestHandlerMethod(handlerMethod);
            if (handlerMethod != null) {
                this.requestType = handlerMethod.getParameterTypes()[0];
                this.responseType = handlerMethod.getReturnType();
            } else {
                Type[] types = HandlerHelper.getRequestAndResponseTypes((RequestHandler) requestHandler);
                this.requestType = types[0];
                this.responseType = types[1];
            }
        }

        @Override
        public void handleRequest(InputStream inputStream,
                                  OutputStream outputStream,
                                  Context context) throws IOException {
            try {
                PojoSerializer requestSerializer = HandlerHelper.getSerializer(context, requestType);
                PojoSerializer responseSerializer = HandlerHelper.getSerializer(context, responseType);

                Object request = requestSerializer.fromJson(inputStream);
                Object response;
                if (handlerMethod == null) {
                    response = ((RequestHandler) requestHandler).handleRequest(request, context);
                } else {
                    if (parameterCount == 1) {
                        response = handlerMethod.invoke(requestHandler, request);
                    } else {
                        response = handlerMethod.invoke(requestHandler, request, context);
                    }
                }

                responseSerializer.toJson(response, outputStream);
            } catch (Throwable t) {
                ExceptionUtils.sneakyThrow(t);
                throw new IOException("Error occurred while invoking handler", t);
            }
        }
    }

    public static class ProxyLambdaRequestStreamHandler implements RequestStreamHandler {

        private final Object requestStreamHandler;
        private final Method handlerMethod;
        private int parameterCount;

        public ProxyLambdaRequestStreamHandler(Object requestStreamHandler, Method handlerMethod) {
            if (handlerMethod == null && !(requestStreamHandler instanceof RequestStreamHandler)) {
                throw new IllegalArgumentException(
                        "When no handler method is specified, handler must be a sub-type of " +
                        RequestStreamHandler.class.getName());
            }
            this.requestStreamHandler = requestStreamHandler;
            this.handlerMethod = handlerMethod;
            this.parameterCount = HandlerHelper.checkRequestStreamHandlerMethod(handlerMethod);
        }

        @Override
        public void handleRequest(InputStream inputStream,
                                  OutputStream outputStream,
                                  Context context) throws IOException {
            try {
                if (handlerMethod == null) {
                    ((RequestStreamHandler) requestStreamHandler).handleRequest(inputStream, outputStream, context);
                } else {
                    if (parameterCount == 2) {
                        handlerMethod.invoke(requestStreamHandler, inputStream, outputStream);
                    } else {
                        handlerMethod.invoke(requestStreamHandler, inputStream, outputStream, context);
                    }
                }
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                ExceptionUtils.sneakyThrow(t);
                throw new IOException("Error occurred while invoking handler", t);
            }
        }

    }

}
