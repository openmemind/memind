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
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.support.MemindMcpToolValidation;
import java.util.List;

public record MemindQueryItemsToolRequest(
        String userId,
        String agentId,
        String scope,
        List<String> categories,
        List<String> sourceClients,
        List<String> rawDataTypes,
        QueryMemoryItemsRequest.TimeRange timeRange,
        MetadataFilter metadataFilter,
        Integer limit,
        String cursor) {

    public QueryMemoryItemsRequest toApplicationRequest(MemindMcpToolProperties properties) {
        return new QueryMemoryItemsRequest(
                MemindMcpToolValidation.requireText(userId, "userId"),
                MemindMcpToolValidation.requireText(agentId, "agentId"),
                trimOptional(scope),
                safeList(categories),
                safeList(sourceClients),
                safeList(rawDataTypes),
                timeRange,
                metadataFilter,
                effectiveLimit(properties),
                cursor);
    }

    private int effectiveLimit(MemindMcpToolProperties properties) {
        return MemindMcpToolValidation.effectivePositiveInt(
                limit, 20, properties.maxResultLimit(), "limit");
    }

    private static List<String> safeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(MemindQueryItemsToolRequest::hasText)
                        .map(String::trim)
                        .toList();
    }

    private static String trimOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
