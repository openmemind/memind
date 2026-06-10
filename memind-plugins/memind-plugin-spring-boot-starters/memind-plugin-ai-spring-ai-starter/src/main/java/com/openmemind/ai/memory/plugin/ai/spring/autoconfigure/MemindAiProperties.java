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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.ai")
public class MemindAiProperties {

    private ChatProperties chat = new ChatProperties();
    private EmbeddingProperties embedding = new EmbeddingProperties();

    public ChatProperties getChat() {
        return chat;
    }

    public void setChat(ChatProperties chat) {
        this.chat = chat;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }

    public static class ChatProperties {

        private String defaultClient;
        private Map<String, ClientProperties> clients = new LinkedHashMap<>();
        private Map<ChatClientSlot, String> slots = new EnumMap<>(ChatClientSlot.class);

        public String getDefaultClient() {
            return defaultClient;
        }

        public void setDefaultClient(String defaultClient) {
            this.defaultClient = defaultClient;
        }

        public Map<String, ClientProperties> getClients() {
            return clients;
        }

        public void setClients(Map<String, ClientProperties> clients) {
            this.clients = clients;
        }

        public Map<ChatClientSlot, String> getSlots() {
            return slots;
        }

        public void setSlots(Map<ChatClientSlot, String> slots) {
            this.slots = slots;
        }
    }

    public static class EmbeddingProperties {

        private String client;
        private Map<String, ClientProperties> clients = new LinkedHashMap<>();

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public Map<String, ClientProperties> getClients() {
            return clients;
        }

        public void setClients(Map<String, ClientProperties> clients) {
            this.clients = clients;
        }
    }

    public static class ClientProperties {

        private String provider;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxTokens;
        private Integer maxCompletionTokens;
        private Integer dimensions;
        private String projectId;
        private String location;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

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

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
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

        public Integer getDimensions() {
            return dimensions;
        }

        public void setDimensions(Integer dimensions) {
            this.dimensions = dimensions;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }
}
