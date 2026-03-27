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
package com.openmemind.ai.memory.core.extraction.item.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LlmItemExtractionStrategy")
class LlmItemExtractionStrategyTest {

    @Test
    @DisplayName("resolveOccurredAt should return null when LLM omits the time")
    void resolveOccurredAtShouldReturnNullWhenLlmOmitsTheTime() {
        assertThat(LlmItemExtractionStrategy.resolveOccurredAt(null)).isNull();
        assertThat(LlmItemExtractionStrategy.resolveOccurredAt("  ")).isNull();
    }

    @Test
    @DisplayName("resolveOccurredAt should parse LLM provided ISO timestamp")
    void resolveOccurredAtShouldParseLlmProvidedIsoTimestamp() {
        Instant occurredAt = LlmItemExtractionStrategy.resolveOccurredAt("2026-03-27T02:18:00Z");

        assertThat(occurredAt).isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
    }

    @Test
    @DisplayName("resolveObservedAt should use the latest source message timestamp")
    void resolveObservedAtShouldUseTheLatestSourceMessageTimestamp() {
        var segment =
                new ParsedSegment(
                        "text",
                        null,
                        0,
                        1,
                        "raw-1",
                        Map.of(
                                "messages",
                                List.of(
                                        Message.user(
                                                "hello",
                                                Instant.parse("2026-03-27T02:17:00Z"),
                                                "User"),
                                        new Message(
                                                Message.Role.ASSISTANT,
                                                List.of(),
                                                Instant.parse("2026-03-27T02:18:00Z"),
                                                null))));

        assertThat(LlmItemExtractionStrategy.resolveObservedAt(segment))
                .isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
    }

    @Test
    @DisplayName("resolveObservedAt should return null when source messages have no timestamp")
    void resolveObservedAtShouldReturnNullWhenSourceMessagesHaveNoTimestamp() {
        var segment = new ParsedSegment("text", null, 0, 1, "raw-1", Map.of());

        assertThat(LlmItemExtractionStrategy.resolveObservedAt(segment)).isNull();
    }

    @Test
    @DisplayName("resolveUserName should ignore assistant-role userName values")
    void resolveUserNameShouldIgnoreAssistantRoleUserNameValues() {
        var segment =
                new ParsedSegment(
                        "text",
                        null,
                        0,
                        1,
                        "raw-1",
                        Map.of(
                                "messages",
                                List.of(
                                        new Message(
                                                Message.Role.ASSISTANT,
                                                List.of(),
                                                Instant.parse("2024-03-15T10:00:00Z"),
                                                "AssistantAlias"),
                                        Message.user(
                                                "hello",
                                                Instant.parse("2024-03-15T10:01:00Z"),
                                                "RealUser"))));

        assertThat(LlmItemExtractionStrategy.resolveUserName(segment)).isEqualTo("RealUser");
    }
}
