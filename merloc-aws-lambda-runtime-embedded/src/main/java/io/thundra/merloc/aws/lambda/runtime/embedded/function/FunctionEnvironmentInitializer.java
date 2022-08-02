package io.thundra.merloc.aws.lambda.runtime.embedded.function;

import io.thundra.merloc.aws.lambda.runtime.embedded.domain.FunctionEnvironmentInfo;

import java.io.Serializable;

/**
 * @author serkan
 */
public interface FunctionEnvironmentInitializer<C> extends Serializable {

    default C beforeInit(FunctionEnvironmentInfo functionEnvironmentInfo) {
        return null;
    }

    default void afterInit(FunctionEnvironmentInfo functionEnvironmentInfo, C context) {
    }

}
