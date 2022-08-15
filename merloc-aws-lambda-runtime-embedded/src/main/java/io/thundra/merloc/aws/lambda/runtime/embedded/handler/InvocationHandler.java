package io.thundra.merloc.aws.lambda.runtime.embedded.handler;

import java.io.IOException;

/**
 * @author serkan
 */
public interface InvocationHandler {

    void start() throws IOException;
    void stop() throws IOException;

}
