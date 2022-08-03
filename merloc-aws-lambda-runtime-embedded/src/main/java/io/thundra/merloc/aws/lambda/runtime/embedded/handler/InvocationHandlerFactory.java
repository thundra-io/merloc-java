package io.thundra.merloc.aws.lambda.runtime.embedded.handler;

import io.thundra.merloc.aws.lambda.runtime.embedded.InvocationExecutor;
import io.thundra.merloc.aws.lambda.runtime.embedded.handler.http.HttpInvocationHandler;

/**
 * @author serkan
 */
public final class InvocationHandlerFactory {

    private InvocationHandlerFactory() {
    }

    public static InvocationHandler create(InvocationExecutor invocationExecutor) {
        return new HttpInvocationHandler(invocationExecutor);
    }

}
