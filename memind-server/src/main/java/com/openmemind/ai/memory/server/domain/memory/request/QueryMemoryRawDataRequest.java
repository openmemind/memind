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
package com.openmemind.ai.memory.server.domain.memory.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public record QueryMemoryRawDataRequest(
        @NotBlank String userId,
        @NotBlank String agentId,
        List<String> types,
        List<String> sourceClients,
        TimeRange timeRange,
        MetadataFilter metadataFilter,
        IncludeOptions include,
        @Min(1) @Max(100) Integer limit,
        String cursor) {

    public QueryMemoryRawDataRequest {
        types = types == null ? List.of() : List.copyOf(types);
        sourceClients = sourceClients == null ? List.of() : List.copyOf(sourceClients);
    }

    public int effectiveLimit() {
        return limit == null ? 20 : limit;
    }

    public IncludeOptions effectiveInclude() {
        return include == null ? new IncludeOptions(false, true) : include;
    }

    public record TimeRange(String field, Instant from, Instant to) {}

    public record IncludeOptions(Boolean segment, Boolean metadata) {
        public boolean includeSegment() {
            return Boolean.TRUE.equals(segment);
        }

        public boolean includeMetadata() {
            return metadata == null || Boolean.TRUE.equals(metadata);
        }
    }
}
