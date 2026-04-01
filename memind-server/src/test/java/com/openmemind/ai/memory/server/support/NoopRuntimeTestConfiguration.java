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
package com.openmemind.ai.memory.server.support;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@TestConfiguration(proxyBeanMethods = false)
public class NoopRuntimeTestConfiguration {

    public static final String SPRING_AI_AUTOCONFIG_EXCLUDES =
            String.join(
                    ",",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
                    "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiLlmAutoConfiguration",
                    "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiVectorAutoConfiguration");

    @Bean
    StructuredChatClient structuredChatClient() {
        return new NoopStructuredChatClient();
    }

    @Bean
    MemoryVector memoryVector() {
        return new NoopMemoryVector();
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "LLM calls are not used in this integration test"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "LLM calls are not used in this integration test"));
        }
    }

    private static final class NoopMemoryVector implements MemoryVector {

        @Override
        public Mono<String> store(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String text,
                Map<String, Object> metadata) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "Vector store is not used in this integration test"));
        }

        @Override
        public Mono<List<String>> storeBatch(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                List<String> texts,
                List<Map<String, Object>> metadataList) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "Vector store is not used in this integration test"));
        }

        @Override
        public Mono<Void> delete(
                com.openmemind.ai.memory.core.data.MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(
                com.openmemind.ai.memory.core.data.MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                com.openmemind.ai.memory.core.data.MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String query,
                int topK,
                Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "Vector embedding is not used in this integration test"));
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "Vector embedding is not used in this integration test"));
        }
    }
}
