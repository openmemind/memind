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
package com.openmemind.ai.memory.core.extraction.item.graph.pipeline.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for DefaultItemGraphMaterializer. */
public final class DefaultItemGraphMaterializerObservation {

    private DefaultItemGraphMaterializerObservation() {}

    public static Mono<ItemGraphMaterializationResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            List<MemoryItem> items,
            Supplier<Mono<ItemGraphMaterializationResult>> operation) {
        return observe(observationRegistry, memoryId, items, ignored -> operation.get());
    }

    public static Mono<ItemGraphMaterializationResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            List<MemoryItem> items,
            Function<GraphMaterializeObservationContext, Mono<ItemGraphMaterializationResult>>
                    operation) {
        return MemoryObservation.mono(
                observationRegistry,
                GraphMaterializeDocument.GRAPH_MATERIALIZE,
                GraphMaterializeConvention.INSTANCE,
                () -> new GraphMaterializeObservationContext(memoryId, items),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum GraphMaterializeDocument implements ObservationDocumentation {
        GRAPH_MATERIALIZE;

        @Override
        public String getName() {
            return "memind.graph.materialize";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return GraphMaterializeConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        MEMORY_ID {
            @Override
            public String asString() {
                return "memind.memory_id";
            }
        },
        EXTRACTION_ITEM_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.item_count";
            }
        },
        ENTITY_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.entity_count";
            }
        },
        MENTION_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.mention_count";
            }
        },
        STRUCTURED_LINK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.structured_link_count";
            }
        },
        TEMPORAL_SOURCE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_source_count";
            }
        },
        TEMPORAL_HISTORY_QUERY_BATCH_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_history_query_batch_count";
            }
        },
        TEMPORAL_HISTORY_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_history_candidate_count";
            }
        },
        TEMPORAL_INTRA_BATCH_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_intra_batch_candidate_count";
            }
        },
        TEMPORAL_SELECTED_PAIR_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_selected_pair_count";
            }
        },
        TEMPORAL_CREATED_LINK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_created_link_count";
            }
        },
        TEMPORAL_QUERY_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_query_duration_ms";
            }
        },
        TEMPORAL_BUILD_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_build_duration_ms";
            }
        },
        TEMPORAL_UPSERT_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_upsert_duration_ms";
            }
        },
        TEMPORAL_BELOW_RETRIEVAL_FLOOR_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_below_retrieval_floor_count";
            }
        },
        TEMPORAL_MIN_STRENGTH {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_min_strength";
            }
        },
        TEMPORAL_MAX_STRENGTH {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_max_strength";
            }
        },
        TEMPORAL_STRENGTH_BUCKET_SUMMARY {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_strength_bucket_summary";
            }
        },
        TEMPORAL_DEGRADED {
            @Override
            public String asString() {
                return "memind.extraction.graph.temporal_degraded";
            }
        },
        RESOLUTION_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_candidate_count";
            }
        },
        RESOLUTION_SOURCE_DISTRIBUTION {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_source_distribution";
            }
        },
        RESOLUTION_SCORE_HISTOGRAM {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_score_histogram";
            }
        },
        RESOLUTION_CANDIDATE_REJECTED_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_candidate_rejected_count";
            }
        },
        RESOLUTION_MERGE_ACCEPTED_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_merge_accepted_count";
            }
        },
        RESOLUTION_MERGE_REJECTED_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_merge_rejected_count";
            }
        },
        RESOLUTION_CREATE_NEW_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_create_new_count";
            }
        },
        RESOLUTION_EXACT_FALLBACK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_exact_fallback_count";
            }
        },
        RESOLUTION_CANDIDATE_CAP_HIT_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_candidate_cap_hit_count";
            }
        },
        ALIAS_EVIDENCE_OBSERVED_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.alias_evidence_observed_count";
            }
        },
        ALIAS_EVIDENCE_MERGED_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.alias_evidence_merged_count";
            }
        },
        RESOLUTION_SPECIAL_BYPASS_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.resolution_special_bypass_count";
            }
        },
        SEMANTIC_SEARCH_REQUEST_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_search_request_count";
            }
        },
        SEMANTIC_SEARCH_INVOCATION_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_search_invocation_count";
            }
        },
        SEMANTIC_SEARCH_HIT_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_search_hit_count";
            }
        },
        SEMANTIC_RESOLVED_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_resolved_candidate_count";
            }
        },
        SEMANTIC_LINK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_link_count";
            }
        },
        SEMANTIC_UPSERT_BATCH_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_upsert_batch_count";
            }
        },
        SEMANTIC_SOURCE_WINDOW_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_source_window_count";
            }
        },
        SEMANTIC_FAILED_RESOLVE_CHUNK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_failed_resolve_chunk_count";
            }
        },
        SEMANTIC_FAILED_WINDOW_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_failed_window_count";
            }
        },
        SEMANTIC_FAILED_UPSERT_BATCH_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_failed_upsert_batch_count";
            }
        },
        SEMANTIC_SAME_BATCH_HIT_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_same_batch_hit_count";
            }
        },
        SEMANTIC_SEARCH_FALLBACK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_search_fallback_count";
            }
        },
        SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_intra_batch_candidate_count";
            }
        },
        SEMANTIC_SEARCH_PHASE_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_search_phase_duration_ms";
            }
        },
        SEMANTIC_RESOLVE_PHASE_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_resolve_phase_duration_ms";
            }
        },
        SEMANTIC_UPSERT_PHASE_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_upsert_phase_duration_ms";
            }
        },
        SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_intra_batch_phase_duration_ms";
            }
        },
        SEMANTIC_DEGRADED {
            @Override
            public String asString() {
                return "memind.extraction.graph.semantic_degraded";
            }
        },
        TYPE_FALLBACK_TO_OTHER_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.type_fallback_to_other_count";
            }
        },
        TOP_UNRESOLVED_TYPE_LABELS {
            @Override
            public String asString() {
                return "memind.extraction.graph.top_unresolved_type_labels";
            }
        },
        DROPPED_BLANK_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_blank_count";
            }
        },
        DROPPED_PUNCTUATION_ONLY_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_punctuation_only_count";
            }
        },
        DROPPED_PRONOUN_LIKE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_pronoun_like_count";
            }
        },
        DROPPED_TEMPORAL_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_temporal_count";
            }
        },
        DROPPED_DATE_LIKE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_date_like_count";
            }
        },
        DROPPED_RESERVED_SPECIAL_COLLISION_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.graph.dropped_reserved_special_collision_count";
            }
        },
        STRUCTURED_BATCH_DEGRADED {
            @Override
            public String asString() {
                return "memind.extraction.graph.structured_batch_degraded";
            }
        };
    }

    public static final class GraphMaterializeObservationContext extends Observation.Context {

        private final MemoryId memoryId;
        private final int itemCount;

        public GraphMaterializeObservationContext(MemoryId memoryId, List<MemoryItem> items) {
            this.memoryId = memoryId;
            this.itemCount = items == null ? 0 : items.size();
        }

        public void recordResult(ItemGraphMaterializationResult result) {
            var stats = result.stats();
            add(HighCardinalityKeyNames.ENTITY_COUNT, stats.entityCount());
            add(HighCardinalityKeyNames.MENTION_COUNT, stats.mentionCount());
            add(HighCardinalityKeyNames.STRUCTURED_LINK_COUNT, stats.structuredItemLinkCount());
            add(HighCardinalityKeyNames.TEMPORAL_SOURCE_COUNT, stats.temporalSourceCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_HISTORY_QUERY_BATCH_COUNT,
                    stats.temporalHistoryQueryBatchCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_HISTORY_CANDIDATE_COUNT,
                    stats.temporalHistoryCandidateCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_INTRA_BATCH_CANDIDATE_COUNT,
                    stats.temporalIntraBatchCandidateCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_SELECTED_PAIR_COUNT,
                    stats.temporalSelectedPairCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_CREATED_LINK_COUNT,
                    stats.temporalCreatedLinkCount());
            add(
                    HighCardinalityKeyNames.TEMPORAL_QUERY_DURATION_MS,
                    stats.temporalQueryDurationMs());
            add(
                    HighCardinalityKeyNames.TEMPORAL_BUILD_DURATION_MS,
                    stats.temporalBuildDurationMs());
            add(
                    HighCardinalityKeyNames.TEMPORAL_UPSERT_DURATION_MS,
                    stats.temporalUpsertDurationMs());
            add(
                    HighCardinalityKeyNames.TEMPORAL_BELOW_RETRIEVAL_FLOOR_COUNT,
                    stats.temporalBelowRetrievalFloorCount());
            add(HighCardinalityKeyNames.TEMPORAL_MIN_STRENGTH, stats.temporalMinStrength());
            add(HighCardinalityKeyNames.TEMPORAL_MAX_STRENGTH, stats.temporalMaxStrength());
            add(
                    HighCardinalityKeyNames.TEMPORAL_STRENGTH_BUCKET_SUMMARY,
                    stats.temporalStrengthBucketSummary());
            add(HighCardinalityKeyNames.TEMPORAL_DEGRADED, stats.temporalDegraded());
            add(
                    HighCardinalityKeyNames.RESOLUTION_CANDIDATE_COUNT,
                    stats.resolutionCandidateCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_SOURCE_DISTRIBUTION,
                    stats.resolutionCandidateSourceSummary());
            add(
                    HighCardinalityKeyNames.RESOLUTION_SCORE_HISTOGRAM,
                    stats.resolutionMergeScoreHistogramSummary());
            add(
                    HighCardinalityKeyNames.RESOLUTION_CANDIDATE_REJECTED_COUNT,
                    stats.resolutionCandidateRejectedCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_MERGE_ACCEPTED_COUNT,
                    stats.resolutionMergeAcceptedCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_MERGE_REJECTED_COUNT,
                    stats.resolutionMergeRejectedCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_CREATE_NEW_COUNT,
                    stats.resolutionCreateNewCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_EXACT_FALLBACK_COUNT,
                    stats.resolutionExactFallbackCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_CANDIDATE_CAP_HIT_COUNT,
                    stats.resolutionCandidateCapHitCount());
            add(
                    HighCardinalityKeyNames.ALIAS_EVIDENCE_OBSERVED_COUNT,
                    stats.aliasEvidenceObservedCount());
            add(
                    HighCardinalityKeyNames.ALIAS_EVIDENCE_MERGED_COUNT,
                    stats.aliasEvidenceMergedCount());
            add(
                    HighCardinalityKeyNames.RESOLUTION_SPECIAL_BYPASS_COUNT,
                    stats.resolutionSpecialBypassCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SEARCH_REQUEST_COUNT,
                    stats.semanticSearchRequestCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SEARCH_INVOCATION_COUNT,
                    stats.semanticSearchInvocationCount());
            add(HighCardinalityKeyNames.SEMANTIC_SEARCH_HIT_COUNT, stats.semanticSearchHitCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_RESOLVED_CANDIDATE_COUNT,
                    stats.semanticResolvedCandidateCount());
            add(HighCardinalityKeyNames.SEMANTIC_LINK_COUNT, stats.semanticLinkCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_UPSERT_BATCH_COUNT,
                    stats.semanticUpsertBatchCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SOURCE_WINDOW_COUNT,
                    stats.semanticSourceWindowCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_FAILED_RESOLVE_CHUNK_COUNT,
                    stats.semanticFailedResolveChunkCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_FAILED_WINDOW_COUNT,
                    stats.semanticFailedWindowCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_FAILED_UPSERT_BATCH_COUNT,
                    stats.semanticFailedUpsertBatchCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SAME_BATCH_HIT_COUNT,
                    stats.semanticSameBatchHitCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SEARCH_FALLBACK_COUNT,
                    stats.semanticSearchFallbackCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT,
                    stats.semanticIntraBatchCandidateCount());
            add(
                    HighCardinalityKeyNames.SEMANTIC_SEARCH_PHASE_DURATION_MS,
                    stats.semanticSearchPhaseDurationMs());
            add(
                    HighCardinalityKeyNames.SEMANTIC_RESOLVE_PHASE_DURATION_MS,
                    stats.semanticResolvePhaseDurationMs());
            add(
                    HighCardinalityKeyNames.SEMANTIC_UPSERT_PHASE_DURATION_MS,
                    stats.semanticUpsertPhaseDurationMs());
            add(
                    HighCardinalityKeyNames.SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS,
                    stats.semanticIntraBatchPhaseDurationMs());
            add(HighCardinalityKeyNames.SEMANTIC_DEGRADED, stats.semanticDegraded());
            add(
                    HighCardinalityKeyNames.TYPE_FALLBACK_TO_OTHER_COUNT,
                    stats.typeFallbackToOtherCount());
            add(
                    HighCardinalityKeyNames.TOP_UNRESOLVED_TYPE_LABELS,
                    stats.topUnresolvedTypeLabelsSummary());
            add(HighCardinalityKeyNames.DROPPED_BLANK_COUNT, stats.droppedBlankCount());
            add(
                    HighCardinalityKeyNames.DROPPED_PUNCTUATION_ONLY_COUNT,
                    stats.droppedPunctuationOnlyCount());
            add(
                    HighCardinalityKeyNames.DROPPED_PRONOUN_LIKE_COUNT,
                    stats.droppedPronounLikeCount());
            add(HighCardinalityKeyNames.DROPPED_TEMPORAL_COUNT, stats.droppedTemporalCount());
            add(HighCardinalityKeyNames.DROPPED_DATE_LIKE_COUNT, stats.droppedDateLikeCount());
            add(
                    HighCardinalityKeyNames.DROPPED_RESERVED_SPECIAL_COLLISION_COUNT,
                    stats.droppedReservedSpecialCollisionCount());
            add(HighCardinalityKeyNames.STRUCTURED_BATCH_DEGRADED, stats.structuredBatchDegraded());
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class GraphMaterializeConvention
            implements ObservationConvention<GraphMaterializeObservationContext> {

        public static final GraphMaterializeConvention INSTANCE = new GraphMaterializeConvention();

        @Override
        public String getName() {
            return GraphMaterializeDocument.GRAPH_MATERIALIZE.getName();
        }

        @Override
        public String getContextualName(GraphMaterializeObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(GraphMaterializeObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()),
                    HighCardinalityKeyNames.EXTRACTION_ITEM_COUNT.withValue(
                            String.valueOf(context.itemCount)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof GraphMaterializeObservationContext;
        }
    }
}
