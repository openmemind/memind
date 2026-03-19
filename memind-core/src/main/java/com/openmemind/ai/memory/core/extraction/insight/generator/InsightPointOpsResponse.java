package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightPointOpsResponse(List<PointOperation> operations) {
    public InsightPointOpsResponse {
        if (operations == null) {
            operations = List.of();
        }
    }
}
