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

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = MultiAiModelProperties.PREFIX)
public class MultiAiModelProperties {

    public static final String PREFIX = "spring.ai";

    @NestedConfigurationProperty
    private PrimaryModelProperties model = new PrimaryModelProperties();

    private Map<String, ChatModelProperties> chatModels = new LinkedHashMap<>();

    private Map<String, EmbeddingModelProperties> embeddingModels = new LinkedHashMap<>();

    public PrimaryModelProperties getModel() {
        return model;
    }

    public void setModel(PrimaryModelProperties model) {
        this.model = model != null ? model : new PrimaryModelProperties();
    }

    public Map<String, ChatModelProperties> getChatModels() {
        return chatModels;
    }

    public void setChatModels(Map<String, ChatModelProperties> chatModels) {
        this.chatModels = chatModels;
    }

    public Map<String, EmbeddingModelProperties> getEmbeddingModels() {
        return embeddingModels;
    }

    public void setEmbeddingModels(Map<String, EmbeddingModelProperties> embeddingModels) {
        this.embeddingModels = embeddingModels;
    }

    public static final class PrimaryModelProperties {

        private static final String DEFAULT_MODEL_ID = "default";

        /**
         * Primary chat model id.
         */
        private String chat = DEFAULT_MODEL_ID;

        /**
         * Primary embedding model id.
         */
        private String embedding = DEFAULT_MODEL_ID;

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat;
        }

        public String getEmbedding() {
            return embedding;
        }

        public void setEmbedding(String embedding) {
            this.embedding = embedding;
        }
    }
}
