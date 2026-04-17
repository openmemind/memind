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
