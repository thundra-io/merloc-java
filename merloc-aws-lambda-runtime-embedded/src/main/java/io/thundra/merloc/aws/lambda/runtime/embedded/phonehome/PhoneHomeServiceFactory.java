package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome;

import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.impl.PhoneHomeServiceImpl;

/**
 * @author serkan
 */
public final class PhoneHomeServiceFactory {

    private static final PhoneHomeService INSTANCE = new PhoneHomeServiceImpl();

    private PhoneHomeServiceFactory() {
    }

    public static PhoneHomeService get() {
        return INSTANCE;
    }

}
