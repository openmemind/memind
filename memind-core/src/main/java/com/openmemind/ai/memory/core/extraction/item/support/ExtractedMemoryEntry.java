package com.openmemind.ai.memory.core.extraction.item.support;

import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Intermediate result of the extracted memory entry
 *
 * <p>Intermediate state after parsing from LLM response, before deduplication/persistence
 *
 * @param content Memory content
 * @param confidence Confidence level (used only for filtering, not persisted)
 * @param occurredAt Time when the memory occurred (LLM parsing or referenceTime fallback)
 * @param rawDataId Source data ID
 * @param contentHash Content hash (filled after deduplication)
 * @param insightTypes List of matched InsightType names (can be empty)
 * @param metadata Additional metadata (can be null; merged from ParsedSegment.metadata + LLM output metadata, including whenToUse)
 */
public record ExtractedMemoryEntry(
        String content,
        float confidence,
        Instant occurredAt,
        String rawDataId,
        String contentHash,
        List<String> insightTypes,
        Map<String, Object> metadata,
        MemoryItemType type,
        String category) {

    /**
     * Set contentHash
     */
    public ExtractedMemoryEntry withContentHash(String contentHash) {
        return new ExtractedMemoryEntry(
                content,
                confidence,
                occurredAt,
                rawDataId,
                contentHash,
                insightTypes,
                metadata,
                type,
                category);
    }
}
