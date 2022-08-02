package io.thundra.merloc.aws.lambda.runtime.embedded.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author serkan
 */
public class ManagedOutputStream extends OutputStream {

    private static final char NEW_LINE = '\n';

    private final PrintStream original;
    private final Map<ThreadGroup, DelegateContext> threadGroupOutputStreams = new ConcurrentHashMap<>();

    public ManagedOutputStream() {
        this.original = new PrintStream(new NoOpOutputStream());
    }

    public ManagedOutputStream(PrintStream original) {
        this.original = original;
    }

    public PrintStream getOriginal() {
        return original;
    }

    private static class DelegateContext {

        private final OutputStream outputStream;
        private final ThreadLocal<Integer> latestChar = new ThreadLocal<>();

        private DelegateContext(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

    }

    private DelegateContext getDelegateContext() {
        for (ThreadGroup tg = Thread.currentThread().getThreadGroup(); tg != null; tg = tg.getParent()) {
            DelegateContext context = threadGroupOutputStreams.get(tg);
            if (context != null) {
                return context;
            }
        }
        return null;
    }

    public OutputStream getThreadGroupAwareOutputStream() {
        DelegateContext context = getDelegateContext();
        return context != null ? context.outputStream : null;
    }

    public void setThreadGroupAwareOutputStream(OutputStream outputStream) {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            threadGroupOutputStreams.put(tg, new DelegateContext(outputStream));
        }
    }

    public void clearThreadGroupAwareOutputStream() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            threadGroupOutputStreams.remove(tg);
        }
    }

    private void write(int b, DelegateContext context) throws IOException {
        if (context != null) {
            Integer latestCharValue = context.latestChar.get();
            if (latestCharValue == null || latestCharValue.byteValue() == NEW_LINE) {
                if (context.outputStream instanceof NewlineAwareOutputStream) {
                    ((NewlineAwareOutputStream) context.outputStream).onNewLine();
                }
            }
            context.outputStream.write(b);
            context.latestChar.set(b);
        } else {
            original.write(b);
        }
    }

    @Override
    public void write(int b) throws IOException {
        DelegateContext context = getDelegateContext();
        write(b, context);
    }

    @Override
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        /*
        original.write(b, off, len);
        OutputStream delegate = getDelegate();
        if (delegate != null) {
            delegate.write(b, off, len);
        }
        */
        DelegateContext context = getDelegateContext();
        for (int i = 0; i < len; i++) {
            write(b[off + i], context);
        }
    }

    @Override
    public void flush() throws IOException {
        original.flush();
        DelegateContext context = getDelegateContext();
        if (context != null) {
            context.outputStream.flush();
        }
    }

    private static class NoOpOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public void write(byte[] b) throws IOException {
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
        }

    }

}
