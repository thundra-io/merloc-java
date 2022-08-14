package io.thundra.merloc.aws.lambda.runtime.embedded.function;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author serkan
 */
public class FunctionEnvironmentClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    public FunctionEnvironmentClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public boolean hasLoadedClass(String className) {
        return findLoadedClass(className) != null;
    }

}
