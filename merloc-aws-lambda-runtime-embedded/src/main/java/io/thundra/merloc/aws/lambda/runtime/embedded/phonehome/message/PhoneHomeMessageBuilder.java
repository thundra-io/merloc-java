package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message;

/**
 * @author serkan
 */
public interface PhoneHomeMessageBuilder<M extends PhoneHomeMessage> {

    M build();

}
