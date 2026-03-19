package com.openmemind.ai.memory.core.extraction.item.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Foresight extraction LLM response format
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightExtractionResponse(List<ForesightItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForesightItem(
            String content,
            String evidence,
            String validUntil,
            Integer durationDays,
            Map<String, Object> metadata) {}
}
