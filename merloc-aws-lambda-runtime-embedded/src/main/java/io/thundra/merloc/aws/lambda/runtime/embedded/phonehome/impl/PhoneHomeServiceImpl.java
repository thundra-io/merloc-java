package io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.PhoneHomeService;
import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.PhoneHomeMessage;
import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.impl.RuntimeDownMessage;
import io.thundra.merloc.aws.lambda.runtime.embedded.phonehome.message.impl.RuntimeUpMessage;
import io.thundra.merloc.common.config.ConfigManager;
import io.thundra.merloc.common.logger.StdLogger;
import io.thundra.merloc.common.utils.ExecutorUtils;
import okhttp3.Call;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author serkan
 */
public class PhoneHomeServiceImpl implements PhoneHomeService {

    private static final String PHONE_HOME_URL_CONFIG_NAME = "merloc.phonehome.url";
    private static final String PHONE_HOME_ENABLE_CONFIG_NAME = "merloc.phonehome.enable";
    private static final String DEFAULT_PHONE_HOME_URL = "merloc.bizops.thundra.io";

    private final OkHttpClient httpClient =
            new OkHttpClient.Builder().
                    dispatcher(new Dispatcher(
                            ExecutorUtils.newCachedExecutorService(
                                    "phone-home-okhttp-dispatcher", false))).
                    readTimeout(3,  TimeUnit.SECONDS).
                    pingInterval(30, TimeUnit.SECONDS).
                    build();
    private final boolean phoneHomeEnable =
            ConfigManager.getBooleanConfig(PHONE_HOME_ENABLE_CONFIG_NAME, true);
    private final String phoneHomeURL =
            "https://" + ConfigManager.getConfig(PHONE_HOME_URL_CONFIG_NAME, DEFAULT_PHONE_HOME_URL) + "/phone-home";
    private final String machineHash = getMachineHash();
    private final String osName = getOsName();
    private final int jvmVersion = getJvmVersion();
    private final ObjectMapper objectMapper =
            new ObjectMapper().
                    disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private String getMachineHash() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
            byte[] hardwareAddress = ni.getHardwareAddress();
            return UUID.nameUUIDFromBytes(hardwareAddress).toString();
        } catch (Exception e) {
            StdLogger.debug("Unable to get machine hash", e);
            return null;
        }
    }

    private String getOsName() {
        return System.getProperty("os.name");
    }

    private int getJvmVersion() {
        try {
            String[] versionElements = System.getProperty("java.version").split("\\.");
            int discard = Integer.parseInt(versionElements[0]);
            int version;
            if (discard == 1) {
                version = Integer.parseInt(versionElements[1]);
            } else {
                version = discard;
            }
            return version;
        } catch (Exception e) {
            StdLogger.debug("Unable to get JVM version", e);
            return -1;
        }
    }

    @Override
    public void runtimeUp(long startTime) {
        if (!phoneHomeEnable) {
            return;
        }
        RuntimeUpMessage message =
                RuntimeUpMessage.
                        builder().
                        machineHash(machineHash).
                        osName(osName).
                        jvmVersion(jvmVersion).
                        startTime(startTime).
                        build();
        sendMessage(message, "/runtime/up");
    }

    @Override
    public void runtimeDown(long startTime, long finishTime) {
        if (!phoneHomeEnable) {
            return;
        }
        RuntimeDownMessage message =
                RuntimeDownMessage.
                        builder().
                        machineHash(machineHash).
                        osName(osName).
                        jvmVersion(jvmVersion).
                        startTime(startTime).
                        finishTime(finishTime).
                        build();
        sendMessage(message, "/runtime/down");
    }

    private void sendMessage(PhoneHomeMessage message, String path) {
        try {
            String messageStr = objectMapper.writeValueAsString(message);

            StdLogger.debug(String.format(
                    "Sending '%s' message to phone-home: %s",
                    message.getType(), messageStr));

            Request request =
                    new Request.Builder().
                            url(phoneHomeURL + path).
                            post(RequestBody.create(MediaType.parse("application/json"), messageStr)).
                            build();
            Call call = httpClient.newCall(request);
            Response response = call.execute();

            StdLogger.debug(String.format(
                    "Has sent '%s' message to phone-home with status code %d",
                    message.getType(), response.code()));
        } catch (Throwable error) {
            StdLogger.debug(
                    String.format("Unable to send '%s' message to phone-home", message.getType()),
                    error);
        }
    }

}
