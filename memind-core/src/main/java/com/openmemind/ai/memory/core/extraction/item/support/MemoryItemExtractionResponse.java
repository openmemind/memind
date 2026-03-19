package com.openmemind.ai.memory.core.extraction.item.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * LLM Memory Extraction Structured Response
 *
 * @param items List of extracted memory items
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemoryItemExtractionResponse(List<ExtractedItem> items) {

    /**
     * Single extraction result
     *
     * @param content Memory content
     * @param confidence Confidence level (0.0-1.0)
     * @param occurredAt Memory occurrence time ISO-8601 (LLM parsed, may be null)
     * @param insightTypes List of matched InsightType names (may be empty)
     * @param metadata Additional metadata (may be null; includes whenToUse, etc.)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedItem(
            String content,
            float confidence,
            String occurredAt,
            List<String> insightTypes,
            Map<String, Object> metadata,
            String category) {}
}
