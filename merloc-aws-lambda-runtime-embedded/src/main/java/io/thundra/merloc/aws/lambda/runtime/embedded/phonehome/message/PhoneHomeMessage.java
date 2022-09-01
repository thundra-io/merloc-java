package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message;

/**
 * @author serkan
 */
public interface PhoneHomeMessage {

    String getType();
    String getMachineHash();
    String getOsName();
    int getJvmVersion();

}
