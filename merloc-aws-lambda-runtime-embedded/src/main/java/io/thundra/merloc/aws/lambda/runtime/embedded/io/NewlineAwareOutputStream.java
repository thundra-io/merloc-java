package io.thundra.merloc.aws.lambda.runtime.embedded.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author serkan
 */
public class NewlineAwareOutputStream extends OutputStream {

    protected final OutputStream delegate;

    public NewlineAwareOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    public void onNewLine() throws IOException {
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

}
