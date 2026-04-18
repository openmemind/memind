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
package com.openmemind.ai.memory.core.builder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Objects;

/**
 * Runtime switches and caps for bounded graph-assisted simple retrieval.
 */
public record SimpleRetrievalGraphOptions(
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
        double semanticEvidenceDecayFactor,
        Duration timeout) {

    public SimpleRetrievalGraphOptions {
        validateGraphAssistShape(
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
                semanticEvidenceDecayFactor,
                timeout);
    }

    public static void validateGraphAssistShape(
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
            double semanticEvidenceDecayFactor,
            Duration timeout) {
        if (maxSeedItems <= 0
                || maxExpandedItems <= 0
                || maxSemanticNeighborsPerSeed < 0
                || maxTemporalNeighborsPerSeed < 0
                || maxCausalNeighborsPerSeed < 0
                || maxEntitySiblingItemsPerSeed < 0
                || maxItemsPerEntity <= 0
                || protectDirectTopK < 0) {
            throw new IllegalArgumentException("graph retrieval caps must be non-negative");
        }
        if (graphChannelWeight < 0.0d || graphChannelWeight >= 1.0d) {
            throw new IllegalArgumentException(
                    "graph retrieval weights must keep graph below direct");
        }
        if (minLinkStrength < 0.0d || minLinkStrength > 1.0d) {
            throw new IllegalArgumentException("graph retrieval weights must be bounded");
        }
        if (minMentionConfidence < 0.0f || minMentionConfidence > 1.0f) {
            throw new IllegalArgumentException("minMentionConfidence must be in [0,1]");
        }
        if (semanticEvidenceDecayFactor < 0.0d) {
            throw new IllegalArgumentException("semanticEvidenceDecayFactor must be non-negative");
        }
        Objects.requireNonNull(timeout, "timeout");
    }

    public static SimpleRetrievalGraphOptions defaults() {
        return new SimpleRetrievalGraphOptions(
                false, 6, 12, 2, 2, 2, 3, 8, 0.35d, 0.55d, 0.70f, 3, 0.5d, Duration.ofMillis(200));
    }

    @JsonCreator
    public static SimpleRetrievalGraphOptions fromJson(
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
            @JsonProperty("semanticEvidenceDecayFactor") Double semanticEvidenceDecayFactor,
            @JsonProperty("timeout") Duration timeout) {
        var defaults = defaults();
        return new SimpleRetrievalGraphOptions(
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
                semanticEvidenceDecayFactor != null
                        ? semanticEvidenceDecayFactor
                        : defaults.semanticEvidenceDecayFactor(),
                timeout != null ? timeout : defaults.timeout());
    }

    public SimpleRetrievalGraphOptions withEnabled(boolean enabled) {
        return new SimpleRetrievalGraphOptions(
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
                semanticEvidenceDecayFactor,
                timeout);
    }

    public SimpleRetrievalGraphOptions withMaxSeedItems(int maxSeedItems) {
        return new SimpleRetrievalGraphOptions(
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
                semanticEvidenceDecayFactor,
                timeout);
    }

    public SimpleRetrievalGraphOptions withTimeout(Duration timeout) {
        return new SimpleRetrievalGraphOptions(
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
                semanticEvidenceDecayFactor,
                timeout);
    }
}
