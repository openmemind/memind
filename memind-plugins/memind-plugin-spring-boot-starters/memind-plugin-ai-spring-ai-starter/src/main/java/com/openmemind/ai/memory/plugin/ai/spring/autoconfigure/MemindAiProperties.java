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
        private String defaultBean;
        private Map<ChatClientSlot, String> slots = new EnumMap<>(ChatClientSlot.class);

        public String getDefaultClient() {
            return getDefault();
        }

        public void setDefaultClient(String defaultClient) {
            this.defaultClient = defaultClient;
        }

        public String getDefault() {
            return defaultBean != null ? defaultBean : defaultClient;
        }

        public void setDefault(String defaultBean) {
            this.defaultBean = defaultBean;
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
        private String defaultBean;

        public String getClient() {
            return getDefault();
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getDefault() {
            return defaultBean != null ? defaultBean : client;
        }

        public void setDefault(String defaultBean) {
            this.defaultBean = defaultBean;
        }
    }
}
