package io.thundra.merloc.broker.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author serkan
 */
public class TypeAwareBrokerMessageCallback implements BrokerMessageCallback {

    private final Set<String> types;
    private final BrokerMessageCallback callback;

    public TypeAwareBrokerMessageCallback(Collection<String> types, BrokerMessageCallback callback) {
        this.types = new HashSet<>(types);
        this.callback = callback;
    }

    @Override
    public final void onMessage(BrokerClient brokerClient, BrokerMessage brokerMessage) {
        String type = brokerMessage.getType();
        if (type != null && types.contains(type)) {
            callback.onMessage(brokerClient, brokerMessage);
        }
    }

}
