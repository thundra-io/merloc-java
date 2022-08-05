package io.thundra.merloc.broker.client.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.thundra.merloc.broker.client.BrokerCredentials;
import io.thundra.merloc.broker.client.BrokerMessage;
import io.thundra.merloc.broker.client.BrokerMessageCallback;
import io.thundra.merloc.broker.client.BrokerClient;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, InFlightMessage> messageMap = new ConcurrentHashMap<>();
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

    @Override
    public void send(BrokerMessage message) throws IOException {
        if (StringUtils.isNullOrEmpty(message.getId())) {
            message.setId(UUID.randomUUID().toString());
        }
        String messageStr = objectMapper.writeValueAsString(message);
        if (!webSocket.send(messageStr)) {
            throw new IOException("Unable to send message");
        }
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
        handleOnMessage(text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        String text = bytes.utf8();
        if (StdLogger.DEBUG_ENABLED) {
            StdLogger.debug("MESSAGE: " + text);
        }
        handleOnMessage(text);
    }

    private void handleOnMessage(String text) {
        try {
            BrokerMessage message = objectMapper.readValue(text, BrokerMessage.class);
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
            StdLogger.error(String.format("Unable to deserialize broker message: %s", text, error));
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
    }

    private class InFlightMessage {

        private final CompletableFuture completableFuture;
        private final ScheduledFuture scheduledFuture;

        private InFlightMessage(CompletableFuture completableFuture,
                                ScheduledFuture scheduledFuture) {
            this.completableFuture = completableFuture;
            this.scheduledFuture = scheduledFuture;
        }

    }

}
