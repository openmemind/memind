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
package com.openmemind.ai.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryMemoryRawDataRequest(
        String userId,
        String agentId,
        List<String> types,
        List<String> sourceClients,
        TimeRange timeRange,
        MetadataFilter metadataFilter,
        IncludeOptions include,
        Integer limit,
        String cursor) {

    public static Builder builder() {
        return new Builder();
    }

    public QueryMemoryRawDataRequest(String userId, String agentId) {
        this(userId, agentId, null, null, null, null, null, null, null);
    }

    public record TimeRange(String field, Instant from, Instant to) {}

    public record IncludeOptions(Boolean segment, Boolean metadata) {}

    public static final class Builder {

        private String userId;
        private String agentId;
        private List<String> types;
        private List<String> sourceClients;
        private TimeRange timeRange;
        private MetadataFilter metadataFilter;
        private IncludeOptions include;
        private Integer limit;
        private String cursor;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder types(List<String> types) {
            this.types = types;
            return this;
        }

        public Builder sourceClients(List<String> sourceClients) {
            this.sourceClients = sourceClients;
            return this;
        }

        public Builder timeRange(TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder metadataFilter(MetadataFilter metadataFilter) {
            this.metadataFilter = metadataFilter;
            return this;
        }

        public Builder include(IncludeOptions include) {
            this.include = include;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        public QueryMemoryRawDataRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            return new QueryMemoryRawDataRequest(
                    userId,
                    agentId,
                    types,
                    sourceClients,
                    timeRange,
                    metadataFilter,
                    include,
                    limit,
                    cursor);
        }
    }
}
