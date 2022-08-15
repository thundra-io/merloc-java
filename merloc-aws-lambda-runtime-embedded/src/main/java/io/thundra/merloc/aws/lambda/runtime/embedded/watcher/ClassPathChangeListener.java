package io.thundra.merloc.aws.lambda.runtime.embedded.watcher;

/**
 * @author serkan
 */
public interface ClassPathChangeListener {

    void onChange(FileChangeEvent fileChangeEvent);

}
