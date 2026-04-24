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
package com.openmemind.ai.memory.core.tracing.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingItemGraphMaterializerTest {

    @Test
    void tracingDecoratorEmitsExpandedSemanticThroughputAndDegradationAttributes() {
        var observer = new RecordingMemoryObserver();
        var stats = semanticThroughputStats();
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .assertNext(result -> assertThat(result.stats().semanticDegraded()).isTrue())
                .verifyComplete();

        assertThat(observer.monoContexts())
                .extracting(ObservationContext::spanName)
                .contains(MemorySpanNames.GRAPH_MATERIALIZE);

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_ENTITY_COUNT, 2)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_MENTION_COUNT, 3)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_REQUEST_COUNT, 4)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_INVOCATION_COUNT, 4)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_HIT_COUNT, 8)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_RESOLVED_CANDIDATE_COUNT, 6)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_LINK_COUNT, 5)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_UPSERT_BATCH_COUNT, 2)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SOURCE_WINDOW_COUNT, 2)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_FAILED_RESOLVE_CHUNK_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_FAILED_WINDOW_COUNT, 1)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_FAILED_UPSERT_BATCH_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SAME_BATCH_HIT_COUNT, 3)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_FALLBACK_COUNT, 0)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT, 4)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_PHASE_DURATION_MS, 15L)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_RESOLVE_PHASE_DURATION_MS, 9L)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_UPSERT_PHASE_DURATION_MS, 6L)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS,
                        12L)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_DEGRADED, true);
    }

    @Test
    void tracingDecoratorEmitsStage3IntraBatchAttributes() {
        var observer = new RecordingMemoryObserver();
        var stats = semanticThroughputStats();
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT, 4)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS,
                        12L);
    }

    @Test
    void tracingDecoratorShouldEmitDedicatedTemporalAndRolloutSummaryAttributes() {
        var observer = new RecordingMemoryObserver();
        var stats =
                ItemGraphMaterializationResult.Stats.withTemporalAndSemantic(
                        2,
                        2,
                        1,
                        new TemporalItemLinker.TemporalLinkingStats(
                                2,
                                1,
                                3,
                                1,
                                1,
                                1,
                                4L,
                                3L,
                                2L,
                                0,
                                0.75d,
                                0.75d,
                                "0.75-0.89=1",
                                true),
                        EntityResolutionDiagnostics.empty(),
                        new SemanticItemLinker.SemanticLinkingStats(
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, false),
                        0,
                        "",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0);
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_SOURCE_COUNT, 2)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_CREATED_LINK_COUNT, 1)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_BELOW_RETRIEVAL_FLOOR_COUNT, 0)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_MIN_STRENGTH, 0.75d)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_MAX_STRENGTH, 0.75d)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_STRENGTH_BUCKET_SUMMARY,
                        "0.75-0.89=1")
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_DEGRADED, true);
    }

    @Test
    void tracingDecoratorEmitsStructuredBatchDegradedAttribute() {
        var observer = new RecordingMemoryObserver();
        var stats = semanticThroughputStats().withStructuredBatchDegraded(true);
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_STRUCTURED_BATCH_DEGRADED,
                        true);
    }

    @Test
    void tracingDecoratorShouldEmitStage1EntityHardeningCounters() {
        var observer = new RecordingMemoryObserver();
        var stats = stageOneStats(2, 2, 1, "未分类标签=1", 0, 1, 0, 1, 0, 1);
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_TYPE_FALLBACK_TO_OTHER_COUNT, 1)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_TOP_UNRESOLVED_TYPE_LABELS, "未分类标签=1")
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_DROPPED_PUNCTUATION_ONLY_COUNT, 1)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_DROPPED_RESERVED_SPECIAL_COLLISION_COUNT,
                        1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_DROPPED_TEMPORAL_COUNT, 1);
    }

    @Test
    void tracingDecoratorShouldEmitStage2ResolutionAttributes() {
        var observer = new RecordingMemoryObserver();
        var stats =
                stageTwoStats(
                        2,
                        2,
                        0,
                        3,
                        "exact_canonical_hit=1,safe_variant_hit=1",
                        "0.90-1.00=2",
                        1,
                        2,
                        1,
                        1,
                        4,
                        2,
                        3,
                        1,
                        1);
        ItemGraphMaterializer materializer =
                new TracingItemGraphMaterializer(
                        (memoryId, items, entries) ->
                                Mono.just(new ItemGraphMaterializationResult(stats)),
                        observer);

        StepVerifier.create(
                        materializer.materialize(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                List.of(newItem(101L)),
                                List.of(newEntry())))
                .expectNextCount(1)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((ObservationContext<ItemGraphMaterializationResult>)
                                observer.monoContexts().getFirst())
                        .resultExtractor()
                        .extract(new ItemGraphMaterializationResult(stats));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_COUNT, 3)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_SOURCE_DISTRIBUTION,
                        "exact_canonical_hit=1,safe_variant_hit=1")
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_SCORE_HISTOGRAM, "0.90-1.00=2")
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_REJECTED_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_MERGE_ACCEPTED_COUNT, 2)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_MERGE_REJECTED_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_CREATE_NEW_COUNT, 1)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_EXACT_FALLBACK_COUNT, 4)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_CAP_HIT_COUNT, 2)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_ALIAS_EVIDENCE_OBSERVED_COUNT, 3)
                .containsEntry(MemoryAttributes.EXTRACTION_GRAPH_ALIAS_EVIDENCE_MERGED_COUNT, 1)
                .containsEntry(
                        MemoryAttributes.EXTRACTION_GRAPH_RESOLUTION_SPECIAL_BYPASS_COUNT, 1);
    }

    private static MemoryItem newItem(Long id) {
        return new MemoryItem(
                id,
                "user-1:agent-1",
                "User discussed OpenAI deployment",
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-16T10:00:00Z"),
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry newEntry() {
        return new ExtractedMemoryEntry(
                "User discussed OpenAI deployment",
                1.0f,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                "raw-1",
                "hash-1",
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event");
    }

    private static ItemGraphMaterializationResult.Stats semanticThroughputStats() {
        return new ItemGraphMaterializationResult.Stats(
                2, 3, 1, 0, "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 8, 6, 5, 2, 2, 1, 1, 1, 3, 0,
                4, 15L, 9L, 6L, 12L, true, 0, "", 0, 0, 0, 0, 0, 0);
    }

    private static ItemGraphMaterializationResult.Stats stageTwoStats(
            int entityCount,
            int mentionCount,
            int structuredItemLinkCount,
            int resolutionCandidateCount,
            String resolutionCandidateSourceSummary,
            String resolutionMergeScoreHistogramSummary,
            int resolutionCandidateRejectedCount,
            int resolutionMergeAcceptedCount,
            int resolutionMergeRejectedCount,
            int resolutionCreateNewCount,
            int resolutionExactFallbackCount,
            int resolutionCandidateCapHitCount,
            int aliasEvidenceObservedCount,
            int aliasEvidenceMergedCount,
            int resolutionSpecialBypassCount) {
        return new ItemGraphMaterializationResult.Stats(
                entityCount,
                mentionCount,
                structuredItemLinkCount,
                resolutionCandidateCount,
                resolutionCandidateSourceSummary,
                resolutionMergeScoreHistogramSummary,
                resolutionCandidateRejectedCount,
                resolutionMergeAcceptedCount,
                resolutionMergeRejectedCount,
                resolutionCreateNewCount,
                resolutionExactFallbackCount,
                resolutionCandidateCapHitCount,
                aliasEvidenceObservedCount,
                aliasEvidenceMergedCount,
                resolutionSpecialBypassCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                false,
                0,
                "",
                0,
                0,
                0,
                0,
                0,
                0);
    }

    private static ItemGraphMaterializationResult.Stats stageOneStats(
            int entityCount,
            int mentionCount,
            int typeFallbackToOtherCount,
            String topUnresolvedTypeLabelsSummary,
            int droppedBlankCount,
            int droppedPunctuationOnlyCount,
            int droppedPronounLikeCount,
            int droppedTemporalCount,
            int droppedDateLikeCount,
            int droppedReservedSpecialCollisionCount) {
        return new ItemGraphMaterializationResult.Stats(
                entityCount,
                mentionCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                false,
                typeFallbackToOtherCount,
                topUnresolvedTypeLabelsSummary,
                droppedBlankCount,
                droppedPunctuationOnlyCount,
                droppedPronounLikeCount,
                droppedTemporalCount,
                droppedDateLikeCount,
                droppedReservedSpecialCollisionCount);
    }
}
