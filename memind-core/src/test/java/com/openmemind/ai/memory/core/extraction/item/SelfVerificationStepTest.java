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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.test.StepVerifier;

/**
 * SelfVerificationStep unit test
 *
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SelfVerificationStep unit test")
class SelfVerificationStepTest {

    @Mock private ChatClient chatClient;

    @Mock private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock private ChatClient.CallResponseSpec callSpec;

    private SelfVerificationStep step;

    private static final String RAW_DATA_ID = "raw-001";

    private static final String ORIGINAL_TEXT =
            """
            user: My name is Zhang San, I am 30 years old, and I work as a backend developer in an internet company in Shanghai.
            user: I play badminton every Wednesday and Saturday, and I am currently preparing for the marathon in March next year.
            user: My girlfriend Xiao Li works as a frontend developer in the same company.
            """;

    @BeforeEach
    void setUp() {
        step = new SelfVerificationStep(chatClient);
    }

    // ==================== Missed Item Detection Tests ====================

    @Nested
    @DisplayName("Missed Item Detection")
    class MissedItemDetectionTests {

        @Test
        @DisplayName("Should return missed memory entries found by LLM")
        void shouldReturnMissedItems() {
            var existingEntries =
                    List.of(createEntry("The username is Zhang San, I am 30 years old"));

            var missedItems =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The user works as a backend developer in an internet company"
                                            + " in Shanghai",
                                    0.95f,
                                    null,
                                    List.of("profile"),
                                    null,
                                    null),
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The user's girlfriend Xiao Li works as a frontend developer in"
                                            + " the same company",
                                    0.9f,
                                    null,
                                    List.of("relationships"),
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(missedItems));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(2);
                                assertThat(result.getFirst().content())
                                        .isEqualTo(
                                                "The user works as a backend developer in an"
                                                        + " internet company in Shanghai");
                                assertThat(result.getFirst().rawDataId()).isEqualTo(RAW_DATA_ID);
                                assertThat(result.getLast().content())
                                        .isEqualTo(
                                                "The user's girlfriend Xiao Li works as a frontend"
                                                        + " developer in the same company");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should detect missed periodic behavior")
        void shouldDetectMissedPeriodicBehavior() {
            var existingEntries =
                    List.of(createEntry("The username is Zhang San, I am 30 years old"));

            var missedItems =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The user plays badminton every Wednesday and Saturday",
                                    0.95f,
                                    null,
                                    List.of("behavior"),
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(missedItems));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                assertThat(entry.content())
                                        .isEqualTo(
                                                "The user plays badminton every Wednesday and"
                                                        + " Saturday");
                            })
                    .verifyComplete();
        }
    }

    // ==================== Empty Result Tests ====================

    @Nested
    @DisplayName("No Missed Items Scenario")
    class EmptyResultTests {

        @Test
        @DisplayName("Should return an empty list when LLM returns empty items")
        void shouldReturnEmptyWhenNoMissedItems() {
            var existingEntries =
                    List.of(
                            createEntry("The username is Zhang San, I am 30 years old"),
                            createEntry("The user works as a backend developer in Shanghai"),
                            createEntry("The user plays badminton every Wednesday and Saturday"));

            setupMockChatClient(new MemoryItemExtractionResponse(List.of()));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(result -> assertThat(result).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns null response")
        void shouldReturnEmptyForNullResponse() {
            var existingEntries = List.of(createEntry("The username is Zhang San"));

            setupMockChatClient(null);

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(result -> assertThat(result).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return an empty list when LLM returns response with null items")
        void shouldReturnEmptyForNullItemsList() {
            var existingEntries = List.of(createEntry("The username is Zhang San"));

            setupMockChatClient(new MemoryItemExtractionResponse(null));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(result -> assertThat(result).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should gracefully degrade to return an empty list on LLM call exception")
        void shouldReturnEmptyOnException() {
            var existingEntries = List.of(createEntry("The username is Zhang San"));

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(anyString())).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenThrow(new RuntimeException("LLM API Error"));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(result -> assertThat(result).isEmpty())
                    .verifyComplete();
        }
    }

    // ==================== Field Mapping Tests ====================

    @Nested
    @DisplayName("Field Mapping")
    class FieldMappingTests {

        @Test
        @DisplayName("Should correctly map basic fields")
        void shouldMapBasicFieldsCorrectly() {
            var existingEntries = List.<ExtractedMemoryEntry>of();

            var items =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The username is Zhang San",
                                    1.0f,
                                    null,
                                    List.of("profile"),
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(items));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                assertThat(entry.content()).isEqualTo("The username is Zhang San");
                                assertThat(entry.confidence()).isEqualTo(1.0f);
                                assertThat(entry.insightTypes()).containsExactly("profile");
                                assertThat(entry.contentHash()).isNull();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Null fields should use default values")
        void shouldUseDefaultsForNullValues() {
            var existingEntries = List.<ExtractedMemoryEntry>of();

            var items =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The user works as a backend developer",
                                    0.8f,
                                    null,
                                    null,
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(items));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                assertThat(entry.insightTypes()).isEmpty();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should correctly clamp confidence to [0, 1] range")
        void shouldClampConfidence() {
            var existingEntries = List.<ExtractedMemoryEntry>of();

            var items =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "Out of range value", 1.5f, null, List.of(), null, null));

            setupMockChatClient(new MemoryItemExtractionResponse(items));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                assertThat(entry.confidence()).isEqualTo(1.0f);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should correctly parse valid occurredAt")
        void shouldParseValidTimestamps() {
            var existingEntries = List.<ExtractedMemoryEntry>of();

            var items =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "The user has an event next week",
                                    0.85f,
                                    "2026-02-20T10:00:00Z",
                                    List.of(),
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(items));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                assertThat(entry.occurredAt()).isNotNull();
                                assertThat(entry.occurredAt().toString())
                                        .isEqualTo("2026-02-20T10:00:00Z");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Invalid time strings should fallback to referenceTime")
        void shouldReturnNullForInvalidTimestamps() {
            var existingEntries = List.<ExtractedMemoryEntry>of();

            var items =
                    List.of(
                            new MemoryItemExtractionResponse.ExtractedItem(
                                    "Invalid time",
                                    0.8f,
                                    "not-a-valid-time",
                                    List.of(),
                                    null,
                                    null));

            setupMockChatClient(new MemoryItemExtractionResponse(items));

            StepVerifier.create(step.verify(ORIGINAL_TEXT, existingEntries, RAW_DATA_ID))
                    .assertNext(
                            result -> {
                                assertThat(result).hasSize(1);
                                var entry = result.getFirst();
                                // Invalid time fallback to referenceTime (default Instant.now())
                                assertThat(entry.occurredAt()).isNotNull();
                            })
                    .verifyComplete();
        }
    }

    // ==================== Helper Methods ====================

    private void setupMockChatClient(MemoryItemExtractionResponse response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(MemoryItemExtractionResponse.class)).thenReturn(response);
    }

    private static ExtractedMemoryEntry createEntry(String content) {
        return new ExtractedMemoryEntry(
                content, 0.9f, null, RAW_DATA_ID, null, List.of(), null, MemoryItemType.FACT, null);
    }
}
