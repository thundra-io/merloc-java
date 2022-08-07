package io.thundra.merloc.broker.client.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.thundra.merloc.broker.client.BrokerCredentials;
import io.thundra.merloc.broker.client.BrokerEnvelope;
import io.thundra.merloc.broker.client.BrokerMessage;
import io.thundra.merloc.broker.client.BrokerMessageCallback;
import io.thundra.merloc.broker.client.BrokerClient;
import io.thundra.merloc.broker.client.BrokerPayload;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ExceptionUtils;
import io.thundra.merloc.common.utils.ExecutorUtils;
import io.thundra.merloc.common.utils.StringUtils;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author serkan
 */
public final class OkHttpWebSocketBrokerClient
        extends WebSocketListener
        implements BrokerClient {

    private static final String API_KEY_HEADER_NAME = "x-api-key";
    private static final int MAX_FRAME_SIZE = (16 * 1024);

    private static final OkHttpClient baseClient =
            new OkHttpClient.Builder().
                    dispatcher(new Dispatcher(
                            ExecutorUtils.newCachedExecutorService(
                                    "broker-client-okhttp-dispatcher", false))).
                    readTimeout(3,  TimeUnit.SECONDS).
                    pingInterval(30, TimeUnit.SECONDS).
                    build();

    private final ObjectMapper objectMapper =
            new ObjectMapper().
                    configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ScheduledExecutorService inFlightMessageCleanerExecutorService =
            ExecutorUtils.newScheduledExecutorService(1, "broker-client-inflight-cleaner");
    private final ScheduledExecutorService idleEnvelopeCleanerExecutorService =
            ExecutorUtils.newScheduledExecutorService(1, "broker-client-envelope-cleaner");
    private final Map<String, InFlightMessage> messageMap = new ConcurrentHashMap<>();
    private final EnvelopeGlue envelopeGlue = new EnvelopeGlue();
    private final OkHttpClient client;
    private final WebSocket webSocket;
    private final BrokerMessageCallback messageCallback;
    private final CompletableFuture<Boolean> connectedFuture;
    private final CompletableFuture<Boolean> closedFuture;

    public OkHttpWebSocketBrokerClient(String host,
                                       BrokerCredentials brokerCredentials,
                                       BrokerMessageCallback messageCallback) {
        this(host, brokerCredentials, messageCallback, null, null, null);
    }

    public OkHttpWebSocketBrokerClient(String host,
                                       BrokerCredentials brokerCredentials,
                                       BrokerMessageCallback messageCallback,
                                       Map<String, String> headers,
                                       CompletableFuture connectedFuture,
                                       CompletableFuture closedFuture) {
        this.messageCallback = messageCallback;
        this.connectedFuture =
                connectedFuture == null
                        ? new CompletableFuture()
                        : connectedFuture;
        this.closedFuture =
                closedFuture == null
                        ? new CompletableFuture()
                        : closedFuture;
        Request request = buildRequest(host, brokerCredentials, headers);
        this.client = baseClient.newBuilder().build();
        this.webSocket = client.newWebSocket(request, this);
        idleEnvelopeCleanerExecutorService.scheduleAtFixedRate(
                () -> envelopeGlue.cleanIdleEnvelopes(), 1, 1, TimeUnit.MINUTES);
    }

    private static Request buildRequest(String host,
                                        BrokerCredentials brokerCredentials,
                                        Map<String, String> headers) {
        Request.Builder builder = new Request.Builder();
        String url = generateBrokerUrl(host);
        builder.url(url);
        if (brokerCredentials.getConnectionName() != null) {
            builder.header(API_KEY_HEADER_NAME, brokerCredentials.getConnectionName());
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String headerName = e.getKey();
                String headerValue = e.getValue();
                builder.header(headerName, headerValue);
            }
        }
        return builder.build();
    }

    private static String generateBrokerUrl(String host) {
        if (host.startsWith("ws://") || host.startsWith("wss://")) {
            return host;
        } else {
            return "wss://" + host;
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isConnected() {
        return connectedFuture.isDone() && !connectedFuture.isCompletedExceptionally();
    }

    @Override
    public boolean waitUntilConnected() {
        try {
            return connectedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    @Override
    public boolean waitUntilConnected(long timeout, TimeUnit unit) {
        try {
            return connectedFuture.get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void doSend(BrokerMessage message, String payloadStr) throws IOException {
        int payloadLength = payloadStr.length();
        if (payloadLength < MAX_FRAME_SIZE) {
            BrokerEnvelope envelope =
                    new BrokerEnvelope().
                            withId(message.getId()).
                            withResponseOf(message.getResponseOf()).
                            withConnectionName(message.getConnectionName()).
                            withSourceConnectionId(message.getSourceConnectionId()).
                            withSourceConnectionType(message.getSourceConnectionType()).
                            withTargetConnectionId(message.getTargetConnectionId()).
                            withTargetConnectionType(message.getTargetConnectionType()).
                            withType(message.getType()).
                            withPayload(payloadStr);
            String envelopeStr = objectMapper.writeValueAsString(envelope);
            if (!webSocket.send(envelopeStr)) {
                throw new IOException("Unable to send message");
            }
        } else {
            int fragmentCount = (payloadLength / MAX_FRAME_SIZE) + (payloadLength % MAX_FRAME_SIZE == 0 ? 0 : 1);
            for (int i = 0; i < fragmentCount; i++) {
                String fragmentedPayload =
                        payloadStr.substring(
                                i * MAX_FRAME_SIZE,
                                Math.min((i + 1) * MAX_FRAME_SIZE, payloadLength));
                BrokerEnvelope envelope =
                        new BrokerEnvelope().
                                withId(message.getId()).
                                withResponseOf(message.getResponseOf()).
                                withConnectionName(message.getConnectionName()).
                                withSourceConnectionId(message.getSourceConnectionId()).
                                withSourceConnectionType(message.getSourceConnectionType()).
                                withTargetConnectionId(message.getTargetConnectionId()).
                                withTargetConnectionType(message.getTargetConnectionType()).
                                withType(message.getType()).
                                withPayload(fragmentedPayload).
                                withFragmented(true).
                                withFragmentNo(i).
                                withFragmentCount(fragmentCount);
                String envelopeStr = objectMapper.writeValueAsString(envelope);
                if (!webSocket.send(envelopeStr)) {
                    throw new IOException("Unable to send message");
                }
            }
        }
    }

    @Override
    public void send(BrokerMessage message) throws IOException {
        if (StringUtils.isNullOrEmpty(message.getId())) {
            message.setId(UUID.randomUUID().toString());
        }
        BrokerPayload payload =
                new BrokerPayload().
                        withData(message.getData()).
                        withError(message.getError());
        String payloadStr = objectMapper.writeValueAsString(payload);
        doSend(message, payloadStr);
    }

    @Override
    public BrokerMessage sendAndGetResponse(BrokerMessage message,
                                            long timeout, TimeUnit timeUnit) throws IOException {
        if (StringUtils.isNullOrEmpty(message.getId())) {
            message.setId(UUID.randomUUID().toString());
        }
        CompletableFuture<BrokerMessage> responseFuture = new CompletableFuture();
        ScheduledFuture scheduledFuture =
                inFlightMessageCleanerExecutorService.schedule(() -> {
                    InFlightMessage inFlightMessage = messageMap.remove(message.getId());
                    if (inFlightMessage != null) {
                        if (inFlightMessage.completableFuture != null) {
                            inFlightMessage.completableFuture.completeExceptionally(
                                    new TimeoutException(
                                            String.format("Message with id %s has timed-out", message.getId())));
                        }
                    }
                }, timeout, timeUnit);
        messageMap.put(
                message.getId(),
                new InFlightMessage(responseFuture, scheduledFuture));
        try {
            send(message);
        } catch (Throwable t) {
            messageMap.remove(message.getId());
            scheduledFuture.cancel(true);
            ExceptionUtils.sneakyThrow(t);
        }
        try {
            return responseFuture.get(timeout, timeUnit);
        } catch (Throwable t) {
            if (t instanceof ExecutionException) {
                t = t.getCause();
            }
            StdLogger.error("Unable to get response", t);
            return null;
        }
    }

    @Override
    public void sendCloseMessage(int code, String reason) throws IOException {
        if (!webSocket.close(code, reason)) {
            throw new IOException("Unable to send message");
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void close() {
        try {
            webSocket.close(1000, null);
        } catch (Exception e) {
        }
    }

    @Override
    public void destroy() {
        try {
            webSocket.cancel();
        } catch (Exception e) {
        }
        inFlightMessageCleanerExecutorService.shutdownNow();
        idleEnvelopeCleanerExecutorService.shutdownNow();
    }

    @Override
    public boolean isClosed() {
        return closedFuture.isDone() && !closedFuture.isCompletedExceptionally();
    }

    @Override
    public boolean waitUntilClosed() {
        try {
            return closedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    @Override
    public boolean waitUntilClosed(long timeout, TimeUnit unit) {
        try {
            return closedFuture.get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        StdLogger.debug("OPEN: " + response.message());
        connectedFuture.complete(true);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (StdLogger.DEBUG_ENABLED) {
            StdLogger.debug("MESSAGE: " + text);
        }
        receiveMessage(text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        String text = bytes.utf8();
        if (StdLogger.DEBUG_ENABLED) {
            StdLogger.debug("MESSAGE: " + text);
        }
        receiveMessage(text);
    }

    private void receiveMessage(String text) {
        try {
            BrokerEnvelope envelope = objectMapper.readValue(text, BrokerEnvelope.class);
            String payloadStr = envelope.getPayload();
            if (StringUtils.isNullOrEmpty(payloadStr)) {
                StdLogger.error("Empty payload in envelope");
                return;
            }
            if (envelope.isFragmented()) {
                envelopeGlue.glue(envelope);
            } else {
                BrokerPayload payload = objectMapper.readValue(payloadStr, BrokerPayload.class);
                if (payload == null) {
                    StdLogger.error("Empty payload in envelope");
                    return;
                }
                BrokerMessage message =
                        new BrokerMessage().
                                withId(envelope.getId()).
                                withResponseOf(envelope.getResponseOf()).
                                withConnectionName(envelope.getConnectionName()).
                                withSourceConnectionId(envelope.getSourceConnectionId()).
                                withSourceConnectionType(envelope.getSourceConnectionType()).
                                withTargetConnectionId(envelope.getTargetConnectionId()).
                                withTargetConnectionType(envelope.getTargetConnectionType()).
                                withType(envelope.getType()).
                                withData(payload.getData()).
                                withError(payload.getError());
                handleMessage(message);
            }
        } catch (Throwable error) {
            StdLogger.error(String.format("Unable to deserialize broker message: %s", text, error));
        }
    }

    private void handleMessage(BrokerMessage message) {
        try {
            if (StringUtils.hasValue(message.getResponseOf())) {
                InFlightMessage inFlightMessage = messageMap.remove(message.getResponseOf());
                if (inFlightMessage != null) {
                    if (inFlightMessage.scheduledFuture != null) {
                        inFlightMessage.scheduledFuture.cancel(true);
                    }
                    if (inFlightMessage.completableFuture != null) {
                        inFlightMessage.completableFuture.complete(message);
                    }
                }
            }
            if (messageCallback != null) {
                messageCallback.onMessage(this, message);
            }
        } catch (Throwable error) {
            StdLogger.error(String.format("Unable to handle broker message: %s", message, error));
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        StdLogger.debug("CLOSING: " + code + " " + reason);
        webSocket.close(1000, null);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        StdLogger.debug("CLOSED: " + code + " " + reason);
        closedFuture.complete(true);
        destroyInFlightMessages(code, reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        StdLogger.error("FAILED: ", t);
        try {
            if (response != null && response.body() != null) {
                StdLogger.error(response.body().string());
            }
        } catch (Exception e) {
        }
        if (!isConnected()) {
            connectedFuture.completeExceptionally(t);
        }
        closedFuture.completeExceptionally(t);
        destroyInFlightMessages(-1, t.getMessage());
    }

    private void destroyInFlightMessages(int code, String reason) {
        Iterator<InFlightMessage> iter = messageMap.values().iterator();
        while (iter.hasNext()) {
            InFlightMessage inFlightMessage = iter.next();
            iter.remove();
            if (inFlightMessage.scheduledFuture != null) {
                inFlightMessage.scheduledFuture.cancel(true);
            }
            if (inFlightMessage.completableFuture != null) {
                inFlightMessage.completableFuture.completeExceptionally(
                        new IOException(String.format(
                                "Connection is closed (code=%d, reason=%s)", code, reason)));
            }
        }
    }

    private class InFlightMessage {

        private final CompletableFuture completableFuture;
        private final ScheduledFuture scheduledFuture;

        private InFlightMessage(CompletableFuture completableFuture, ScheduledFuture scheduledFuture) {
            this.completableFuture = completableFuture;
            this.scheduledFuture = scheduledFuture;
        }

    }

    private static class BrokerEnvelopeKey {

        private final String id;
        private final long initTime;

        private BrokerEnvelopeKey(String id, long initTime) {
            this.id = id;
            this.initTime = initTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BrokerEnvelopeKey that = (BrokerEnvelopeKey) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

    }

    private class EnvelopeGlue {

        private final long ENVELOPE_IDLE_TIMEOUT = TimeUnit.MINUTES.toMillis(1);

        private final Map<BrokerEnvelopeKey, Set<BrokerEnvelope>> envelopeMap = new ConcurrentHashMap<>();

        private void glueSiblingEnvelopesAndHandleMessage(Set<BrokerEnvelope> siblingEnvelopes) {
            BrokerEnvelope firstEnvelope = siblingEnvelopes.iterator().next();
            StringBuilder payloadBuilder = new StringBuilder();
            for (BrokerEnvelope envelope : siblingEnvelopes) {
                payloadBuilder.append(envelope.getPayload());
            }
            String payloadStr = payloadBuilder.toString();
            try {
                BrokerPayload payload = objectMapper.readValue(payloadStr, BrokerPayload.class);
                BrokerMessage message =
                        new BrokerMessage().
                                withId(firstEnvelope.getId()).
                                withResponseOf(firstEnvelope.getResponseOf()).
                                withConnectionName(firstEnvelope.getConnectionName()).
                                withSourceConnectionId(firstEnvelope.getSourceConnectionId()).
                                withSourceConnectionType(firstEnvelope.getSourceConnectionType()).
                                withTargetConnectionId(firstEnvelope.getTargetConnectionId()).
                                withTargetConnectionType(firstEnvelope.getTargetConnectionType()).
                                withType(firstEnvelope.getType()).
                                withData(payload.getData()).
                                withError(payload.getError());
                handleMessage(message);
            } catch (Throwable t) {
                StdLogger.error(String.format(
                        "Unable to deserialize broker message from glued data: %s", payloadStr),
                        t);
            }
        }

        private void glue(BrokerEnvelope envelope) {
            String id = envelope.getId();
            int fragmentCount = envelope.getFragmentCount();
            BrokerEnvelopeKey key = new BrokerEnvelopeKey(id, System.currentTimeMillis());
            Set<BrokerEnvelope> siblingEnvelopes = envelopeMap.get(key);
            if (siblingEnvelopes == null) {
                siblingEnvelopes = new ConcurrentSkipListSet<>(Comparator.comparingInt(BrokerEnvelope::getFragmentNo));
                Set<BrokerEnvelope> existingSiblingEnvelopes = envelopeMap.putIfAbsent(key, siblingEnvelopes);
                if (existingSiblingEnvelopes != null) {
                    siblingEnvelopes = existingSiblingEnvelopes;
                }
            }
            siblingEnvelopes.add(envelope);
            // Check whether we collect all the fragments
            if (siblingEnvelopes.size() == fragmentCount) {
                envelopeMap.remove(key);
                // If so, glue all the fragments to build original message
                glueSiblingEnvelopesAndHandleMessage(siblingEnvelopes);
            }
        }

        private void cleanIdleEnvelopes() {
            long currentTime = System.currentTimeMillis();
            Iterator<BrokerEnvelopeKey> iter = envelopeMap.keySet().iterator();
            while (iter.hasNext()) {
                BrokerEnvelopeKey key = iter.next();
                // Check whether if there is an idle envelope.
                // Normally this is not an expected case,
                // but it can happen if some fragments were not able to transmitted or processed somehow.
                if (currentTime - key.initTime > ENVELOPE_IDLE_TIMEOUT) {
                    iter.remove();
                }
            }
        }

    }

}
