package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome;

/**
 * @author serkan
 */
public interface PhoneHomeService {

    void runtimeUp(long startTime);
    void runtimeDown(long startTime, long finishTime);

}
