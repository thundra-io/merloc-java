package io.thundra.merloc.aws.lambda.runtime.embedded.utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author serkan
 */
public final class ClassLoaderUtils {

    private ClassLoaderUtils() {
    }

    public static Collection<URL> fromClassLoader(ClassLoader classLoader) {
        List<URL> urls = new ArrayList<>();
        for (URL url : urlsFromClassLoader(classLoader)) {
            urls.add(url);
        }
        return urls;
    }

    private static URL[] urlsFromClassLoader(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader) {
            return ((URLClassLoader) classLoader).getURLs();
        }
        return Stream.of(ManagementFactory.getRuntimeMXBean().getClassPath().split(File.pathSeparator))
                .map(ClassLoaderUtils::toURL).toArray(URL[]::new);
    }

    private static URL toURL(String classPathEntry) {
        try {
            return new File(classPathEntry).toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("URL could not be created from '" + classPathEntry + "'", ex);
        }
    }

}
