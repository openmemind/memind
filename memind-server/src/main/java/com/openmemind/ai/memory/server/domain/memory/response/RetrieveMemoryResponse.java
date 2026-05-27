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
package com.openmemind.ai.memory.server.domain.memory.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RetrieveMemoryResponse(
        String status,
        List<RetrievedItemView> items,
        List<RetrievedInsightView> insights,
        List<RetrievedRawDataView> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {

    public RetrieveMemoryResponse(
            String status,
            List<RetrievedItemView> items,
            List<RetrievedInsightView> insights,
            List<RetrievedRawDataView> rawData,
            List<String> evidences,
            String strategy,
            String query) {
        this(status, items, insights, rawData, evidences, strategy, query, null);
    }

    public record RetrievedItemView(
            String id,
            String text,
            float vectorScore,
            double finalScore,
            Instant occurredAt,
            String category,
            Map<String, Object> metadata) {

        public RetrievedItemView(
                String id, String text, float vectorScore, double finalScore, Instant occurredAt) {
            this(id, text, vectorScore, finalScore, occurredAt, null, Map.of());
        }

        public RetrievedItemView {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record RetrievedInsightView(String id, String text, String tier) {}

    public record RetrievedRawDataView(
            String rawDataId,
            String caption,
            double maxScore,
            List<String> itemIds,
            String type,
            String sourceClient,
            Map<String, Object> metadata,
            Instant startTime,
            Instant endTime,
            Instant createdAt) {

        public RetrievedRawDataView(
                String rawDataId, String caption, double maxScore, List<String> itemIds) {
            this(rawDataId, caption, maxScore, itemIds, null, null, Map.of(), null, null, null);
        }

        public RetrievedRawDataView {
            itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
