package com.openmemind.ai.memory.core.retrieval.scoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * Unified scoring result
 *
 * <p>Encapsulates the retrieved memory entries and their scoring information for cross-layer and cross-strategy result merging and sorting
 *
 * @param sourceType  Source type (INSIGHT / ITEM / RAW_DATA)
 * @param sourceId    Source ID (MemoryInsight.id / MemoryItem.id / MemoryRawData.id)
 * @param text        Text content for display
 * @param vectorScore Raw vector similarity score (0.0-1.0)
 * @param finalScore  Final score (calculated by ScoringStrategy)
 * @param occurredAt  Memory occurrence time (only ITEM type may have a value, Profile/Behavior class is null)
 */
public record ScoredResult(
        SourceType sourceType,
        String sourceId,
        String text,
        float vectorScore,
        double finalScore,
        Instant occurredAt) {

    /** 5 parameter compatible constructor (occurredAt defaults to null) */
    public ScoredResult(
            SourceType sourceType,
            String sourceId,
            String text,
            float vectorScore,
            double finalScore) {
        this(sourceType, sourceId, text, vectorScore, finalScore, null);
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
        return new ScoredResult(sourceType, sourceId, text, vectorScore, finalScore, occurredAt);
    }
}
