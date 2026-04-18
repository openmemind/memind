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
import com.openmemind.ai.memory.core.builder.SimpleMemoryThreadAssistOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalGraphOptions;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.thread.RetrievalMemoryThreadSettings;
import java.time.Duration;

/**
 * Simple Strategy Configuration
 *
 * @param enableKeywordSearch Whether to enable BM25 keyword search (only effective at the Item level)
 */
public record SimpleStrategyConfig(
        boolean enableKeywordSearch,
        GraphAssistConfig graphAssist,
        MemoryThreadAssistConfig memoryThreadAssist)
        implements StrategyConfig {

    public SimpleStrategyConfig {
        graphAssist = graphAssist != null ? graphAssist : GraphAssistConfig.defaults();
        memoryThreadAssist =
                memoryThreadAssist != null
                        ? memoryThreadAssist
                        : MemoryThreadAssistConfig.defaults();
    }

    public SimpleStrategyConfig(boolean enableKeywordSearch) {
        this(
                enableKeywordSearch,
                GraphAssistConfig.defaults(),
                MemoryThreadAssistConfig.defaults());
    }

    public SimpleStrategyConfig(boolean enableKeywordSearch, GraphAssistConfig graphAssist) {
        this(enableKeywordSearch, graphAssist, MemoryThreadAssistConfig.defaults());
    }

    @Override
    public String strategyName() {
        return RetrievalStrategies.SIMPLE;
    }

    public static SimpleStrategyConfig defaults() {
        return new SimpleStrategyConfig(true);
    }

    public SimpleStrategyConfig withKeywordSearch(boolean enabled) {
        return new SimpleStrategyConfig(enabled, graphAssist, memoryThreadAssist);
    }

    public SimpleStrategyConfig withGraphAssist(GraphAssistConfig graphAssist) {
        return new SimpleStrategyConfig(enableKeywordSearch, graphAssist, memoryThreadAssist);
    }

    public SimpleStrategyConfig withMemoryThreadAssist(
            MemoryThreadAssistConfig memoryThreadAssist) {
        return new SimpleStrategyConfig(enableKeywordSearch, graphAssist, memoryThreadAssist);
    }

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
            var defaults = SimpleRetrievalGraphOptions.defaults();
            return new GraphAssistConfig(
                    defaults.enabled(),
                    defaults.maxSeedItems(),
                    defaults.maxExpandedItems(),
                    defaults.maxSemanticNeighborsPerSeed(),
                    defaults.maxTemporalNeighborsPerSeed(),
                    defaults.maxCausalNeighborsPerSeed(),
                    defaults.maxEntitySiblingItemsPerSeed(),
                    defaults.maxItemsPerEntity(),
                    defaults.graphChannelWeight(),
                    defaults.minLinkStrength(),
                    defaults.minMentionConfidence(),
                    defaults.protectDirectTopK(),
                    defaults.timeout());
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

        public GraphAssistConfig withMaxSeedItems(int maxSeedItems) {
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

    /** Runtime memory-thread assist configuration for simple retrieval. */
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
            var defaults = SimpleMemoryThreadAssistOptions.defaults();
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
