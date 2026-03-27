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
package com.openmemind.ai.memory.core.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.item.SelfVerificationPrompts;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmSelfVerificationStep Unit Test")
class LlmSelfVerificationStepTest {

    private static final String RAW_DATA_ID = "raw-001";
    private static final String ORIGINAL_TEXT =
            """
            user: My name is Zhang San, I am 30 years old, and I work as a backend developer in an internet company in Shanghai.
            user: I play badminton every Wednesday and Saturday, and I am currently preparing for the marathon in March next year.
            user: My girlfriend Xiao Li works as a frontend developer in the same company.
            """;

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw when structuredChatClient is null")
        void shouldThrowWhenStructuredChatClientIsNull() {
            assertThatThrownBy(() -> new LlmSelfVerificationStep(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("structuredChatClient must not be null");
        }
    }

    @Nested
    @DisplayName("verify method")
    class VerifyTests {

        @Test
        @DisplayName("Should return missed memory entries found by the LLM")
        void shouldReturnMissedMemoryEntriesFoundByTheLlm() {
            var existingEntries =
                    List.of(createEntry("The username is Zhang San, I am 30 years old"));
            var referenceTime = Instant.parse("2024-03-15T10:00:00Z");
            var response =
                    new MemoryItemExtractionResponse(
                            List.of(
                                    new MemoryItemExtractionResponse.ExtractedItem(
                                            "The user works as a backend developer in an internet"
                                                    + " company in Shanghai",
                                            0.95f,
                                            null,
                                            List.of("profile"),
                                            null,
                                            null),
                                    new MemoryItemExtractionResponse.ExtractedItem(
                                            "The user's girlfriend Xiao Li works as a frontend"
                                                    + " developer in the same company",
                                            0.9f,
                                            null,
                                            List.of("relationships"),
                                            null,
                                            null)));
            var structuredLlmClient = new FakeStructuredChatClient(response);
            var step = new LlmSelfVerificationStep(structuredLlmClient);

            StepVerifier.create(
                            step.verify(
                                    ORIGINAL_TEXT,
                                    existingEntries,
                                    RAW_DATA_ID,
                                    referenceTime,
                                    List.of(),
                                    null,
                                    null))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(2);
                                assertThat(result.getFirst().rawDataId()).isEqualTo(RAW_DATA_ID);
                                assertThat(result.getLast().content())
                                        .contains("frontend developer");
                            })
                    .verifyComplete();

            var prompt =
                    SelfVerificationPrompts.build(
                                    ORIGINAL_TEXT,
                                    existingEntries,
                                    referenceTime,
                                    List.of(),
                                    null,
                                    null)
                            .render(null);
            assertThat(structuredLlmClient.lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("Should return an empty list when the LLM call fails")
        void shouldReturnEmptyListWhenTheLlmCallFails() {
            var step =
                    new LlmSelfVerificationStep(
                            new FakeStructuredChatClient(new RuntimeException("LLM unavailable")));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, List.of(), RAW_DATA_ID))
                    .expectNext(List.of())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should render self verification prompt with explicit language")
        void shouldRenderSelfVerificationPromptWithExplicitLanguage() {
            var existingEntries =
                    List.of(createEntry("The username is Zhang San, I am 30 years old"));
            var referenceTime = Instant.parse("2024-03-15T10:00:00Z");
            var structuredLlmClient =
                    new FakeStructuredChatClient(new MemoryItemExtractionResponse(List.of()));
            var step = new LlmSelfVerificationStep(structuredLlmClient);

            StepVerifier.create(
                            step.verify(
                                    ORIGINAL_TEXT,
                                    existingEntries,
                                    RAW_DATA_ID,
                                    referenceTime,
                                    List.of(),
                                    null,
                                    null,
                                    "zh-CN"))
                    .expectNext(List.of())
                    .verifyComplete();

            var prompt =
                    SelfVerificationPrompts.build(
                                    ORIGINAL_TEXT,
                                    existingEntries,
                                    referenceTime,
                                    List.of(),
                                    null,
                                    null)
                            .render("zh-CN");
            assertThat(structuredLlmClient.lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("Should clamp confidence and default insight types")
        void shouldClampConfidenceAndDefaultInsightTypes() {
            var response =
                    new MemoryItemExtractionResponse(
                            List.of(
                                    new MemoryItemExtractionResponse.ExtractedItem(
                                            "Out of range value", 1.5f, null, null, null, null)));
            var step = new LlmSelfVerificationStep(new FakeStructuredChatClient(response));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, List.of(), RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                assertThat(result.getFirst().confidence()).isEqualTo(1.0f);
                                assertThat(result.getFirst().insightTypes()).isEmpty();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should keep occurredAt null when the review result has no semantic time")
        void shouldKeepOccurredAtNullWhenTheReviewResultHasNoSemanticTime() {
            var response =
                    new MemoryItemExtractionResponse(
                            List.of(
                                    new MemoryItemExtractionResponse.ExtractedItem(
                                            "User tends to blame themselves when facing trusted"
                                                    + " people",
                                            0.95f,
                                            null,
                                            List.of("behavior"),
                                            null,
                                            "behavior")));
            var step = new LlmSelfVerificationStep(new FakeStructuredChatClient(response));

            StepVerifier.create(
                            step.verify(
                                    ORIGINAL_TEXT,
                                    List.of(),
                                    RAW_DATA_ID,
                                    Instant.parse("2026-03-27T02:18:00Z"),
                                    List.of(),
                                    null,
                                    null))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                assertThat(result.getFirst().occurredAt()).isNull();
                            })
                    .verifyComplete();
        }
    }

    private static ExtractedMemoryEntry createEntry(String content) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                Instant.parse("2024-03-15T10:00:00Z"),
                RAW_DATA_ID,
                null,
                List.of(),
                null,
                null,
                null);
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final Object response;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private FakeStructuredChatClient(Object response) {
            this.response = response;
            this.error = null;
        }

        private FakeStructuredChatClient(RuntimeException error) {
            this.response = null;
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.justOrEmpty((T) response);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
