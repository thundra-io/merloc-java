package io.thundra.merloc.aws.lambda.runtime.embedded.utils.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author serkan
 */
public class ManagedThreadPoolExecutor
        extends ThreadPoolExecutor
        implements ThundraExecutor {

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                                     RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

}
