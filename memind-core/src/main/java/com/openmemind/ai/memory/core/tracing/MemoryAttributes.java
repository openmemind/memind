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
package com.openmemind.ai.memory.core.tracing;

/**
 * memind span attribute key constants
 *
 * <p>Follows the OpenTelemetry semantic convention naming style: {namespace}.{name}
 */
public final class MemoryAttributes {

    private MemoryAttributes() {}

    // ===== Common =====
    public static final String MEMORY_ID = "memind.memory_id";
    public static final String OPERATION = "memind.operation";

    // ===== Extraction =====
    public static final String EXTRACTION_LAYER = "memind.extraction.layer";
    public static final String EXTRACTION_CONTENT_TYPE = "memind.extraction.content_type";
    public static final String EXTRACTION_SEGMENT_COUNT = "memind.extraction.segment_count";
    public static final String EXTRACTION_ITEM_COUNT = "memind.extraction.item_count";
    public static final String EXTRACTION_NEW_ITEM_COUNT = "memind.extraction.new_item_count";
    public static final String EXTRACTION_REINFORCED_ITEM_COUNT =
            "memind.extraction.reinforced_item_count";
    public static final String EXTRACTION_INSIGHT_COUNT = "memind.extraction.insight_count";
    public static final String EXTRACTION_MODE = "memind.extraction.mode";
    public static final String EXTRACTION_CATEGORY_COUNT = "memind.extraction.category_count";
    public static final String EXTRACTION_EXISTED = "memind.extraction.existed";
    public static final String EXTRACTION_STATUS = "memind.extraction.status";
    public static final String EXTRACTION_DURATION_MS = "memind.extraction.duration_ms";
    public static final String EXTRACTION_GRAPH_ENTITY_COUNT =
            "memind.extraction.graph.entity_count";
    public static final String EXTRACTION_GRAPH_MENTION_COUNT =
            "memind.extraction.graph.mention_count";
    public static final String EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT =
            "memind.extraction.graph.structured_link_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_SOURCE_COUNT =
            "memind.extraction.graph.temporal_source_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_HISTORY_QUERY_BATCH_COUNT =
            "memind.extraction.graph.temporal_history_query_batch_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_HISTORY_CANDIDATE_COUNT =
            "memind.extraction.graph.temporal_history_candidate_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_INTRA_BATCH_CANDIDATE_COUNT =
            "memind.extraction.graph.temporal_intra_batch_candidate_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_SELECTED_PAIR_COUNT =
            "memind.extraction.graph.temporal_selected_pair_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_CREATED_LINK_COUNT =
            "memind.extraction.graph.temporal_created_link_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_QUERY_DURATION_MS =
            "memind.extraction.graph.temporal_query_duration_ms";
    public static final String EXTRACTION_GRAPH_TEMPORAL_BUILD_DURATION_MS =
            "memind.extraction.graph.temporal_build_duration_ms";
    public static final String EXTRACTION_GRAPH_TEMPORAL_UPSERT_DURATION_MS =
            "memind.extraction.graph.temporal_upsert_duration_ms";
    public static final String EXTRACTION_GRAPH_TEMPORAL_BELOW_RETRIEVAL_FLOOR_COUNT =
            "memind.extraction.graph.temporal_below_retrieval_floor_count";
    public static final String EXTRACTION_GRAPH_TEMPORAL_MIN_STRENGTH =
            "memind.extraction.graph.temporal_min_strength";
    public static final String EXTRACTION_GRAPH_TEMPORAL_MAX_STRENGTH =
            "memind.extraction.graph.temporal_max_strength";
    public static final String EXTRACTION_GRAPH_TEMPORAL_STRENGTH_BUCKET_SUMMARY =
            "memind.extraction.graph.temporal_strength_bucket_summary";
    public static final String EXTRACTION_GRAPH_TEMPORAL_DEGRADED =
            "memind.extraction.graph.temporal_degraded";
    public static final String EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_COUNT =
            "memind.extraction.graph.resolution_candidate_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_SOURCE_DISTRIBUTION =
            "memind.extraction.graph.resolution_source_distribution";
    public static final String EXTRACTION_GRAPH_RESOLUTION_SCORE_HISTOGRAM =
            "memind.extraction.graph.resolution_score_histogram";
    public static final String EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_REJECTED_COUNT =
            "memind.extraction.graph.resolution_candidate_rejected_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_MERGE_ACCEPTED_COUNT =
            "memind.extraction.graph.resolution_merge_accepted_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_MERGE_REJECTED_COUNT =
            "memind.extraction.graph.resolution_merge_rejected_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_CREATE_NEW_COUNT =
            "memind.extraction.graph.resolution_create_new_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_EXACT_FALLBACK_COUNT =
            "memind.extraction.graph.resolution_exact_fallback_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_CANDIDATE_CAP_HIT_COUNT =
            "memind.extraction.graph.resolution_candidate_cap_hit_count";
    public static final String EXTRACTION_GRAPH_ALIAS_EVIDENCE_OBSERVED_COUNT =
            "memind.extraction.graph.alias_evidence_observed_count";
    public static final String EXTRACTION_GRAPH_ALIAS_EVIDENCE_MERGED_COUNT =
            "memind.extraction.graph.alias_evidence_merged_count";
    public static final String EXTRACTION_GRAPH_RESOLUTION_SPECIAL_BYPASS_COUNT =
            "memind.extraction.graph.resolution_special_bypass_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SEARCH_REQUEST_COUNT =
            "memind.extraction.graph.semantic_search_request_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SEARCH_INVOCATION_COUNT =
            "memind.extraction.graph.semantic_search_invocation_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SEARCH_HIT_COUNT =
            "memind.extraction.graph.semantic_search_hit_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_RESOLVED_CANDIDATE_COUNT =
            "memind.extraction.graph.semantic_resolved_candidate_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_LINK_COUNT =
            "memind.extraction.graph.semantic_link_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_UPSERT_BATCH_COUNT =
            "memind.extraction.graph.semantic_upsert_batch_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SOURCE_WINDOW_COUNT =
            "memind.extraction.graph.semantic_source_window_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_FAILED_RESOLVE_CHUNK_COUNT =
            "memind.extraction.graph.semantic_failed_resolve_chunk_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_FAILED_WINDOW_COUNT =
            "memind.extraction.graph.semantic_failed_window_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_FAILED_UPSERT_BATCH_COUNT =
            "memind.extraction.graph.semantic_failed_upsert_batch_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SAME_BATCH_HIT_COUNT =
            "memind.extraction.graph.semantic_same_batch_hit_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SEARCH_FALLBACK_COUNT =
            "memind.extraction.graph.semantic_search_fallback_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_CANDIDATE_COUNT =
            "memind.extraction.graph.semantic_intra_batch_candidate_count";
    public static final String EXTRACTION_GRAPH_SEMANTIC_SEARCH_PHASE_DURATION_MS =
            "memind.extraction.graph.semantic_search_phase_duration_ms";
    public static final String EXTRACTION_GRAPH_SEMANTIC_RESOLVE_PHASE_DURATION_MS =
            "memind.extraction.graph.semantic_resolve_phase_duration_ms";
    public static final String EXTRACTION_GRAPH_SEMANTIC_UPSERT_PHASE_DURATION_MS =
            "memind.extraction.graph.semantic_upsert_phase_duration_ms";
    public static final String EXTRACTION_GRAPH_SEMANTIC_INTRA_BATCH_PHASE_DURATION_MS =
            "memind.extraction.graph.semantic_intra_batch_phase_duration_ms";
    public static final String EXTRACTION_GRAPH_SEMANTIC_DEGRADED =
            "memind.extraction.graph.semantic_degraded";
    public static final String EXTRACTION_GRAPH_TYPE_FALLBACK_TO_OTHER_COUNT =
            "memind.extraction.graph.type_fallback_to_other_count";
    public static final String EXTRACTION_GRAPH_TOP_UNRESOLVED_TYPE_LABELS =
            "memind.extraction.graph.top_unresolved_type_labels";
    public static final String EXTRACTION_GRAPH_DROPPED_BLANK_COUNT =
            "memind.extraction.graph.dropped_blank_count";
    public static final String EXTRACTION_GRAPH_DROPPED_PUNCTUATION_ONLY_COUNT =
            "memind.extraction.graph.dropped_punctuation_only_count";
    public static final String EXTRACTION_GRAPH_DROPPED_PRONOUN_LIKE_COUNT =
            "memind.extraction.graph.dropped_pronoun_like_count";
    public static final String EXTRACTION_GRAPH_DROPPED_TEMPORAL_COUNT =
            "memind.extraction.graph.dropped_temporal_count";
    public static final String EXTRACTION_GRAPH_DROPPED_DATE_LIKE_COUNT =
            "memind.extraction.graph.dropped_date_like_count";
    public static final String EXTRACTION_GRAPH_DROPPED_RESERVED_SPECIAL_COLLISION_COUNT =
            "memind.extraction.graph.dropped_reserved_special_collision_count";
    public static final String EXTRACTION_GRAPH_STRUCTURED_BATCH_DEGRADED =
            "memind.extraction.graph.structured_batch_degraded";

