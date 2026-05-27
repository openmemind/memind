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
public record QueryMemoryItemsResponse(List<MemoryItem> items, String nextCursor) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryItem(
            String id,
            String text,
            String scope,
            String category,
            String type,
            String rawDataId,
            String rawDataType,
            String sourceClient,
            Instant occurredAt,
            Instant observedAt,
            Instant createdAt,
            Map<String, Object> metadata) {}
}
