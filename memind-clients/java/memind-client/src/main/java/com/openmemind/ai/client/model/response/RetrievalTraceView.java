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
package com.openmemind.ai.client.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RetrievalTraceView(
        String traceId,
        Instant startedAt,
        Instant completedAt,
        boolean truncated,
        List<StageView> stages,
        MergeView merge,
        FinalView finalResults) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageView(
            String stage,
            String tier,
            String method,
            String status,
            Integer inputCount,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            Instant startedAt,
            Long durationMillis,
            Map<String, Object> attributes,
            List<Map<String, Object>> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MergeView(
            int inputCount,
            int outputCount,
            int deduplicatedCount,
            int sourceCount,
            String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinalView(
            String strategy,
            String status,
            int itemCount,
            int insightCount,
            int rawDataCount,
            int evidenceCount) {}
}
