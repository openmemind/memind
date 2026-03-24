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
package com.openmemind.ai.memory.core.rawdata.caption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.LlmConversationCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.LlmConversationCaptionGenerator.CaptionResponse;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmConversationCaptionGenerator Unit Test")
class LlmConversationCaptionGeneratorTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw when structuredChatClient is null")
        void shouldThrowWhenStructuredChatClientIsNull() {
            assertThatThrownBy(() -> new LlmConversationCaptionGenerator(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("structuredChatClient must not be null");
        }
    }

    @Nested
    @DisplayName("generate method")
    class GenerateTests {

        @Test
        @DisplayName("Should call structured LLM client and compose title plus content")
        void shouldCallStructuredLlmClientAndComposeTitlePlusContent() {
            var response =
                    new CaptionResponse(
                            "User asked about the weather",
                            "The assistant answered that the weather was sunny.");
            var structuredLlmClient = new FakeStructuredChatClient(response);
            var generator = new LlmConversationCaptionGenerator(structuredLlmClient);
            var content = "user: How's the weather today?\nassistant: It's sunny today";
            Map<String, Object> metadata = Map.of("content_start_time", "2024-03-15T10:00:00");

            StepVerifier.create(generator.generate(content, metadata))
                    .assertNext(
                            caption ->
                                    assertThat(caption)
                                            .isEqualTo(
                                                    "User asked about the weather\n\n"
                                                            + "The assistant answered that the"
                                                            + " weather was sunny."))
                    .verifyComplete();

            var prompt = CaptionPrompts.build(content, metadata).render(null);
            assertThat(structuredLlmClient.lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("Blank content should return empty string without calling the LLM")
        void shouldReturnEmptyForBlankContent() {
            var structuredLlmClient = new FakeStructuredChatClient(null);
            var generator = new LlmConversationCaptionGenerator(structuredLlmClient);

            StepVerifier.create(generator.generate(" ", Map.of())).expectNext("").verifyComplete();

            assertThat(structuredLlmClient.lastMessages()).isEmpty();
        }

        @Test
        @DisplayName("Should return empty string when the LLM call fails")
        void shouldReturnEmptyStringWhenLlmCallFails() {
            var generator =
                    new LlmConversationCaptionGenerator(
                            new FakeStructuredChatClient(new RuntimeException("LLM unavailable")));

            StepVerifier.create(generator.generate("content", Map.of()))
                    .expectNext("")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("toCaption")
    class ToCaptionTests {

        @Test
        @DisplayName("Should join title and content with a blank line")
        void shouldJoinTitleAndContentWithBlankLine() {
            assertThat(
                            LlmConversationCaptionGenerator.toCaption(
                                    new CaptionResponse("Title", "Content")))
                    .isEqualTo("Title\n\nContent");
        }

        @Test
        @DisplayName("Null response should return empty string")
        void shouldReturnEmptyForNullResponse() {
            assertThat(LlmConversationCaptionGenerator.toCaption(null)).isEmpty();
        }
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
