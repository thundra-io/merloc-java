package io.thundra.merloc.aws.lambda.core.handler;

import com.amazonaws.services.lambda.runtime.Client;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author serkan
 */
public class LambdaClientContextClient implements Client {

    @JsonProperty("installation_id")
    private String installationId;
    @JsonProperty("app_title")
    private String appTitle;
    @JsonProperty("app_version_name")
    private String appVersionName;
    @JsonProperty("app_version_code")
    private String appVersionCode;
    @JsonProperty("app_package_name")
    private String appPackageName;

    public String getInstallationId() {
        return this.installationId;
    }

    public void setInstallationId(String installationId) {
        this.installationId = installationId;
    }

    public String getAppTitle() {
        return this.appTitle;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle;
    }

    public String getAppVersionName() {
        return this.appVersionName;
    }

    public void setAppVersionName(String appVersionName) {
        this.appVersionName = appVersionName;
    }

    public String getAppVersionCode() {
        return this.appVersionCode;
    }

    public void setAppVersionCode(String appVersionCode) {
        this.appVersionCode = appVersionCode;
    }

    public String getAppPackageName() {
        return this.appPackageName;
    }

    public void setAppPackageName(String appPackageName) {
        this.appPackageName = appPackageName;
    }

}
