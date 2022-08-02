package io.thundra.merloc.aws.lambda.runtime.embedded.watcher;

import io.thundra.merloc.aws.lambda.core.logger.StdLogger;
import io.thundra.merloc.aws.lambda.runtime.embedded.utils.ExecutorUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author serkan
 */
public class ClassPathWatcher {

    private static final int MAX_EVENT_BUFFER_SIZE = 10000;

    private final WatchService watcher;
    private final ClassPathChangeListener classPathChangeListener;
    private final Map<WatchKey, Path> keys;
    private final BlockingQueue<FileChangeEvent> eventBuffer = new LinkedBlockingQueue<>(MAX_EVENT_BUFFER_SIZE);
    private final boolean recursive;
    private volatile boolean running = false;

    private final ExecutorService watcherExecutorService =
            ExecutorUtils.newFixedExecutorService(1, "lambda-runtime-classpath-watcher");
    private final ExecutorService publisherExecutorService =
            ExecutorUtils.newFixedExecutorService(1, "lambda-runtime-classpath-notification-event-publisher");

    /**
     * Creates a {@link WatchService} and registers the given {@link URL url}s.
     */
    public ClassPathWatcher(Collection<URL> urls,
                            ClassPathChangeListener classPathChangeListener,
                            boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.classPathChangeListener = classPathChangeListener;
        // No need to use CHM as it is not accessed by multiple threads at runtime (only initialized here once)
        this.keys = new HashMap<>();
        this.recursive = recursive;
        for (URL url : urls) {
            try {
                Path path = Paths.get(url.toURI());
                if (recursive) {
                    StdLogger.debug(String.format("Scanning for registration to watch: %s ...", path));
                    registerAll(path);
                } else {
                    register(path);
                }
            } catch (URISyntaxException e) {
                StdLogger.error(String.format("Unable to get path from URL: %s", url), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Registers the given directory with the {@link WatchService}.
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        Path prev = keys.get(key);
        if (prev == null) {
            StdLogger.debug(String.format("Registered for watching: %s", dir));
        } else {
            if (!dir.equals(prev)) {
                StdLogger.debug(String.format("Registration updated form watching: %s -> %s", prev, dir));
            }
        }
        keys.put(key, dir);
    }

    /**
     * Registers the given directory, and all its sub-directories, with the {@link WatchService}.
     */
    private void registerAll(final Path start) throws IOException {
        // Register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private class Watcher implements Runnable {

        @Override
        public void run() {
            while (running) {
                // Wait for key to be signalled
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException x) {
                    return;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    StdLogger.debug("WatchKey not recognized!");
                    continue;
                }

                for (WatchEvent<?> event: key.pollEvents()) {
                    WatchEvent.Kind kind = event.kind();

                    // TODO How to handle overflow
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    // Context for directory entry event is the file name of entry
                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path child = dir.resolve(name);

                    // If directory is created, and watching recursively.
                    // Then register it and its sub-directories.
                    if (recursive && (kind == ENTRY_CREATE)) {
                        try {
                            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                                registerAll(child);
                            }
                        } catch (IOException e) {
                            StdLogger.error(String.format(
                                    "Error occurred while registering directory to watch: %s", child));
                        }
                    }

                    StdLogger.debug(String.format(
                            "Detected file system change: change type=%s, file=%s",
                            event.kind().name(), child));

                    FileChangeType fileChangeType = FileChangeType.of(kind);
                    if (fileChangeType != null) {
                        FileChangeEvent fileChangeEvent = new FileChangeEvent(child.toFile(), fileChangeType);
                        eventBuffer.offer(fileChangeEvent);
                    }
                }

                // Reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    // All directories are inaccessible
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        }

    }

    private class Publisher implements Runnable {

        @Override
        public void run() {
            while (running) {
                try {
                    FileChangeEvent event = eventBuffer.poll(5, TimeUnit.SECONDS);
                    if (event != null) {
                        classPathChangeListener.onChange(event);
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable e) {
                    StdLogger.error("Error occurred while publishing classpath file change events", e);
                }
            }
        }

    }

    /**
     * Checks whether watcher is running.
     * @return <code>true</code> if the watcher is running, <code>false</code> otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Starts watching given {@link URL url}s.
     */
    public synchronized void start() {
        if (!running) {
            running = true;
            publisherExecutorService.submit(new Publisher());
            watcherExecutorService.submit(new Watcher());
        }
    }

    /**
     * Stops watching given {@link URL url}s.
     */
    public synchronized void stop() {
        if (running) {
            running = false;
            watcherExecutorService.shutdownNow();
            publisherExecutorService.shutdownNow();
            eventBuffer.clear();
        }
    }

}
