package io.thundra.merloc.aws.lambda.runtime.embedded.utils;

import io.thundra.merloc.aws.lambda.runtime.embedded.utils.executor.ManagedScheduledThreadPoolExecutor;
import io.thundra.merloc.aws.lambda.runtime.embedded.utils.executor.ManagedThreadPoolExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for {@link Executor} and {@link ExecutorService} related stuffs.
 *
 * @author serkan
 */
public final class ExecutorUtils {

    private static final long DEFAULT_KEEP_ALIVE_TIME_IN_SECS = 60;

    public static final Executor SYNC_EXECUTOR = (r) -> r.run();

    private ExecutorUtils() {
    }

    public static ExecutorService newFixedExecutorService(int nThreads, String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                ThreadUtils.newDaemonThreadFactory(threadNamePrefix));
    }

    public static ExecutorService newFixedExecutorService(ThreadGroup threadGroup,
                                                          int nThreads, String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                ThreadUtils.newDaemonThreadFactory(threadGroup, threadNamePrefix));
    }

    public static ExecutorService newCachedExecutorService(String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(0, Integer.MAX_VALUE,
                DEFAULT_KEEP_ALIVE_TIME_IN_SECS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadUtils.newDaemonThreadFactory(threadNamePrefix));
    }

    public static ExecutorService newCachedExecutorService(ThreadGroup threadGroup,
                                                           String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(0, Integer.MAX_VALUE,
                DEFAULT_KEEP_ALIVE_TIME_IN_SECS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadUtils.newDaemonThreadFactory(threadGroup, threadNamePrefix));
    }

    public static ExecutorService newMaxSizedExecutorService(int maxSize, String threadNamePrefix) {
        return newMaxSizedExecutorService(
                Runtime.getRuntime().availableProcessors(), maxSize, threadNamePrefix);
    }

    public static ExecutorService newMaxSizedExecutorService(ThreadGroup threadGroup,
                                                             int maxSize, String threadNamePrefix) {
        return newMaxSizedExecutorService(threadGroup,
                Runtime.getRuntime().availableProcessors(), maxSize, threadNamePrefix);
    }

    public static ExecutorService newMaxSizedExecutorService(int nThread, int maxSize, String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(
                nThread,
                nThread,
                DEFAULT_KEEP_ALIVE_TIME_IN_SECS, TimeUnit.SECONDS,
                new LinkedBlockingQueue(maxSize),
                ThreadUtils.newDaemonThreadFactory(threadNamePrefix));
    }

    public static ExecutorService newMaxSizedExecutorService(ThreadGroup threadGroup,
                                                             int nThread, int maxSize, String threadNamePrefix) {
        return new ManagedThreadPoolExecutor(
                nThread,
                nThread,
                DEFAULT_KEEP_ALIVE_TIME_IN_SECS, TimeUnit.SECONDS,
                new LinkedBlockingQueue(maxSize),
                ThreadUtils.newDaemonThreadFactory(threadGroup, threadNamePrefix));
    }

    public static ScheduledExecutorService newScheduledExecutorService(String threadNamePrefix) {
        return new ManagedScheduledThreadPoolExecutor(
                1, ThreadUtils.newDaemonThreadFactory(threadNamePrefix));
    }

    public static ScheduledExecutorService newScheduledExecutorService(ThreadGroup threadGroup,
                                                                       String threadNamePrefix) {
        return new ManagedScheduledThreadPoolExecutor(
                1, ThreadUtils.newDaemonThreadFactory(threadGroup, threadNamePrefix));
    }

    public static ScheduledExecutorService newScheduledExecutorService(int corePoolSize, String threadNamePrefix) {
        return new ManagedScheduledThreadPoolExecutor(
                corePoolSize, ThreadUtils.newDaemonThreadFactory(threadNamePrefix));
    }

    public static ScheduledExecutorService newScheduledExecutorService(ThreadGroup threadGroup,
                                                                       int corePoolSize, String threadNamePrefix) {
        return new ManagedScheduledThreadPoolExecutor(
                corePoolSize, ThreadUtils.newDaemonThreadFactory(threadGroup, threadNamePrefix));
    }

}
