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

public record RetrieveMemoryResponse(
        List<RetrievedItemView> items,
        List<RetrievedInsightView> insights,
        List<RetrievedRawDataView> rawData,
        List<String> evidences,
        String strategy,
        String query,
        RetrievalTraceView trace) {

    public RetrieveMemoryResponse(
            List<RetrievedItemView> items,
            List<RetrievedInsightView> insights,
            List<RetrievedRawDataView> rawData,
            List<String> evidences,
            String strategy,
            String query) {
        this(items, insights, rawData, evidences, strategy, query, null);
    }

    public record RetrievedItemView(
            String id, String text, float vectorScore, double finalScore, Instant occurredAt) {}

    public record RetrievedInsightView(String id, String text, String tier) {}

    public record RetrievedRawDataView(
            String rawDataId, String caption, double maxScore, List<String> itemIds) {}
}
