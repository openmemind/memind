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
import com.openmemind.ai.client.model.common.Strategy;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrieveMemoryRequest(
        String userId, String agentId, String query, Strategy strategy, Boolean trace) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String userId;
        private String agentId;
        private String query;
        private Strategy strategy;
        private Boolean trace;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder strategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder trace(Boolean trace) {
            this.trace = trace;
            return this;
        }

        public RetrieveMemoryRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(strategy, "strategy");
            return new RetrieveMemoryRequest(userId, agentId, query, strategy, trace);
        }
    }
}
