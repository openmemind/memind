package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openmemind.ai.memory.core.data.InsightPoint;
import java.util.List;

/**
 * InsightPoint generates LLM structured responses
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPointGenerateResponse(List<InsightPoint> points) {

    public InsightPointGenerateResponse {
        if (points == null) {
            points = List.of();
        }
    }
}
