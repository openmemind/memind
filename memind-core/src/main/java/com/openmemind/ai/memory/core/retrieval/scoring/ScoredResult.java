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
package com.openmemind.ai.memory.core.retrieval.scoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.time.Instant;
import java.util.Map;

/**
 * Unified scoring result
 *
 * <p>Encapsulates the retrieved memory entries and their scoring information for cross-layer and cross-strategy
 * result merging and sorting
 *
 * @param sourceType  Source type (INSIGHT / ITEM / RAW_DATA)
 * @param sourceId    Source ID (MemoryInsight.id / MemoryItem.id / MemoryRawData.id)
 * @param text        Text content for display
 * @param vectorScore Raw vector similarity score (0.0-1.0)
 * @param finalScore  Final score (calculated by ScoringStrategy)
 * @param occurredAt  Memory occurrence time (only ITEM type may have a value, Profile/Behavior class is null)
 * @param category    Memory item category name (only ITEM type may have a value)
 * @param metadata    Memory item metadata (only ITEM type may have values)
 */
public record ScoredResult(
        SourceType sourceType,
        String sourceId,
        String text,
        float vectorScore,
        double finalScore,
        Instant occurredAt,
        String category,
        Map<String, Object> metadata) {

    public ScoredResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 5 parameter compatible constructor (occurredAt defaults to null) */
    public ScoredResult(
            SourceType sourceType,
            String sourceId,
            String text,
            float vectorScore,
            double finalScore) {
        this(sourceType, sourceId, text, vectorScore, finalScore, null, null, Map.of());
    }

    /** 6 parameter compatible constructor (category/metadata default to empty) */
    public ScoredResult(
            SourceType sourceType,
            String sourceId,
            String text,
            float vectorScore,
            double finalScore,
            Instant occurredAt) {
        this(sourceType, sourceId, text, vectorScore, finalScore, occurredAt, null, Map.of());
    }

    /** Source type */
    public enum SourceType {
        INSIGHT,
        ITEM,
        RAW_DATA
    }

    /** Key for deduplication: sourceType + sourceId */
    @JsonIgnore
    public String dedupKey() {
        return sourceType.name() + ":" + sourceId;
    }

    /** Returns a copy with the specified occurredAt */
    public ScoredResult withOccurredAt(Instant occurredAt) {
        return new ScoredResult(
                sourceType,
                sourceId,
                text,
                vectorScore,
                finalScore,
                occurredAt,
                category,
                metadata);
    }

    /** Returns a copy with the specified final score. */
    public ScoredResult withFinalScore(double finalScore) {
        return new ScoredResult(
                sourceType,
                sourceId,
                text,
                vectorScore,
                finalScore,
                occurredAt,
                category,
                metadata);
    }

    /** Returns a copy with the specified text and scoring fields. */
    public ScoredResult withTextAndScores(String text, float vectorScore, double finalScore) {
        return new ScoredResult(
                sourceType,
                sourceId,
                text,
                vectorScore,
                finalScore,
                occurredAt,
                category,
                metadata);
    }

    /** Builds an ITEM retrieval result from a MemoryItem while preserving item category and metadata. */
    public static ScoredResult fromItem(
            MemoryItem item,
            String text,
            float vectorScore,
            double finalScore,
            Instant occurredAt) {
        return new ScoredResult(
                SourceType.ITEM,
                String.valueOf(item.id()),
                text,
                vectorScore,
                finalScore,
                occurredAt,
                item.category() == null ? null : item.category().categoryName(),
                item.metadata());
    }
}
