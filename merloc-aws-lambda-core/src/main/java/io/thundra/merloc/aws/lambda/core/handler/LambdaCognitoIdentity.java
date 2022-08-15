package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author serkan
 */
public class LambdaCognitoIdentity implements CognitoIdentity {

    @JsonProperty("identityid")
    private String identityId;
    @JsonProperty("poolid")
    private String poolId;

    public String getIdentityId() {
        return this.identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public String getIdentityPoolId() {
        return this.poolId;
    }

    public void setIdentityPoolId(String poolId) {
        this.poolId = poolId;
    }

}
