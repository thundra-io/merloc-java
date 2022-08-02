package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.serialization.PojoSerializer;
import io.thundra.merloc.aws.lambda.core.logger.StdLogger;
import io.thundra.merloc.aws.lambda.core.utils.LambdaUtils;
import io.thundra.merloc.aws.lambda.core.utils.StringUtils;

/**
 * @author serkan
 */
public final class LambdaContextFactory {

    private static final PojoSerializer<ClientContext> clientContextSerializer =
            HandlerHelper.getSerializer(LambdaClientContext.class);
    private static  final PojoSerializer<CognitoIdentity> cognitoIdentitySerializer =
            HandlerHelper.getSerializer(LambdaCognitoIdentity.class);

    private LambdaContextFactory() {
    }

    public static Context create(String functionArn, String requestId, int timeout) {
        return new EnvVarAwareContext(functionArn, requestId, timeout, null, null);
    }

    public static Context create(String functionArn, String requestId, int timeout,
                                 ClientContext clientContext, CognitoIdentity cognitoIdentity) {
        return new EnvVarAwareContext(functionArn, requestId, timeout, clientContext, cognitoIdentity);
    }

    public static Context create(String functionArn, String requestId, int timeout,
                                 String clientContextJson, String cognitoIdentityJson) {
        ClientContext clientContext = StringUtils.hasValue(clientContextJson)
                ? clientContextSerializer.fromJson(clientContextJson)
                : null;
        CognitoIdentity cognitoIdentity = StringUtils.hasValue(cognitoIdentityJson)
                ? cognitoIdentitySerializer.fromJson(cognitoIdentityJson)
                : null;
        return new EnvVarAwareContext(functionArn, requestId, timeout, clientContext, cognitoIdentity);
    }

    private static class EnvVarAwareContext implements Context {

        private final String functionArn;
        private final String requestId;
        private final long deadline;
        private final ClientContext clientContext;
        private final CognitoIdentity cognitoIdentity;

        private EnvVarAwareContext(String functionArn, String requestId, int timeout,
                                   ClientContext clientContext, CognitoIdentity cognitoIdentity) {
            this.functionArn = functionArn;
            this.requestId = requestId;
            this.deadline = timeout < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;
            this.clientContext = clientContext;
            this.cognitoIdentity = cognitoIdentity;
        }

        @Override
        public String getAwsRequestId() {
            return requestId;
        }

        @Override
        public String getInvokedFunctionArn() {
            return functionArn;
        }

        @Override
        public String getFunctionName() {
            return LambdaUtils.getEnvVar("AWS_LAMBDA_FUNCTION_NAME");
        }

        @Override
        public String getFunctionVersion() {
            return LambdaUtils.getEnvVar("AWS_LAMBDA_FUNCTION_VERSION");
        }

        @Override
        public String getLogGroupName() {
            return LambdaUtils.getEnvVar("AWS_LAMBDA_LOG_GROUP_NAME");
        }

        @Override
        public String getLogStreamName() {
            return LambdaUtils.getEnvVar("AWS_LAMBDA_LOG_STREAM_NAME");
        }

        @Override
        public int getMemoryLimitInMB() {
            String memorySize = LambdaUtils.getEnvVar("AWS_LAMBDA_FUNCTION_MEMORY_SIZE");
            return Integer.parseInt(memorySize);
        }

        @Override
        public int getRemainingTimeInMillis() {
            if (deadline == Long.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else {
                return (int) (deadline - System.currentTimeMillis());
            }
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String msg) {
                    StdLogger.info(msg);
                }

                @Override
                public void log(byte[] msg) {
                    log(new String(msg));
                }
            };
        }

        @Override
        public ClientContext getClientContext() {
            return clientContext;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return cognitoIdentity;
        }

    }

}
