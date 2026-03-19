package com.openmemind.ai.memory.core.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PointOperation(
        OpType op,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer targetIndex,
        @JsonInclude(JsonInclude.Include.NON_NULL) InsightPoint point,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    public enum OpType {
        ADD,
        UPDATE,
        DELETE
    }
}
