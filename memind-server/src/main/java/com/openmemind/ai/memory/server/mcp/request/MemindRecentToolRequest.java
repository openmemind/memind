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

import com.openmemind.ai.memory.server.domain.memory.request.MetadataFilter;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.support.MemindMcpToolValidation;
import java.util.List;
import java.util.Locale;

public record MemindRecentToolRequest(
        String userId,
        String agentId,
        List<String> types,
        Integer limit,
        String cursor,
        MetadataFilter metadataFilter) {

    public String requiredUserId() {
        return MemindMcpToolValidation.requireText(userId, "userId");
    }

    public String requiredAgentId() {
        return MemindMcpToolValidation.requireText(agentId, "agentId");
    }

    public List<EntryType> effectiveTypes() {
        if (types == null || types.isEmpty()) {
            return List.of(EntryType.ITEM, EntryType.RAWDATA);
        }
        return types.stream().map(MemindRecentToolRequest::parseType).distinct().toList();
    }

    public int effectiveLimit(MemindMcpToolProperties properties) {
        return MemindMcpToolValidation.effectivePositiveInt(
                limit, 20, properties.maxResultLimit(), "limit");
    }

    private static EntryType parseType(String value) {
        String normalized =
                MemindMcpToolValidation.requireText(value, "types").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ITEM" -> EntryType.ITEM;
            case "RAWDATA" -> EntryType.RAWDATA;
            default ->
                    throw new IllegalArgumentException("types must contain only ITEM or RAWDATA");
        };
    }

    public enum EntryType {
        ITEM,
        RAWDATA
    }
}
