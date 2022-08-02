package io.thundra.merloc.aws.lambda.runtime.embedded.utils;

import io.thundra.merloc.aws.lambda.runtime.embedded.utils.thread.ManagedThread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for thread related stuff.
 *
 * @author serkan
 */
public final class ThreadUtils {

    private ThreadUtils() {
    }

    public static String newThreadName(String threadName) {
        return "thundra-" + threadName;
    }

    public static Thread newThread(Runnable runnable, String threadName) {
        return new ManagedThread(runnable, newThreadName(threadName));
    }

    public static Thread newThread(ThreadGroup threadGroup, Runnable runnable, String threadName) {
        return new ManagedThread(threadGroup, runnable, newThreadName(threadName));
    }

    public static Thread newDaemonThread(Runnable runnable, String threadName) {
        Thread thread = new ManagedThread(runnable, newThreadName(threadName));
        thread.setDaemon(true);
        return thread;
    }

    public static Thread newDaemonThread(ThreadGroup threadGroup, Runnable runnable, String threadName) {
        Thread thread = new ManagedThread(threadGroup, runnable, newThreadName(threadName));
        thread.setDaemon(true);
        return thread;
    }

    public static ThreadFactory newDaemonThreadFactory(String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return newDaemonThread(runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

    public static ThreadFactory newDaemonThreadFactory(ThreadGroup threadGroup, String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return newDaemonThread(threadGroup, runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

}
