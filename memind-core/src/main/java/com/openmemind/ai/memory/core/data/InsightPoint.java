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
        @JsonInclude(JsonInclude.Include.NON_NULL) String pointId,
        PointType type,
        String content,
        List<String> sourceItemIds,
        List<InsightPointRef> sourcePointRefs,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> metadata) {

    public InsightPoint {
        sourceItemIds = sourceItemIds == null ? List.of() : List.copyOf(sourceItemIds);
        sourcePointRefs = sourcePointRefs == null ? List.of() : List.copyOf(sourcePointRefs);
        metadata = metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata);
    }

    /** Backward compatible: constructor without metadata */
    public InsightPoint(PointType type, String content, List<String> sourceItemIds) {
        this(null, type, content, sourceItemIds, List.of(), null);
    }

    public InsightPoint(
            String pointId, PointType type, String content, List<String> sourceItemIds) {
        this(pointId, type, content, sourceItemIds, List.of(), null);
    }

    /** Backward compatible: constructor without pointId */
    public InsightPoint(
            PointType type,
            String content,
            List<String> sourceItemIds,
            Map<String, String> metadata) {
        this(null, type, content, sourceItemIds, List.of(), metadata);
    }

    public InsightPoint withPointId(String pointId) {
        return new InsightPoint(pointId, type, content, sourceItemIds, sourcePointRefs, metadata);
    }

    public enum PointType {
        SUMMARY,
        REASONING
    }
}
