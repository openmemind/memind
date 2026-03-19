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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.ConversationCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.ConversationCaptionGenerator.CaptionResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationCaptionGenerator Unit Test")
class ConversationCaptionGeneratorTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponse;

    private ConversationCaptionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ConversationCaptionGenerator(chatClient);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("chatClient is null should throw exception")
        void shouldThrowWhenChatClientIsNull() {
            assertThatThrownBy(() -> new ConversationCaptionGenerator(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chatClient must not be null");
        }
    }

    @Nested
    @DisplayName("generate method")
    class GenerateTests {

        @Test
        @DisplayName("should call LLM and parse structured output to title + content format")
        void shouldCallLlmAndParseStructuredCaption() {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponse);
            when(callResponse.entity(CaptionResponse.class))
                    .thenReturn(
                            new CaptionResponse(
                                    "User inquired about the weather",
                                    "The user asked the assistant about the weather for the day."
                                        + " The assistant replied that the weather was sunny."));

            StepVerifier.create(
                            generator.generate(
                                    "user: How's the weather today?\nassistant: It's sunny today",
                                    Map.of()))
                    .assertNext(
                            caption -> {
                                assertThat(caption).startsWith("User inquired about the weather");
                                assertThat(caption).contains("\n\n");
                                assertThat(caption)
                                        .contains(
                                                "The assistant replied that the weather was sunny");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should include content in prompt")
        void shouldIncludeContentInPrompt() {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponse);
            when(callResponse.entity(CaptionResponse.class))
                    .thenReturn(new CaptionResponse("Title", "Content"));

            ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);

            StepVerifier.create(generator.generate("Test content", Map.of()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(requestSpec).user(userPromptCaptor.capture());
            assertThat(userPromptCaptor.getValue()).contains("Test content");
        }

        @Test
        @DisplayName("when metadata contains content_start_time should inject into prompt")
        void shouldInjectStartTimeFromMetadata() {
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponse);
            when(callResponse.entity(CaptionResponse.class))
                    .thenReturn(new CaptionResponse("Title", "Content"));

            ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);

            StepVerifier.create(
                            generator.generate(
                                    "Test content",
                                    Map.of("content_start_time", "2024-03-15T10:00:00")))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(requestSpec).user(userPromptCaptor.capture());
            assertThat(userPromptCaptor.getValue()).contains("2024-03-15T10:00:00");
        }

        @Test
        @DisplayName("blank content should directly return empty string")
        void shouldReturnEmptyForBlankContent() {
            StepVerifier.create(generator.generate("", Map.of()))
                    .assertNext(caption -> assertThat(caption).isEmpty())
                    .verifyComplete();

            verify(chatClient, never()).prompt();
        }

        @Test
        @DisplayName("null content should directly return empty string")
        void shouldReturnEmptyForNullContent() {
            StepVerifier.create(generator.generate(null, Map.of()))
                    .assertNext(caption -> assertThat(caption).isEmpty())
                    .verifyComplete();

            verify(chatClient, never()).prompt();
        }
    }

    @Nested
    @DisplayName("toCaption structured response conversion")
    class ToCaptionTests {

        @Test
        @DisplayName("valid response should convert to title + content")
        void shouldConvertValidResponse() {
            var result =
                    ConversationCaptionGenerator.toCaption(
                            new CaptionResponse(
                                    "Meeting discussion on project progress",
                                    "The team discussed the project progress in the meeting and"
                                            + " decided to extend the deadline by two weeks."));

            assertThat(result)
                    .isEqualTo(
                            "Meeting discussion on project progress\n\n"
                                    + "The team discussed the project progress in the meeting and"
                                    + " decided to extend the deadline by two weeks.");
        }

        @Test
        @DisplayName("null response should return empty string")
        void shouldReturnEmptyForNullResponse() {
            assertThat(ConversationCaptionGenerator.toCaption(null)).isEmpty();
        }

        @Test
        @DisplayName("only title should not contain empty line")
        void shouldHandleTitleOnly() {
            var result = ConversationCaptionGenerator.toCaption(new CaptionResponse("Title", ""));
            assertThat(result).isEqualTo("Title");
        }

        @Test
        @DisplayName("only content should not contain empty line")
        void shouldHandleContentOnly() {
            var result =
                    ConversationCaptionGenerator.toCaption(
                            new CaptionResponse("", "Content description"));
            assertThat(result).isEqualTo("Content description");
        }

        @Test
        @DisplayName("both title and content are null should return empty string")
        void shouldHandleBothNull() {
            var result = ConversationCaptionGenerator.toCaption(new CaptionResponse(null, null));
            assertThat(result).isEmpty();
        }
    }
}
