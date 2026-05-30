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
import java.util.Map;

public record MemindExtractRawDataToolRequest(
        String userId,
        String agentId,
        String type,
        Map<String, Object> content,
        Map<String, Object> metadata,
        String sourceClient) {

    public String requiredUserId() {
        return MemindMcpToolValidation.requireText(userId, "userId");
    }

    public String requiredAgentId() {
        return MemindMcpToolValidation.requireText(agentId, "agentId");
    }

    public String requiredType() {
        return MemindMcpToolValidation.requireText(type, "type");
    }

    public Map<String, Object> requiredContent() {
        Map<String, Object> value = MemindMcpToolValidation.requireMap(content, "content");
        if (value.containsKey("type")) {
            throw new IllegalArgumentException("content must not contain type");
        }
        return value;
    }

    public Map<String, Object> effectiveMetadata() {
        return metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String effectiveSourceClient() {
        return MemindMcpToolValidation.normalizeSourceClient(sourceClient);
    }
}
