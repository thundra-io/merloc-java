package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.Client;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @author serkan
 */
public class LambdaClientContext implements ClientContext {

    @JsonProperty("client")
    private LambdaClientContextClient client;
    @JsonProperty("custom")
    private Map<String, String> custom;
    @JsonProperty("env")
    private Map<String, String> env;

    public Client getClient() {
        return this.client;
    }

    public void setClient(LambdaClientContextClient client) {
        this.client = client;
    }

    public Map<String, String> getCustom() {
        return this.custom;
    }

    public void setCustom(Map<String, String> custom) {
        this.custom = custom;
    }

    public Map<String, String> getEnvironment() {
        return this.env;
    }

    public void setEnvironment(Map<String, String> env) {
        this.env = env;
    }

}
