package com.openmemind.ai.memory.core.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Insight structured point
 *
 * <p>Replaces the old summary + reasonings model, each point covers a coherent aspect,
 * supporting point-level incremental updates.
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPoint(
        PointType type,
        String content,
        float confidence,
        List<String> sourceItemIds,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> metadata) {

    /** Backward compatible: constructor without metadata */
    public InsightPoint(
            PointType type, String content, float confidence, List<String> sourceItemIds) {
        this(type, content, confidence, sourceItemIds, null);
    }

    public enum PointType {
        SUMMARY,
        REASONING
    }
}
