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

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class MultiAiGoogleGenAiChatProperties extends MultiAiGoogleGenAiConnectionProperties {

    private List<String> stopSequences;

    private Double temperature;

    private Double topP;

    private Integer topK;

    private Integer candidateCount;

    private Integer maxOutputTokens;

    private String model;

    private String responseMimeType;

    private String responseSchema;

    private Double frequencyPenalty;

    private Double presencePenalty;

    private Integer thinkingBudget;

    private Boolean includeThoughts;

    private String thinkingLevel;

    private Boolean includeExtendedUsageMetadata;

    private String cachedContentName;

    private Boolean useCachedContent;

    private Integer autoCacheThreshold;

    private Duration autoCacheTtl;

    private Boolean googleSearchRetrieval;

    private Boolean includeServerSideToolInvocations;

    private List<SafetySetting> safetySettings;

    private Map<String, String> labels;

    private String serviceTier;

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

    public Integer getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getResponseMimeType() {
        return responseMimeType;
    }

    public void setResponseMimeType(String responseMimeType) {
        this.responseMimeType = responseMimeType;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public void setThinkingBudget(Integer thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }

    public void setIncludeThoughts(Boolean includeThoughts) {
        this.includeThoughts = includeThoughts;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }

    public Boolean getIncludeExtendedUsageMetadata() {
        return includeExtendedUsageMetadata;
    }

    public void setIncludeExtendedUsageMetadata(Boolean includeExtendedUsageMetadata) {
        this.includeExtendedUsageMetadata = includeExtendedUsageMetadata;
    }

    public String getCachedContentName() {
        return cachedContentName;
    }

    public void setCachedContentName(String cachedContentName) {
        this.cachedContentName = cachedContentName;
    }

    public Boolean getUseCachedContent() {
        return useCachedContent;
    }

    public void setUseCachedContent(Boolean useCachedContent) {
        this.useCachedContent = useCachedContent;
    }

    public Integer getAutoCacheThreshold() {
        return autoCacheThreshold;
    }

    public void setAutoCacheThreshold(Integer autoCacheThreshold) {
        this.autoCacheThreshold = autoCacheThreshold;
    }

    public Duration getAutoCacheTtl() {
        return autoCacheTtl;
    }

    public void setAutoCacheTtl(Duration autoCacheTtl) {
        this.autoCacheTtl = autoCacheTtl;
    }

    public Boolean getGoogleSearchRetrieval() {
        return googleSearchRetrieval;
    }

    public void setGoogleSearchRetrieval(Boolean googleSearchRetrieval) {
        this.googleSearchRetrieval = googleSearchRetrieval;
    }

    public Boolean getIncludeServerSideToolInvocations() {
        return includeServerSideToolInvocations;
    }

    public void setIncludeServerSideToolInvocations(Boolean includeServerSideToolInvocations) {
        this.includeServerSideToolInvocations = includeServerSideToolInvocations;
    }

    public List<SafetySetting> getSafetySettings() {
        return safetySettings;
    }

    public void setSafetySettings(List<SafetySetting> safetySettings) {
        this.safetySettings = safetySettings;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public static final class SafetySetting {

        private String category;

        private String threshold;

        private String method;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getThreshold() {
            return threshold;
        }

        public void setThreshold(String threshold) {
            this.threshold = threshold;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }
}
