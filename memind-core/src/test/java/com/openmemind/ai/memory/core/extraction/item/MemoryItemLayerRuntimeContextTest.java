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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MemoryItemLayerRuntimeContextTest {

    @Test
    void selfVerificationUsesFirstUserNameAndLastObservedAtFromSegments() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        var selfVerificationStep = new CapturingSelfVerificationStep();
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, selfVerificationStep);

        var first =
                new ParsedSegment(
                        "seg-1",
                        "caption-1",
                        0,
                        1,
                        "raw-1",
                        java.util.Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-03-27T02:17:00Z"),
                                Instant.parse("2026-03-27T02:18:00Z"),
                                "Alice"));
        var second =
                new ParsedSegment(
                        "seg-2",
                        "caption-2",
                        1,
                        2,
                        "raw-2",
                        java.util.Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-03-27T02:19:00Z"),
                                Instant.parse("2026-03-27T02:20:00Z"),
                                null));
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        ConversationContent.TYPE,
                        MemoryCategory.userCategories(),
                        false,
                        "zh-CN");
        var entry =
                new ExtractedMemoryEntry(
                        "hello",
                        1.0f,
                        null,
                        Instant.parse("2026-03-27T02:18:00Z"),
                        "raw-1",
                        null,
                        List.of(),
                        java.util.Map.of(),
                        null,
                        null);

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(first, second)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(), List.of())));
        when(deduplicator.spanName()).thenReturn("test");

        StepVerifier.create(
                        layer.extract(
                                DefaultMemoryId.of("user1", "agent1"),
                                new RawDataResult(List.of(), List.of(first, second), false),
                                config))
                .assertNext(result -> assertThat(result.isEmpty()).isTrue())
                .verifyComplete();

        assertThat(selfVerificationStep.capturedUserName()).isEqualTo("Alice");
        assertThat(selfVerificationStep.capturedObservedAt())
                .isEqualTo(Instant.parse("2026-03-27T02:20:00Z"));
    }

    private static final class CapturingSelfVerificationStep extends LlmSelfVerificationStep {

        private String capturedUserName;
        private Instant capturedObservedAt;

        private CapturingSelfVerificationStep() {
            super(mock(StructuredChatClient.class));
        }

        @Override
        public Mono<List<ExtractedMemoryEntry>> verify(
                String originalText,
                List<ExtractedMemoryEntry> existingEntries,
                String rawDataId,
                Instant referenceTime,
                List<MemoryInsightType> insightTypes,
                String userName,
                Set<MemoryCategory> categories,
                String language,
                Instant observedAt) {
            capturedUserName = userName;
            capturedObservedAt = observedAt;
            return Mono.just(List.of());
        }

        private String capturedUserName() {
            return capturedUserName;
        }

        private Instant capturedObservedAt() {
            return capturedObservedAt;
        }
    }
}
