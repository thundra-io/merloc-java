package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import com.amazonaws.services.lambda.runtime.serialization.factories.GsonFactory;
import com.amazonaws.services.lambda.runtime.serialization.factories.JacksonFactory;
import io.thundra.merloc.aws.lambda.core.utils.LambdaUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author serkan
 */
public final class HandlerHelper {

    private static final EnumMap<Platform, Map<Type, PojoSerializer<Object>>> typeCache = new EnumMap<>(Platform.class);

    private HandlerHelper() {
    }

    public static Type[] getRequestAndResponseTypes(RequestHandler handler) {
        Class<? extends RequestHandler> handlerClass = handler.getClass();

        Type[] ifaceParams = findInterfaceParameters(handlerClass);
        if (ifaceParams == null) {
            throw new IllegalArgumentException(
                    "Class " + handlerClass.getName() + " does not implement 'RequestHandler' " +
                            "with concrete type parameters");
        }
        if (ifaceParams.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid class signature for RequestHandler. " +
                            "Expected two generic types, got " + ifaceParams.length);
        }

        for (int i = 0; i < ifaceParams.length; i++) {
            Type t = ifaceParams[i];
            if (t instanceof TypeVariable) {
                Type[] bounds = ((TypeVariable)t).getBounds();
                boolean foundBound = false;
                if (bounds != null) {
                    for (int j = 0; j < bounds.length; j++) {
                        Type bound = bounds[j];
                        if (!Object.class.equals(bound)) {
                            foundBound = true;
                            break;
                        }
                    }
                }
                if (!foundBound) {
                    throw new IllegalArgumentException(
                            "Class " + handlerClass.getName() + " does not implement RequestHandler " +
                                    "with concrete type parameters: parameter " + t + " has no upper bound");
                }
            }
        }