    // ===== Extraction (Insight Tree) =====
    public static final String EXTRACTION_INSIGHT_TYPE = "memind.extraction.insight_type";
    public static final String EXTRACTION_INSIGHT_TIER = "memind.extraction.insight_tier";
    public static final String EXTRACTION_INSIGHT_GROUP_NAME =
            "memind.extraction.insight_group_name";
    public static final String EXTRACTION_INSIGHT_GROUP_COUNT =
            "memind.extraction.insight_group_count";
    public static final String EXTRACTION_INSIGHT_POINT_COUNT =
            "memind.extraction.insight_point_count";
    public static final String EXTRACTION_INSIGHT_LEAF_COUNT =
            "memind.extraction.insight_leaf_count";
    public static final String EXTRACTION_INSIGHT_ADD_COUNT = "memind.extraction.insight_add_count";
    public static final String EXTRACTION_INSIGHT_UPDATE_COUNT =
            "memind.extraction.insight_update_count";
    public static final String EXTRACTION_INSIGHT_DELETE_COUNT =
            "memind.extraction.insight_delete_count";

    // ===== Retrieval =====
    public static final String RETRIEVAL_STRATEGY = "memind.retrieval.strategy";
    public static final String RETRIEVAL_QUERY = "memind.retrieval.query";
    public static final String RETRIEVAL_REWRITTEN_QUERY = "memind.retrieval.rewritten_query";
    public static final String RETRIEVAL_INTENT = "memind.retrieval.intent";
    public static final String RETRIEVAL_TIER = "memind.retrieval.sufficiency";
    public static final String RETRIEVAL_RESULT_COUNT = "memind.retrieval.result_count";
    public static final String RETRIEVAL_SUFFICIENT = "memind.retrieval.sufficient";
    public static final String RETRIEVAL_CACHE_HIT = "memind.retrieval.cache_hit";
    public static final String RETRIEVAL_TOP_K = "memind.retrieval.top_k";
    public static final String RETRIEVAL_MEMORY_THREAD_ENABLED =
            "memind.retrieval.memory_thread.enabled";
    public static final String RETRIEVAL_MEMORY_THREAD_SEED_THREAD_COUNT =
            "memind.retrieval.memory_thread.seed_thread_count";
    public static final String RETRIEVAL_MEMORY_THREAD_CANDIDATE_COUNT =
            "memind.retrieval.memory_thread.candidate_count";
    public static final String RETRIEVAL_MEMORY_THREAD_ADMITTED_COUNT =
            "memind.retrieval.memory_thread.admitted_count";
    public static final String RETRIEVAL_MEMORY_THREAD_CLAMPED =
            "memind.retrieval.memory_thread.clamped";
    public static final String RETRIEVAL_MEMORY_THREAD_DEGRADED =
            "memind.retrieval.memory_thread.degraded";
    public static final String RETRIEVAL_MEMORY_THREAD_TIMEOUT =
            "memind.retrieval.memory_thread.timeout";
    public static final String RETRIEVAL_GRAPH_ENABLED = "memind.retrieval.graph.enabled";
    public static final String RETRIEVAL_GRAPH_SEED_COUNT = "memind.retrieval.graph.seed_count";
    public static final String RETRIEVAL_GRAPH_LINK_EXPANSION_COUNT =
            "memind.retrieval.graph.link_expansion_count";
    public static final String RETRIEVAL_GRAPH_ENTITY_EXPANSION_COUNT =
            "memind.retrieval.graph.entity_expansion_count";
    public static final String RETRIEVAL_GRAPH_DEDUPED_CANDIDATE_COUNT =
            "memind.retrieval.graph.deduped_candidate_count";
    public static final String RETRIEVAL_GRAPH_ADMITTED_CANDIDATE_COUNT =
            "memind.retrieval.graph.admitted_candidate_count";
    public static final String RETRIEVAL_GRAPH_DISPLACED_DIRECT_COUNT =
            "memind.retrieval.graph.displaced_direct_count";
    public static final String RETRIEVAL_GRAPH_OVERLAP_COUNT =
            "memind.retrieval.graph.overlap_count";
    public static final String RETRIEVAL_GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT =
            "memind.retrieval.graph.skipped_overfanout_entity_count";
    public static final String RETRIEVAL_GRAPH_TIMEOUT = "memind.retrieval.graph.timeout";
    public static final String RETRIEVAL_GRAPH_DEGRADED = "memind.retrieval.graph.degraded";
}
