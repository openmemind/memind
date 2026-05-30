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
package com.openmemind.ai.memory.server.mcp.request;

import com.openmemind.ai.memory.server.mcp.support.MemindMcpToolValidation;
import java.util.List;

public record MemindItemSourcesToolRequest(
        String userId, String agentId, List<String> ids, Boolean includeSegment) {

    public String requiredUserId() {
        return MemindMcpToolValidation.requireText(userId, "userId");
    }

    public String requiredAgentId() {
        return MemindMcpToolValidation.requireText(agentId, "agentId");
    }

    public List<Long> requiredLongIds(int maxIdsPerRequest) {
        return MemindMcpToolValidation.requireLongIds(ids, "ids", maxIdsPerRequest);
    }

    public boolean effectiveIncludeSegment() {
        return Boolean.TRUE.equals(includeSegment);
    }
}
