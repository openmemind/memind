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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ToolCallContentProcessor")
class ToolCallContentProcessorTest {

    private final ItemExtractionStrategy strategy = new NoopItemExtractionStrategy();
    private final ToolCallContentProcessor processor = new ToolCallContentProcessor(strategy);

    @Test
    @DisplayName("contentType() should return TOOL_CALL")
    void contentTypeIsToolCall() {
        assertThat(processor.contentType()).isEqualTo("TOOL_CALL");
    }

    @Test
    @DisplayName("supportsInsight() should return false")
    void supportsInsightIsFalse() {
        assertThat(processor.supportsInsight()).isFalse();
    }

    @Nested
    @DisplayName("chunk()")
    class ChunkTests {

        @Test
        @DisplayName("3 records with 2 distinct tools should produce 2 segments")
        void groupsByToolName() {
            var now = Instant.now();
            var records =
                    List.of(
                            new ToolCallRecord(
                                    "search", "{q:1}", "r1", "success", 100L, 10, 5, null, now),
                            new ToolCallRecord(
                                    "fetch", "{url:1}", "r2", "success", 200L, 20, 10, null, now),
                            new ToolCallRecord(
                                    "search", "{q:2}", "r3", "success", 150L, 15, 8, null, now));

            var content = new ToolCallContent(records);

            StepVerifier.create(processor.chunk(content))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(2);

                                // First segment: "search" tool (2 records)
                                var searchSegment = segments.get(0);
                                assertThat(searchSegment.metadata())
                                        .containsEntry("toolName", "search");
                                assertThat(searchSegment.metadata()).containsEntry("callCount", 2);

                                // Second segment: "fetch" tool (1 record)
                                var fetchSegment = segments.get(1);
                                assertThat(fetchSegment.metadata())
                                        .containsEntry("toolName", "fetch");
                                assertThat(fetchSegment.metadata()).containsEntry("callCount", 1);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty calls should produce empty segment list")
        void emptyCallsProduceEmptySegments() {
            var content = new ToolCallContent(List.of());

            StepVerifier.create(processor.chunk(content))
                    .assertNext(segments -> assertThat(segments).isEmpty())
                    .verifyComplete();
        }
    }

    private static final class NoopItemExtractionStrategy implements ItemExtractionStrategy {

        @Override
        public Mono<List<ExtractedMemoryEntry>> extract(
                List<ParsedSegment> segments,
                List<com.openmemind.ai.memory.core.data.MemoryInsightType> insightTypes,
                ItemExtractionConfig config) {
            return Mono.just(List.of());
        }
    }
}
