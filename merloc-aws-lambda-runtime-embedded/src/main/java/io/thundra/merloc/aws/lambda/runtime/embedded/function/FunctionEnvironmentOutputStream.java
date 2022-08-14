package io.thundra.merloc.aws.lambda.runtime.embedded.function;

import io.thundra.merloc.aws.lambda.runtime.embedded.io.NewlineAwareOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * @author serkan
 */
public class FunctionEnvironmentOutputStream extends NewlineAwareOutputStream {

    private final Supplier<String> newLineHeaderSupplier;

    public FunctionEnvironmentOutputStream(OutputStream delegate, Supplier<String> newLineHeaderSupplier) {
        super(delegate);
        this.newLineHeaderSupplier = newLineHeaderSupplier;
    }

    @Override
    public void onNewLine() throws IOException {
        String newLineHeader = newLineHeaderSupplier.get();
        delegate.write(newLineHeader.getBytes(StandardCharsets.UTF_8));
    }

}