        return ifaceParams;
    }

    private static Type[] findInterfaceParameters(Class<?> clazz) {
        LinkedList<ClassContext> clazzes = new LinkedList();
        clazzes.addFirst(new ClassContext(clazz, (Type[]) null));

        while (!clazzes.isEmpty()) {
            ClassContext curContext = clazzes.removeLast();
            Type[] interfaces = curContext.clazz.getGenericInterfaces();

            for(int i = 0; i < interfaces.length; i++) {
                Type type = interfaces[i];
                if (type instanceof ParameterizedType) {
                    ParameterizedType candidate = (ParameterizedType)type;
                    Type rawType = candidate.getRawType();
                    if (!(rawType instanceof Class)) {
                        //System.err.println("Raw type is not a class: " + rawType);
                    } else {
                        Class<?> rawClass = (Class)rawType;
                        if (RequestHandler.class.isAssignableFrom(rawClass)) {
                            return (new ClassContext(candidate, curContext)).actualTypeArguments;
                        }
                        clazzes.addFirst(new ClassContext(candidate, curContext));
                    }
                } else if (type instanceof Class) {
                    clazzes.addFirst(new ClassContext((Class)type, curContext));
                } else {
                    //System.err.println("Unexpected type class " + type.getClass().getName());
                }
            }

            Type superClass = curContext.clazz.getGenericSuperclass();
            if (superClass instanceof ParameterizedType) {
                clazzes.addFirst(new ClassContext((ParameterizedType)superClass, curContext));
            } else if (superClass != null) {
                clazzes.addFirst(new ClassContext((Class)superClass, curContext));
            }
        }

        return null;
    }

    public static int checkRequestHandlerMethod(Method handlerMethod) {
        int parameterCount = -1;
        if (handlerMethod != null) {
            Parameter[] parameters = handlerMethod.getParameters();
            parameterCount = parameters.length;
            if (parameters.length != 1 && parameters.length != 2) {
                throw new IllegalArgumentException(
                        "Handler method must have either 1 (request) or 2 (input and context) parameters");
            }
            if (parameters.length == 1) {
                if (parameters[0].getType().equals(Context.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 1 parameter, it must be input");
                }
            }
            if (parameters.length == 2) {
                if (parameters[0].getType().equals(Context.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 2 parameters, first one must be input");
                }
                if (!parameters[1].getType().equals(Context.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 2 parameters, second one must be context");
                }
            }
        }
        return parameterCount;
    }

    public static int checkRequestStreamHandlerMethod(Method handlerMethod) {
        int parameterCount = -1;
        if (handlerMethod != null) {
            Parameter[] parameters = handlerMethod.getParameters();
            parameterCount = parameters.length;
            if (parameters.length != 2 && parameters.length != 3) {
                throw new IllegalArgumentException(
                        "Handler method must have either " +
                                "2 (input stream, output stream) or " +
                                "3 (input stream, output stream and context) parameters");
            }
            if (parameters.length == 2) {
                if (!parameters[0].getType().equals(InputStream.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 2 parameters, first one must be input stream");
                }
                if (!parameters[1].getType().equals(OutputStream.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 2 parameters, second one must be output stream");
                }
            }
            if (parameters.length == 3) {
                if (!parameters[0].getType().equals(InputStream.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 3 parameters, first one must be input stream");
                }
                if (!parameters[1].getType().equals(OutputStream.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 3 parameters, second one must be output stream");
                }
                if (!parameters[2].getType().equals(Context.class)) {
                    throw new IllegalArgumentException(
                            "When handler method takes 3 parameters, third one must be context");
                }
            }
        }
        return parameterCount;
    }

    private static final class ClassContext {

        private final Class<?> clazz;
        private final Type[] actualTypeArguments;
        private TypeVariable[] typeParameters;

        private ClassContext(Class<?> clazz, Type[] actualTypeArguments) {
            this.clazz = clazz;
            this.actualTypeArguments = actualTypeArguments;
        }

        private ClassContext(Class<?> clazz, ClassContext curContext) {
            this.typeParameters = clazz.getTypeParameters();
            if (this.typeParameters.length != 0 && curContext.actualTypeArguments != null) {
                Type[] types = new Type[this.typeParameters.length];
                for (int i = 0; i < types.length; i++) {
                    types[i] = curContext.resolveTypeVariable(this.typeParameters[i]);
                }
                this.clazz = clazz;
                this.actualTypeArguments = types;
            } else {
                this.clazz = clazz;
                this.actualTypeArguments = null;
            }

        }

        private ClassContext(ParameterizedType type, ClassContext curContext) {
            Type[] types = type.getActualTypeArguments();

            for (int i = 0; i < types.length; i++) {
                Type t = types[i];
                if (t instanceof TypeVariable) {
                    types[i] = curContext.resolveTypeVariable((TypeVariable)t);
                }
            }

            Type t = type.getRawType();
            if (t instanceof Class) {
                this.clazz = (Class)t;
            } else {
                if (!(t instanceof TypeVariable)) {
                    throw new RuntimeException("Type " + t + " is of unexpected type " + t.getClass());
                }
                this.clazz = (Class)((TypeVariable)t).getGenericDeclaration();
            }

            this.actualTypeArguments = types;
        }

        private Type resolveTypeVariable(TypeVariable t) {
            TypeVariable[] variables = getTypeParameters();

            for (int i = 0; i < variables.length; i++) {
                if (t.getName().equals(variables[i].getName())) {
                    return (Type) (actualTypeArguments == null ? variables[i] : actualTypeArguments[i]);
                }
            }

            return t;
        }

        private TypeVariable[] getTypeParameters() {
            if (typeParameters == null) {
                typeParameters = clazz.getTypeParameters();
            }

            return typeParameters;
        }
    }

    private enum Platform {
        ANDROID,
        IOS,
        UNKNOWN
    }

    private static Platform getPlatform(Context context) {
        ClientContext cc = context.getClientContext();
        if (cc == null) {
            return Platform.UNKNOWN;
        }

        Map<String, String> env = cc.getEnvironment();
        if (env == null) {
            return Platform.UNKNOWN;
        }

        String platform = env.get("platform");
        if (platform == null) {
            return Platform.UNKNOWN;
        }

        if ("Android".equalsIgnoreCase(platform)) {
            return Platform.ANDROID;
        } else if ("iPhoneOS".equalsIgnoreCase(platform)) {
            return Platform.IOS;
        } else {
            return Platform.UNKNOWN;
        }
    }

    private static PojoSerializer<Object> getSerializer(Platform platform, Type type) {
        // if serializing a Class that is a Lambda supported event, use Jackson with customizations
        if (type instanceof Class) {
            Class<Object> clazz = ((Class)type);
            if (LambdaEventSerializers.isLambdaSupportedEvent(clazz.getName())) {
                return LambdaEventSerializers.serializerFor(clazz, LambdaUtils.class.getClassLoader());
            }
        }
        // else platform dependent (Android uses GSON but all other platforms use Jackson)
        switch (platform) {
            case ANDROID:
                return GsonFactory.getInstance().getSerializer(type);
            default:
                return JacksonFactory.getInstance().getSerializer(type);
        }
    }

    private static PojoSerializer<Object> getSerializerCached(Platform platform, Type type) {
        Map<Type, PojoSerializer<Object>> cache = typeCache.get(platform);
        if (cache == null) {
            cache = new HashMap<>();
            typeCache.put(platform, cache);
        }

        PojoSerializer<Object> serializer = cache.get(type);
        if (serializer == null) {
            serializer = getSerializer(platform, type);
            cache.put(type, serializer);
        }

        return serializer;
    }

    public static <T> PojoSerializer<T> getSerializer(Type type) {
        return (PojoSerializer<T>) getSerializerCached(Platform.UNKNOWN, type);
    }

    public static <T> PojoSerializer<T> getSerializer(Context context, Type type) {
        Platform platform = getPlatform(context);
        return (PojoSerializer<T>) getSerializerCached(platform, type);
    }

}
