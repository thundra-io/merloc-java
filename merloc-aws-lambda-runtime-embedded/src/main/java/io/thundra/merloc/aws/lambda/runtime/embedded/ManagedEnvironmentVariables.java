package io.thundra.merloc.aws.lambda.runtime.embedded;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author serkan
 */
public class ManagedEnvironmentVariables extends HashMap<String, String> {

    private final Map<String, String> baseEnvVars;
    private final Map<ThreadGroup, Map<String, String>> threadGroupEnvVars = new ConcurrentHashMap<>();

    public ManagedEnvironmentVariables(Map<String, String> baseEnvVars) {
        this.baseEnvVars = baseEnvVars;
    }

    private Map<String, String> getDelegate() {
        for (ThreadGroup tg = Thread.currentThread().getThreadGroup(); tg != null; tg = tg.getParent()) {
            Map<String, String> delegate = threadGroupEnvVars.get(tg);
            if (delegate != null) {
                return delegate;
            }
        }
        return baseEnvVars;
    }

    public Map<String, String> mergeWithBaseEnvVars(Map<String, String> envVars) {
        Map<String, String> allEnvVars = new HashMap<>();
        allEnvVars.putAll(baseEnvVars);
        allEnvVars.putAll(envVars);
        return allEnvVars;
    }

    public Map<String, String> getThreadGroupAwareEnvVars() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            return threadGroupEnvVars.get(tg);
        }
        return null;
    }

    public void setThreadGroupAwareEnvVars(Map<String, String> envVars) {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            Map<String, String> allEnvVars = mergeWithBaseEnvVars(envVars);
            threadGroupEnvVars.put(tg, allEnvVars);
        }
    }

    public void clearThreadGroupAwareEnvVars() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        if (tg != null) {
            threadGroupEnvVars.remove(tg);
        }
    }

    @Override
    public String put(String key, String value) {
        return getDelegate().put(key, value);
    }

    @Override
    public int size() {
        return getDelegate().size();
    }

    @Override
    public boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    @Override
    public String get(Object key) {
        return getDelegate().get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        getDelegate().putAll(m);
    }

    @Override
    public String remove(Object key) {
        return getDelegate().remove(key);
    }

    @Override
    public void clear() {
        getDelegate().clear();
    }

    @Override
    public boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    @Override
    public Set<String> keySet() {
        return getDelegate().keySet();
    }

    @Override
    public Collection<String> values() {
        return getDelegate().values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return getDelegate().entrySet();
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        return getDelegate().getOrDefault(key, defaultValue);
    }

    @Override
    public String putIfAbsent(String key, String value) {
        return getDelegate().putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return getDelegate().remove(key, value);
    }

    @Override
    public boolean replace(String key, String oldValue, String newValue) {
        return getDelegate().replace(key, oldValue, newValue);
    }

    @Override
    public String replace(String key, String value) {
        return getDelegate().replace(key, value);
    }

    @Override
    public String computeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
        return getDelegate().computeIfAbsent(key, mappingFunction);
    }

    @Override
    public String computeIfPresent(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return getDelegate().computeIfPresent(key, remappingFunction);
    }

    @Override
    public String compute(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return getDelegate().compute(key, remappingFunction);
    }

    @Override
    public String merge(String key, String value, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return getDelegate().merge(key, value, remappingFunction);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        getDelegate().forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super String, ? extends String> function) {
        getDelegate().replaceAll(function);
    }

    @Override
    public boolean equals(Object o) {
        return getDelegate().equals(o);
    }

    @Override
    public int hashCode() {
        return getDelegate().hashCode();
    }

    @Override
    public String toString() {
        return getDelegate().toString();
    }

}
