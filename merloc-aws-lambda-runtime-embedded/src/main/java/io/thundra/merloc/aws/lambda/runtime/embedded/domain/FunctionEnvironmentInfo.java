package io.thundra.merloc.aws.lambda.runtime.embedded.domain;

import java.util.Map;
import java.util.Properties;

/**
 * @author serkan
 */
public class FunctionEnvironmentInfo {

    private final String functionArn;
    private final String functionName;
    private final Map<String, String> envVars;
    private final Properties sysProps;

    public FunctionEnvironmentInfo(String functionArn, String functionName,
                                   Map<String, String> envVars, Properties sysProps) {
        this.functionArn = functionArn;
        this.functionName = functionName;
        this.envVars = envVars;
        this.sysProps = sysProps;
    }

    public String getFunctionArn() {
        return functionArn;
    }

    public String getFunctionName() {
        return functionName;
    }

    public Map<String, String> getEnvironmentVariables() {
        return envVars;
    }

    public Properties getSystemProperties() {
        return sysProps;
    }

}
