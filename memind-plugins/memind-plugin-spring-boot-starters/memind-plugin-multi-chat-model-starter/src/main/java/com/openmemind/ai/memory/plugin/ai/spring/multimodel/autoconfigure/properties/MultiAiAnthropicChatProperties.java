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
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public final class MultiAiAnthropicChatProperties {

    private String baseUrl;

    private String apiKey;

    private Duration timeout;

    private Integer maxRetries;

    private Proxy proxy;

    private Map<String, String> customHeaders = new HashMap<>();

    private String model;

    private Integer maxTokens;

    private Map<String, Object> metadata;

    private List<String> stopSequences;

    private Double temperature;

    private Double topP;

    private Integer topK;

    private Object toolChoice;

    private Map<String, Object> thinking;

    private Boolean disableParallelToolUse;

    private Map<String, Object> outputConfig;

    @NestedConfigurationProperty private WebSearchTool webSearchTool;

    private String serviceTier;

    private String inferenceGeo;

    @NestedConfigurationProperty private CacheOptions cacheOptions;

    private Map<String, String> httpHeaders = new HashMap<>();

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

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Map<String, Object> getThinking() {
        return thinking;
    }

    public void setThinking(Map<String, Object> thinking) {
        this.thinking = thinking;
    }

    public Boolean getDisableParallelToolUse() {
        return disableParallelToolUse;
    }

    public void setDisableParallelToolUse(Boolean disableParallelToolUse) {
        this.disableParallelToolUse = disableParallelToolUse;
    }

    public Map<String, Object> getOutputConfig() {
        return outputConfig;
    }

    public void setOutputConfig(Map<String, Object> outputConfig) {
        this.outputConfig = outputConfig;
    }

    public WebSearchTool getWebSearchTool() {
        return webSearchTool;
    }

    public void setWebSearchTool(WebSearchTool webSearchTool) {
        this.webSearchTool = webSearchTool;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public String getInferenceGeo() {
        return inferenceGeo;
    }

    public void setInferenceGeo(String inferenceGeo) {
        this.inferenceGeo = inferenceGeo;
    }

    public CacheOptions getCacheOptions() {
        return cacheOptions;
    }

    public void setCacheOptions(CacheOptions cacheOptions) {
        this.cacheOptions = cacheOptions;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public boolean isConnectionPoolMetricsEnabled() {
        return connectionPoolMetricsEnabled;
    }

    public void setConnectionPoolMetricsEnabled(boolean connectionPoolMetricsEnabled) {
        this.connectionPoolMetricsEnabled = connectionPoolMetricsEnabled;
    }

    public static final class CacheOptions {

        private String strategy;

        private Map<String, String> messageTypeTtl;

        private Map<String, Integer> messageTypeMinContentLengths;

        private Boolean multiBlockSystemCaching;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public Map<String, String> getMessageTypeTtl() {
            return messageTypeTtl;
        }

        public void setMessageTypeTtl(Map<String, String> messageTypeTtl) {
            this.messageTypeTtl = messageTypeTtl;
        }

        public Map<String, Integer> getMessageTypeMinContentLengths() {
            return messageTypeMinContentLengths;
        }

        public void setMessageTypeMinContentLengths(
                Map<String, Integer> messageTypeMinContentLengths) {
            this.messageTypeMinContentLengths = messageTypeMinContentLengths;
        }

        public Boolean getMultiBlockSystemCaching() {
            return multiBlockSystemCaching;
        }

        public void setMultiBlockSystemCaching(Boolean multiBlockSystemCaching) {
            this.multiBlockSystemCaching = multiBlockSystemCaching;
        }
    }

    public static final class WebSearchTool {

        private List<String> allowedDomains;

        private List<String> blockedDomains;

        private Long maxUses;

        @NestedConfigurationProperty private UserLocation userLocation;

        public List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(List<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        public List<String> getBlockedDomains() {
            return blockedDomains;
        }

        public void setBlockedDomains(List<String> blockedDomains) {
            this.blockedDomains = blockedDomains;
        }

        public Long getMaxUses() {
            return maxUses;
        }

        public void setMaxUses(Long maxUses) {
            this.maxUses = maxUses;
        }

        public UserLocation getUserLocation() {
            return userLocation;
        }

        public void setUserLocation(UserLocation userLocation) {
            this.userLocation = userLocation;
        }
    }

    public static final class UserLocation {

        private String city;

        private String country;

        private String region;

        private String timezone;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }
    }
}
