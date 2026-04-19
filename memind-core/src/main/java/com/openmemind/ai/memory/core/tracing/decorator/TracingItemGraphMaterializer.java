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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Tracing decorator for post-commit graph materialization.
 */
public final class TracingItemGraphMaterializer extends TracingSupport
        implements ItemGraphMaterializer {

    private final ItemGraphMaterializer delegate;

    public TracingItemGraphMaterializer(ItemGraphMaterializer delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<ItemGraphMaterializationResult> materialize(
            MemoryId memoryId, List<MemoryItem> items, List<ExtractedMemoryEntry> sourceEntries) {
        return trace(
                MemorySpanNames.GRAPH_MATERIALIZE,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        memoryId.toIdentifier(),
                        MemoryAttributes.EXTRACTION_ITEM_COUNT,
                        items != null ? items.size() : 0),
                result ->
                        Map.ofEntries(
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_ENTITY_COUNT,
                                        result.stats().entityCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_MENTION_COUNT,
                                        result.stats().mentionCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT,
                                        result.stats().structuredItemLinkCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_SOURCE_COUNT,
                                        result.stats().temporalSourceCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_HISTORY_QUERY_BATCH_COUNT,
                                        result.stats().temporalHistoryQueryBatchCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_HISTORY_CANDIDATE_COUNT,
                                        result.stats().temporalHistoryCandidateCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_INTRA_BATCH_CANDIDATE_COUNT,
                                        result.stats().temporalIntraBatchCandidateCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_SELECTED_PAIR_COUNT,
                                        result.stats().temporalSelectedPairCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_CREATED_LINK_COUNT,
                                        result.stats().temporalCreatedLinkCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_QUERY_DURATION_MS,
                                        result.stats().temporalQueryDurationMs()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_BUILD_DURATION_MS,
                                        result.stats().temporalBuildDurationMs()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TEMPORAL_UPSERT_DURATION_MS,
                                        result.stats().temporalUpsertDurationMs()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_TEMPORAL_DEGRADED,
                                        result.stats().temporalDegraded()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_COUNT,
                                        result.stats().resolutionCandidateCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_SOURCE_DISTRIBUTION,
                                        result.stats().resolutionCandidateSourceSummary()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_SCORE_HISTOGRAM,
                                        result.stats().resolutionMergeScoreHistogramSummary()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_REJECTED_COUNT,
                                        result.stats().resolutionCandidateRejectedCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_MERGE_ACCEPTED_COUNT,
                                        result.stats().resolutionMergeAcceptedCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_MERGE_REJECTED_COUNT,
                                        result.stats().resolutionMergeRejectedCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_CREATE_NEW_COUNT,
                                        result.stats().resolutionCreateNewCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_EXACT_FALLBACK_COUNT,
                                        result.stats().resolutionExactFallbackCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_CAP_HIT_COUNT,
                                        result.stats().resolutionCandidateCapHitCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_ALIAS_EVIDENCE_OBSERVED_COUNT,
                                        result.stats().aliasEvidenceObservedCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_ALIAS_EVIDENCE_MERGED_COUNT,
                                        result.stats().aliasEvidenceMergedCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_RESOLUTION_SPECIAL_BYPASS_COUNT,
                                        result.stats().resolutionSpecialBypassCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SEARCH_REQUEST_COUNT,
                                        result.stats().semanticSearchRequestCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SEARCH_INVOCATION_COUNT,
                                        result.stats().semanticSearchInvocationCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_HIT_COUNT,
                                        result.stats().semanticSearchHitCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_RESOLVED_CANDIDATE_COUNT,
                                        result.stats().semanticResolvedCandidateCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_LINK_COUNT,
                                        result.stats().semanticLinkCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_UPSERT_BATCH_COUNT,
                                        result.stats().semanticUpsertBatchCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SOURCE_WINDOW_COUNT,
                                        result.stats().semanticSourceWindowCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_FAILED_RESOLVE_CHUNK_COUNT,
                                        result.stats().semanticFailedResolveChunkCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_FAILED_WINDOW_COUNT,
                                        result.stats().semanticFailedWindowCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_FAILED_UPSERT_BATCH_COUNT,
                                        result.stats().semanticFailedUpsertBatchCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SAME_BATCH_HIT_COUNT,
                                        result.stats().semanticSameBatchHitCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SEARCH_FALLBACK_COUNT,
                                        result.stats().semanticSearchFallbackCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT,
                                        result.stats().semanticIntraBatchCandidateCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_SEARCH_PHASE_DURATION_MS,
                                        result.stats().semanticSearchPhaseDurationMs()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_RESOLVE_PHASE_DURATION_MS,
                                        result.stats().semanticResolvePhaseDurationMs()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_UPSERT_PHASE_DURATION_MS,
                                        result.stats().semanticUpsertPhaseDurationMs()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS,
                                        result.stats().semanticIntraBatchPhaseDurationMs()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_DEGRADED,
                                        result.stats().semanticDegraded()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TYPE_FALLBACK_TO_OTHER_COUNT,
                                        result.stats().typeFallbackToOtherCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_TOP_UNRESOLVED_TYPE_LABELS,
                                        result.stats().topUnresolvedTypeLabelsSummary()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_DROPPED_BLANK_COUNT,
                                        result.stats().droppedBlankCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_DROPPED_PUNCTUATION_ONLY_COUNT,
                                        result.stats().droppedPunctuationOnlyCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_DROPPED_PRONOUN_LIKE_COUNT,
                                        result.stats().droppedPronounLikeCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_DROPPED_TEMPORAL_COUNT,
                                        result.stats().droppedTemporalCount()),
                                Map.entry(
                                        MemoryAttributes.EXTRACTION_GRAPH_DROPPED_DATE_LIKE_COUNT,
                                        result.stats().droppedDateLikeCount()),
                                Map.entry(
                                        MemoryAttributes
                                                .EXTRACTION_GRAPH_DROPPED_RESERVED_SPECIAL_COLLISION_COUNT,
                                        result.stats().droppedReservedSpecialCollisionCount())),
                () -> delegate.materialize(memoryId, items, sourceEntries));
    }
}
