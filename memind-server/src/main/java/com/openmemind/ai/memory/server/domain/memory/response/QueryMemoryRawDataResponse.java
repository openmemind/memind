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

public record QueryMemoryRawDataResponse(List<MemoryRawDataView> rawData, String nextCursor) {

    public QueryMemoryRawDataResponse {
        rawData = rawData == null ? List.of() : List.copyOf(rawData);
    }

    public record MemoryRawDataView(
            String id,
            String type,
            String sourceClient,
            String caption,
            Map<String, Object> metadata,
            Map<String, Object> segment,
            Instant startTime,
            Instant endTime,
            Instant createdAt) {

        public MemoryRawDataView {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            segment = segment == null ? null : Map.copyOf(segment);
        }
    }
}
