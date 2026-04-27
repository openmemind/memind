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
 * memind span name constants
 */
public final class MemorySpanNames {

    private MemorySpanNames() {}

    // ===== Extraction =====
    public static final String EXTRACTION = "memind.extraction";
    public static final String EXTRACTION_RAWDATA = "memind.extraction.rawdata";
    public static final String EXTRACTION_RAWDATA_NORMALIZE = "memind.extraction.rawdata.normalize";
    public static final String EXTRACTION_RAWDATA_CHUNK = "memind.extraction.rawdata.chunk";
    public static final String EXTRACTION_RAWDATA_CAPTION = "memind.extraction.rawdata.caption";
    public static final String EXTRACTION_RAWDATA_VECTORIZE = "memind.extraction.rawdata.vectorize";
    public static final String EXTRACTION_ITEM = "memind.extraction.item";
    public static final String EXTRACTION_ITEM_EXTRACT = "memind.extraction.item.extract";
    public static final String EXTRACTION_ITEM_DEDUP = "memind.extraction.item.dedup";
    public static final String EXTRACTION_ITEM_SEMANTIC_DEDUP =
            "memind.extraction.item.semantic_dedup";
    public static final String EXTRACTION_ITEM_VECTORIZE = "memind.extraction.item.vectorize";
    public static final String EXTRACTION_INSIGHT = "memind.extraction.insight";
    public static final String EXTRACTION_INSIGHT_GENERATE = "memind.extraction.insight.generate";

    // ===== Insight Tree =====
    public static final String EXTRACTION_INSIGHT_PIPELINE = "memind.extraction.insight.pipeline";
    public static final String EXTRACTION_INSIGHT_GROUP_CLASSIFY =
            "memind.extraction.insight.group.classify";
    public static final String EXTRACTION_INSIGHT_GENERATE_LEAF =
            "memind.extraction.insight.generate.leaf";
    public static final String EXTRACTION_INSIGHT_GENERATE_BRANCH =
            "memind.extraction.insight.generate.branch";
    public static final String EXTRACTION_INSIGHT_GENERATE_ROOT =
            "memind.extraction.insight.generate.root";
    public static final String EXTRACTION_INSIGHT_TREE_REORGANIZE =
            "memind.extraction.insight.tree.reorganize";

    // ===== Retrieval =====
    public static final String RETRIEVAL = "memind.retrieval";
    public static final String RETRIEVAL_INTENT = "memind.retrieval.intent";
    public static final String RETRIEVAL_REWRITE = "memind.retrieval.rewrite";
    public static final String RETRIEVAL_STRATEGY = "memind.retrieval.strategy";
    public static final String RETRIEVAL_TIER_INSIGHT = "memind.retrieval.tier.insight";
    public static final String RETRIEVAL_TIER_ITEM = "memind.retrieval.tier.item";
    public static final String RETRIEVAL_TIER_RAWDATA = "memind.retrieval.tier.rawdata";
    public static final String RETRIEVAL_SUFFICIENCY = "memind.retrieval.sufficiency";
    public static final String RETRIEVAL_VECTOR_SEARCH = "memind.retrieval.vector_search";
    public static final String RETRIEVAL_RESULT_MERGE = "memind.retrieval.result_merge";
    public static final String RETRIEVAL_RERANK = "memind.retrieval.rerank";
    public static final String RETRIEVAL_KEYWORD_SEARCH = "memind.retrieval.keyword_search";
    public static final String RETRIEVAL_MEMORY_THREAD_ASSIST =
            "memind.retrieval.memory_thread.assist";
    public static final String RETRIEVAL_GRAPH_ASSIST = "memind.retrieval.graph.assist";
    public static final String RETRIEVAL_GRAPH_CHANNEL = "memind.retrieval.channel.graph";
    public static final String RETRIEVAL_TEMPORAL_CHANNEL = "memind.retrieval.channel.temporal";
    public static final String RETRIEVAL_INSIGHT_TYPE_ROUTING =
            "memind.retrieval.insight_type_routing";
    public static final String RETRIEVAL_MULTI_QUERY_EXPAND = "memind.retrieval.multi_query_expand";
    // ===== Graph =====
    public static final String GRAPH_EXTRACT_ENTITIES = "memind.graph.extract_entities";
    public static final String GRAPH_EXTRACT_EDGES = "memind.graph.extract_edges";
    public static final String GRAPH_RESOLVE_ENTITIES = "memind.graph.resolve_entities";
    public static final String GRAPH_RESOLVE_EDGES = "memind.graph.resolve_edges";
    public static final String GRAPH_MATERIALIZE = "memind.graph.materialize";
    public static final String GRAPH_SEMANTIC_LINK = "memind.graph.semantic_link";
}
