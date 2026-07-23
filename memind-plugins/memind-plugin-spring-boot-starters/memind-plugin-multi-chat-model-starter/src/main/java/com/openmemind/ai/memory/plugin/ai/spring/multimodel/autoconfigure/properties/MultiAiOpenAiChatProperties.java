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

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public final class MultiAiOpenAiChatProperties extends MultiAiOpenAiConnectionProperties {

    private Double frequencyPenalty;

    private Map<String, Integer> logitBias;

    private Boolean logprobs;

    private Integer topLogprobs;

    private Integer maxTokens;

    private Integer maxCompletionTokens;

    private Integer n;

    private List<String> outputModalities;

    @NestedConfigurationProperty private AudioParameters outputAudio;

    private Double presencePenalty;

    @NestedConfigurationProperty private ResponseFormat responseFormat;

    @NestedConfigurationProperty private StreamOptions streamOptions;

    private Integer seed;

    private List<String> stop;

    private Double temperature;

    private Double topP;

    private Object toolChoice;

    private String user;

    private Boolean parallelToolCalls;

    private Boolean store;

    private Map<String, String> metadata;

    private String reasoningEffort;

    private String verbosity;

    private String serviceTier;

    private String promptCacheKey;

    private Map<String, Object> extraBody;

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public void setLogitBias(Map<String, Integer> logitBias) {
        this.logitBias = logitBias;
    }

    public Boolean getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(Boolean logprobs) {
        this.logprobs = logprobs;
    }

    public Integer getTopLogprobs() {
        return topLogprobs;
    }

    public void setTopLogprobs(Integer topLogprobs) {
        this.topLogprobs = topLogprobs;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public List<String> getOutputModalities() {
        return outputModalities;
    }

    public void setOutputModalities(List<String> outputModalities) {
        this.outputModalities = outputModalities;
    }

    public AudioParameters getOutputAudio() {
        return outputAudio;
    }

    public void setOutputAudio(AudioParameters outputAudio) {
        this.outputAudio = outputAudio;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    public StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(StreamOptions streamOptions) {
        this.streamOptions = streamOptions;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
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

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public Boolean getStore() {
        return store;
    }

    public void setStore(Boolean store) {
        this.store = store;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(String verbosity) {
        this.verbosity = verbosity;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public String getPromptCacheKey() {
        return promptCacheKey;
    }

    public void setPromptCacheKey(String promptCacheKey) {
        this.promptCacheKey = promptCacheKey;
    }

    public Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody;
    }

    public static final class AudioParameters {

        private String voice;

        private String format;

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    public static final class ResponseFormat {

        private String type;

        private String jsonSchema;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getJsonSchema() {
            return jsonSchema;
        }

        public void setJsonSchema(String jsonSchema) {
            this.jsonSchema = jsonSchema;
        }
    }

    public static final class StreamOptions {

        private Boolean includeObfuscation;

        private Boolean includeUsage;

        private Map<String, Object> additionalProperties;

        public Boolean getIncludeObfuscation() {
            return includeObfuscation;
        }

        public void setIncludeObfuscation(Boolean includeObfuscation) {
            this.includeObfuscation = includeObfuscation;
        }

        public Boolean getIncludeUsage() {
            return includeUsage;
        }

        public void setIncludeUsage(Boolean includeUsage) {
            this.includeUsage = includeUsage;
        }

        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        public void setAdditionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties;
        }
    }
}
