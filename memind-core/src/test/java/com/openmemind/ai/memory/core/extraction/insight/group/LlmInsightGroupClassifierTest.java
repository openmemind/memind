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
package com.openmemind.ai.memory.core.extraction.insight.group;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmInsightGroupClassifierTest {

    @Test
    @DisplayName("Should replace metadata-like group names with a content-derived fallback")
    void shouldReplaceMetadataLikeGroupNames() {
        var item =
                new MemoryItem(
                        1L,
                        "m1",
                        "去动物园会让我安心，这是我常用的自我安抚方式。",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var response =
                new InsightGroupClassifyResponse(
                        List.of(
                                new InsightGroupClassifyResponse.GroupAssignment(
                                        "2026-03-27 会话记录", List.of("1"))));
        var classifier = new LlmInsightGroupClassifier(new FakeStructuredChatClient(response));

        StepVerifier.create(classifier.classify(createInsightType(), List.of(item), List.of()))
                .assertNext(
                        groups ->
                                assertThat(groups)
                                        .containsOnlyKeys("去动物园会让我安心")
                                        .containsEntry("去动物园会让我安心", List.of(item)))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use insight group override instruction")
    void shouldUseInsightGroupOverrideInstruction() {
        var item =
                new MemoryItem(
                        1L,
                        "m1",
                        "The team needs a concise way to redirect a meeting back to the agenda.",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var response =
                new InsightGroupClassifyResponse(
                        List.of(
                                new InsightGroupClassifyResponse.GroupAssignment(
                                        "Meeting Coordination", List.of("1"))));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.INSIGHT_GROUP, "Custom insight group instruction")
                        .build();
        var client = new FakeStructuredChatClient(response);
        var classifier = new LlmInsightGroupClassifier(client, registry);

        StepVerifier.create(classifier.classify(createInsightType(), List.of(item), List.of()))
                .assertNext(groups -> assertThat(groups).containsKey("Meeting Coordination"))
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom insight group instruction");
    }

    private static MemoryInsightType createInsightType() {
        return new MemoryInsightType(
                1L,
                "experiences",
                "What is happening or has happened to the user.",
                null,
                List.of("event"),
                400,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final Object response;
        private List<ChatMessage> lastMessages = List.of();

        private FakeStructuredChatClient(Object response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            return Mono.just((T) response);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
