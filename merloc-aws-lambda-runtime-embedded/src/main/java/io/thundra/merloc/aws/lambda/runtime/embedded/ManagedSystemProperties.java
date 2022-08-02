package io.thundra.merloc.aws.lambda.runtime.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author serkan
 */
public class ManagedSystemProperties extends Properties {

    private final Properties baseSysProps;
    private final Map<ThreadGroup, Properties> threadGroupSysProps = new ConcurrentHashMap<>();

    public ManagedSystemProperties(Properties baseSysProps) {
        this.baseSysProps = new Properties();
        baseSysProps.forEach((key, value) -> {
            this.baseSysProps.setProperty((String) key, (String) value);
        });
    }

    private Properties getDelegate() {
        for (ThreadGroup tg = Thread.currentThread().getThreadGroup(); tg != null; tg = tg.getParent()) {
            Properties delegate = threadGroupSysProps.get(tg);
            if (delegate != null) {
                return delegate;
            }
        }
        return baseSysProps;
    }

    public Properties getThreadGroupAwareSysProps() {
        return getDelegate();
    }

    public void setThreadGroupAwareSysProps(Properties sysProps) {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            Properties allSysProps = new Properties();
            allSysProps.putAll(baseSysProps);
            allSysProps.putAll(sysProps);
            threadGroupSysProps.put(tg, allSysProps);
        }
    }

    public void clearThreadGroupAwareSysProps() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            threadGroupSysProps.remove(tg);
        }
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        return getDelegate().setProperty(key, value);
    }

    @Override
    public synchronized void load(Reader reader) throws IOException {
        getDelegate().load(reader);
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        getDelegate().load(inStream);
    }

    @Override
    public void save(OutputStream out, String comments) {
        getDelegate().save(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        getDelegate().store(writer, comments);
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        getDelegate().store(out, comments);
    }

    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException, InvalidPropertiesFormatException {
        getDelegate().loadFromXML(in);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        getDelegate().storeToXML(os, comment);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        getDelegate().storeToXML(os, comment, encoding);
    }

    @Override
    public String getProperty(String key) {
        return getDelegate().getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return getDelegate().getProperty(key, defaultValue);
    }

    @Override
    public Enumeration<?> propertyNames() {
        return getDelegate().propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return getDelegate().stringPropertyNames();
    }

    @Override
    public void list(PrintStream out) {
        getDelegate().list(out);
    }

    @Override
    public void list(PrintWriter out) {
        getDelegate().list(out);
    }

    @Override
    public synchronized int size() {
        return getDelegate().size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        return getDelegate().keys();
    }

    @Override
    public synchronized Enumeration<Object> elements() {
        return getDelegate().elements();
    }

    @Override
    public synchronized boolean contains(Object value) {
        return getDelegate().contains(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    @Override
    public synchronized Object get(Object key) {
        return getDelegate().get(key);
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        return getDelegate().put(key, value);
    }

    @Override
    public synchronized Object remove(Object key) {
        return getDelegate().remove(key);
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        getDelegate().putAll(t);
    }

    @Override
    public synchronized void clear() {
        getDelegate().clear();
    }

    @Override
    public synchronized Object clone() {
        return getDelegate().clone();
    }

    @Override
    public synchronized String toString() {
        return getDelegate().toString();
    }

    @Override
    public Set<Object> keySet() {
        return getDelegate().keySet();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return getDelegate().entrySet();
    }

    @Override
    public Collection<Object> values() {
        return getDelegate().values();
    }

    @Override
    public synchronized boolean equals(Object o) {
        return getDelegate().equals(o);
    }

    @Override
    public synchronized int hashCode() {
        return getDelegate().hashCode();
    }

    @Override
    public synchronized Object getOrDefault(Object key, Object defaultValue) {
        return getDelegate().getOrDefault(key, defaultValue);
    }

    @Override
    public synchronized void forEach(BiConsumer<? super Object, ? super Object> action) {
        getDelegate().forEach(action);
    }

    @Override
    public synchronized void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        getDelegate().replaceAll(function);
    }

    @Override
    public synchronized Object putIfAbsent(Object key, Object value) {
        return getDelegate().putIfAbsent(key, value);
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        return getDelegate().remove(key, value);
    }

    @Override
    public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
        return getDelegate().replace(key, oldValue, newValue);
    }

    @Override
    public synchronized Object replace(Object key, Object value) {
        return getDelegate().replace(key, value);
    }

    @Override
    public synchronized Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        return getDelegate().computeIfAbsent(key, mappingFunction);
    }

    @Override
    public synchronized Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return getDelegate().computeIfPresent(key, remappingFunction);
    }

    @Override
    public synchronized Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return getDelegate().compute(key, remappingFunction);
    }

    @Override
    public synchronized Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return getDelegate().merge(key, value, remappingFunction);
    }

}
