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
package com.openmemind.ai.memory.core.retrieval.strategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.builder.DeepMemoryThreadAssistOptions;
import com.openmemind.ai.memory.core.builder.SimpleMemoryThreadAssistOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalGraphOptions;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.thread.RetrievalMemoryThreadSettings;
import java.time.Duration;

/**
 * Deep Retrieval strategy configuration
 *
 * <p>Includes multi-query expansion, sufficiency check, and other parameters exclusive to Deep strategies.
 * The topK field in the original DeepRetrievalConfig has been unified into
 * RetrievalConfig.TierConfig.
 *
 * @param queryExpansion  Multi-query expansion configuration
 * @param sufficiency     Sufficiency check configuration
 * @param tier2InitTopK   Tier2 initial vector retrieval candidate amount
 * @param bm25InitTopK    Tier2 initial BM25 retrieval candidate amount
 * @param minScore        Insight/Item minimum score threshold (used to build internal TierConfig)
 * @param graphAssist     Graph-assisted retrieval configuration
 */
public record DeepStrategyConfig(
        QueryExpansionConfig queryExpansion,
        SufficiencyConfig sufficiency,
        int tier2InitTopK,
        int bm25InitTopK,
        double minScore,
        GraphAssistConfig graphAssist,
        MemoryThreadAssistConfig memoryThreadAssist)
        implements StrategyConfig {

    public DeepStrategyConfig {
        queryExpansion = queryExpansion != null ? queryExpansion : QueryExpansionConfig.defaults();
        sufficiency = sufficiency != null ? sufficiency : SufficiencyConfig.defaults();
        graphAssist = graphAssist != null ? graphAssist : GraphAssistConfig.defaults();
        memoryThreadAssist =
                memoryThreadAssist != null
                        ? memoryThreadAssist
                        : MemoryThreadAssistConfig.defaults();
    }

    public DeepStrategyConfig(
            QueryExpansionConfig queryExpansion,
            SufficiencyConfig sufficiency,
            int tier2InitTopK,
            int bm25InitTopK,
            double minScore) {
        this(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                GraphAssistConfig.defaults(),
                MemoryThreadAssistConfig.defaults());
    }

    public DeepStrategyConfig(
            QueryExpansionConfig queryExpansion,
            SufficiencyConfig sufficiency,
            int tier2InitTopK,
            int bm25InitTopK,
            double minScore,
            GraphAssistConfig graphAssist) {
        this(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                MemoryThreadAssistConfig.defaults());
    }

    @Override
    public String strategyName() {
        return RetrievalStrategies.DEEP_RETRIEVAL;
    }

    public static DeepStrategyConfig defaults() {
        return new DeepStrategyConfig(
                QueryExpansionConfig.defaults(),
                SufficiencyConfig.defaults(),
                50,
                50,
                0.3,
                GraphAssistConfig.defaults(),
                MemoryThreadAssistConfig.defaults());
    }

    public DeepStrategyConfig withQueryExpansion(QueryExpansionConfig queryExpansion) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withSufficiency(SufficiencyConfig sufficiency) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withTier2InitTopK(int tier2InitTopK) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withBm25InitTopK(int bm25InitTopK) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withMinScore(double minScore) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withGraphAssist(GraphAssistConfig graphAssist) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    public DeepStrategyConfig withMemoryThreadAssist(MemoryThreadAssistConfig memoryThreadAssist) {
        return new DeepStrategyConfig(
                queryExpansion,
                sufficiency,
                tier2InitTopK,
                bm25InitTopK,
                minScore,
                graphAssist,
                memoryThreadAssist);
    }

    /** Multi-query expansion */
    public record QueryExpansionConfig(int maxExpandedQueries) {
        public static QueryExpansionConfig defaults() {
            return new QueryExpansionConfig(3);
        }
    }

    /** Sufficiency check */
    public record SufficiencyConfig(int itemTopK) {
        public static SufficiencyConfig defaults() {
            return new SufficiencyConfig(20);
        }
    }

    /** Runtime graph assist configuration for deep retrieval. */
    public record GraphAssistConfig(
            boolean enabled,
            int maxSeedItems,
            int maxExpandedItems,
            int maxSemanticNeighborsPerSeed,
            int maxTemporalNeighborsPerSeed,
            int maxCausalNeighborsPerSeed,
            int maxEntitySiblingItemsPerSeed,
            int maxItemsPerEntity,
            double graphChannelWeight,
            double minLinkStrength,
            float minMentionConfidence,
            int protectDirectTopK,
            Duration timeout)
            implements RetrievalGraphSettings {

        public GraphAssistConfig {
            SimpleRetrievalGraphOptions.validateGraphAssistShape(
                    maxSeedItems,
                    maxExpandedItems,
                    maxSemanticNeighborsPerSeed,
                    maxTemporalNeighborsPerSeed,
                    maxCausalNeighborsPerSeed,
                    maxEntitySiblingItemsPerSeed,
                    maxItemsPerEntity,
                    graphChannelWeight,
                    minLinkStrength,
                    minMentionConfidence,
                    protectDirectTopK,
                    timeout);
        }

        public static GraphAssistConfig defaults() {
            return new GraphAssistConfig(
                    false, 8, 16, 2, 2, 2, 4, 8, 0.30d, 0.55d, 0.70f, 5, Duration.ofMillis(300));
        }

        @JsonCreator
        public static GraphAssistConfig fromJson(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("maxSeedItems") Integer maxSeedItems,
                @JsonProperty("maxExpandedItems") Integer maxExpandedItems,
                @JsonProperty("maxSemanticNeighborsPerSeed") Integer maxSemanticNeighborsPerSeed,
                @JsonProperty("maxTemporalNeighborsPerSeed") Integer maxTemporalNeighborsPerSeed,
                @JsonProperty("maxCausalNeighborsPerSeed") Integer maxCausalNeighborsPerSeed,
                @JsonProperty("maxEntitySiblingItemsPerSeed") Integer maxEntitySiblingItemsPerSeed,
                @JsonProperty("maxItemsPerEntity") Integer maxItemsPerEntity,
                @JsonProperty("graphChannelWeight") Double graphChannelWeight,
                @JsonProperty("minLinkStrength") Double minLinkStrength,
                @JsonProperty("minMentionConfidence") Float minMentionConfidence,
                @JsonProperty("protectDirectTopK") Integer protectDirectTopK,
                @JsonProperty("timeout") Duration timeout) {
            var defaults = defaults();
            return new GraphAssistConfig(
                    enabled != null ? enabled : defaults.enabled(),
                    maxSeedItems != null ? maxSeedItems : defaults.maxSeedItems(),
                    maxExpandedItems != null ? maxExpandedItems : defaults.maxExpandedItems(),
                    maxSemanticNeighborsPerSeed != null
                            ? maxSemanticNeighborsPerSeed
                            : defaults.maxSemanticNeighborsPerSeed(),
                    maxTemporalNeighborsPerSeed != null
                            ? maxTemporalNeighborsPerSeed
                            : defaults.maxTemporalNeighborsPerSeed(),
                    maxCausalNeighborsPerSeed != null
                            ? maxCausalNeighborsPerSeed
                            : defaults.maxCausalNeighborsPerSeed(),
                    maxEntitySiblingItemsPerSeed != null
                            ? maxEntitySiblingItemsPerSeed
                            : defaults.maxEntitySiblingItemsPerSeed(),
                    maxItemsPerEntity != null ? maxItemsPerEntity : defaults.maxItemsPerEntity(),
                    graphChannelWeight != null ? graphChannelWeight : defaults.graphChannelWeight(),
                    minLinkStrength != null ? minLinkStrength : defaults.minLinkStrength(),
                    minMentionConfidence != null
                            ? minMentionConfidence
                            : defaults.minMentionConfidence(),
                    protectDirectTopK != null ? protectDirectTopK : defaults.protectDirectTopK(),
                    timeout != null ? timeout : defaults.timeout());
        }

        public GraphAssistConfig withEnabled(boolean enabled) {
            return new GraphAssistConfig(
                    enabled,
                    maxSeedItems,
                    maxExpandedItems,
                    maxSemanticNeighborsPerSeed,
                    maxTemporalNeighborsPerSeed,
                    maxCausalNeighborsPerSeed,
                    maxEntitySiblingItemsPerSeed,
                    maxItemsPerEntity,
                    graphChannelWeight,
                    minLinkStrength,
                    minMentionConfidence,
                    protectDirectTopK,
                    timeout);
        }

        public GraphAssistConfig withTimeout(Duration timeout) {
            return new GraphAssistConfig(
                    enabled,
                    maxSeedItems,
                    maxExpandedItems,
                    maxSemanticNeighborsPerSeed,
                    maxTemporalNeighborsPerSeed,
                    maxCausalNeighborsPerSeed,
                    maxEntitySiblingItemsPerSeed,
                    maxItemsPerEntity,
                    graphChannelWeight,
                    minLinkStrength,
                    minMentionConfidence,
                    protectDirectTopK,
                    timeout);
        }
    }

    /** Runtime memory-thread assist configuration for deep retrieval. */
    public record MemoryThreadAssistConfig(
            boolean enabled,
            int maxThreads,
            int maxMembersPerThread,
            int protectDirectTopK,
            Duration timeout)
            implements RetrievalMemoryThreadSettings {

        public MemoryThreadAssistConfig {
            SimpleMemoryThreadAssistOptions.validateAssistShape(
                    maxThreads, maxMembersPerThread, protectDirectTopK, timeout);
        }

        public static MemoryThreadAssistConfig defaults() {
            var defaults = DeepMemoryThreadAssistOptions.defaults();
            return new MemoryThreadAssistConfig(
                    defaults.enabled(),
                    defaults.maxThreads(),
                    defaults.maxMembersPerThread(),
                    defaults.protectDirectTopK(),
                    defaults.timeout());
        }

        @JsonCreator
        public static MemoryThreadAssistConfig fromJson(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("maxThreads") Integer maxThreads,
                @JsonProperty("maxMembersPerThread") Integer maxMembersPerThread,
                @JsonProperty("protectDirectTopK") Integer protectDirectTopK,
                @JsonProperty("timeout") Duration timeout) {
            var defaults = defaults();
            return new MemoryThreadAssistConfig(
                    enabled != null ? enabled : defaults.enabled(),
                    maxThreads != null ? maxThreads : defaults.maxThreads(),
                    maxMembersPerThread != null
                            ? maxMembersPerThread
                            : defaults.maxMembersPerThread(),
                    protectDirectTopK != null ? protectDirectTopK : defaults.protectDirectTopK(),
                    timeout != null ? timeout : defaults.timeout());
        }
    }
}
