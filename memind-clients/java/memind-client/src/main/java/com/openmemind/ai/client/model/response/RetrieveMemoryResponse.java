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
public record RetrieveMemoryResponse(
        String status,
        List<RetrievedItem> items,
        List<RetrievedInsight> insights,
        List<RetrievedRawData> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrievedItem(
            String id,
            String text,
            float vectorScore,
            double finalScore,
            Instant occurredAt,
            String category,
            Map<String, Object> metadata) {

        public RetrievedItem(
                String id, String text, float vectorScore, double finalScore, Instant occurredAt) {
            this(id, text, vectorScore, finalScore, occurredAt, null, Map.of());
        }

        public RetrievedItem {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrievedInsight(String id, String text, String tier) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrievedRawData(
            String rawDataId,
            String caption,
            double maxScore,
            List<String> itemIds,
            String type,
            String sourceClient,
            Map<String, Object> metadata,
            Instant startTime,
            Instant endTime,
            Instant createdAt) {}
}
