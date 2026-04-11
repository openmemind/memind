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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.item.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.support.ToolItemResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmToolCallItemExtractionStrategy Unit Test")
class LlmToolCallItemExtractionStrategyTest {

    @Test
    @DisplayName("extract should use tool item override instruction")
    void extractShouldUseToolItemOverrideInstruction() {
        var client = new FakeStructuredChatClient(new ToolItemResponse("Tool usage insight", 1.0));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.TOOL_ITEM, "Custom tool item instruction")
                        .build();
        var strategy = new LlmToolCallItemExtractionStrategy(client, registry);

        StepVerifier.create(
                        strategy.extract(
                                List.of(sampleSegment()),
                                List.of(),
                                ItemExtractionConfig.defaults()))
                .assertNext(entries -> assertThat(entries).hasSize(1))
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom tool item instruction");
    }

    private static ParsedSegment sampleSegment() {
        return new ParsedSegment(
                "tool-call summary",
                null,
                0,
                1,
                "raw-1",
                Map.of(
                        "toolName",
                        "git",
                        "callCount",
                        1,
                        "successCount",
                        1,
                        "failCount",
                        0,
                        "avgDurationMs",
                        "10",
                        "records",
                        List.of(
                                Map.of(
                                        "status",
                                        "success",
                                        "durationMs",
                                        10,
                                        "input",
                                        "git status",
                                        "output",
                                        "clean"))));
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
            return Mono.justOrEmpty((T) response);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
