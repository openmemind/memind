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
import com.openmemind.ai.client.model.common.RawContent;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractMemoryRequest(
        String userId, String agentId, RawContent rawContent, String sourceClient) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String userId;
        private String agentId;
        private RawContent rawContent;
        private String sourceClient;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder rawContent(RawContent rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        public Builder sourceClient(String sourceClient) {
            this.sourceClient = sourceClient;
            return this;
        }

        public ExtractMemoryRequest build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(rawContent, "rawContent");
            return new ExtractMemoryRequest(userId, agentId, rawContent, sourceClient);
        }
    }
}
