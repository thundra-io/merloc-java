package io.thundra.merloc.common.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import io.thundra.merloc.common.utils.thread.ManagedThread;

/**
 * Utility class for thread related stuff.
 *
 * @author serkan
 */
public final class ThreadUtils {

    private ThreadUtils() {
    }

    public static String newThreadName(String threadName) {
        return "merloc-" + threadName;
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

    public static ThreadFactory newThreadFactory(String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return ThreadUtils.newThread(runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

    public static ThreadFactory newThreadFactory(ThreadGroup threadGroup, String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return ThreadUtils.newThread(threadGroup, runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

    public static ThreadFactory newDaemonThreadFactory(String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return ThreadUtils.newDaemonThread(runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

    public static ThreadFactory newDaemonThreadFactory(ThreadGroup threadGroup, String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                return ThreadUtils.newDaemonThread(threadGroup, runnable, threadNamePrefix + "-" + threadCount.getAndIncrement());
            }
        };
    }

}
