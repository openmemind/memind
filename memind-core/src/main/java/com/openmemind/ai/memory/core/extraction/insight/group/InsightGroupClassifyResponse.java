package com.openmemind.ai.memory.core.extraction.insight.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * LLM Group Response Structure
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightGroupClassifyResponse(List<GroupAssignment> assignments) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupAssignment(String groupName, List<String> itemIds) {}
}
