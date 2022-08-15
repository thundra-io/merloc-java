package io.thundra.merloc.aws.lambda.runtime.embedded.watcher;

import java.io.File;

/**
 * @author serkan
 */
public class FileChangeEvent {

    private final File file;
    private final FileChangeType fileChangeType;

    public FileChangeEvent(File file, FileChangeType fileChangeType) {
        this.file = file;
        this.fileChangeType = fileChangeType;
    }

    public File getFile() {
        return file;
    }

    public FileChangeType getFileChangeType() {
        return fileChangeType;
    }

}
