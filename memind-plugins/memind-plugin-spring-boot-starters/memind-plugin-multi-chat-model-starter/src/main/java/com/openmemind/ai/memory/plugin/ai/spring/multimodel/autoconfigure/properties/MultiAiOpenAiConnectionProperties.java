/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class MultiAiOpenAiConnectionProperties {

    private String baseUrl;

    private String apiKey;

    private Object credential;

    private String model;

    private String microsoftDeploymentName;

    private String microsoftFoundryServiceVersion;

    private String organizationId;

    private boolean microsoftFoundry;

    private boolean gitHubModels;

    private Duration timeout = Duration.ofSeconds(60);

    private int maxRetries = 3;

    private Proxy proxy;

    private Map<String, String> customHeaders = new HashMap<>();

    private boolean connectionPoolMetricsEnabled;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Object getCredential() {
        return credential;
    }

    public void setCredential(Object credential) {
        this.credential = credential;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getMicrosoftDeploymentName() {
        return microsoftDeploymentName;
    }

    public void setMicrosoftDeploymentName(String microsoftDeploymentName) {
        this.microsoftDeploymentName = microsoftDeploymentName;
    }

    public String getDeploymentName() {
        return microsoftDeploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.microsoftDeploymentName = deploymentName;
    }

    public String getMicrosoftFoundryServiceVersion() {
        return microsoftFoundryServiceVersion;
    }

    public void setMicrosoftFoundryServiceVersion(String microsoftFoundryServiceVersion) {
        this.microsoftFoundryServiceVersion = microsoftFoundryServiceVersion;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public boolean isMicrosoftFoundry() {
        return microsoftFoundry;
    }

    public void setMicrosoftFoundry(boolean microsoftFoundry) {
        this.microsoftFoundry = microsoftFoundry;
    }

    public boolean isGitHubModels() {
        return gitHubModels;
    }

    public void setGitHubModels(boolean gitHubModels) {
        this.gitHubModels = gitHubModels;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public boolean isConnectionPoolMetricsEnabled() {
        return connectionPoolMetricsEnabled;
    }

    public void setConnectionPoolMetricsEnabled(boolean connectionPoolMetricsEnabled) {
        this.connectionPoolMetricsEnabled = connectionPoolMetricsEnabled;
    }
}
