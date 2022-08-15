package io.thundra.merloc.aws.lambda.runtime.embedded.watcher;

import java.nio.file.WatchEvent;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author serkan
 */
public enum FileChangeType {

    CREATED,
    MODIFIED,
    DELETED;

    static FileChangeType of(WatchEvent.Kind kind) {
        if (kind == ENTRY_CREATE) {
            return CREATED;
        } else if (kind == ENTRY_MODIFY) {
            return MODIFIED;
        } else if (kind == ENTRY_DELETE) {
            return DELETED;
        } else {
            return null;
        }
    }

}
