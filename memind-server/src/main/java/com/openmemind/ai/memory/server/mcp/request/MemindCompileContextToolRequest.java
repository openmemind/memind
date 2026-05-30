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

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.server.domain.memory.request.MetadataFilter;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.support.MemindMcpToolValidation;
import java.util.Locale;

public record MemindCompileContextToolRequest(
        String userId,
        String agentId,
        String query,
        String strategy,
        Integer maxItems,
        Integer tokenBudget,
        Boolean includeSources,
        MetadataFilter metadataFilter) {

    private static final int DEFAULT_MAX_ITEMS = 12;

    public RetrieveMemoryRequest toApplicationRequest() {
        return new RetrieveMemoryRequest(
                requiredUserId(),
                requiredAgentId(),
                requiredQuery(),
                parseStrategy(strategy),
                null,
                null,
                java.util.List.of(),
                null,
                metadataFilter,
                null);
    }

    public String requiredUserId() {
        return MemindMcpToolValidation.requireText(userId, "userId");
    }

    public String requiredAgentId() {
        return MemindMcpToolValidation.requireText(agentId, "agentId");
    }

    public String requiredQuery() {
        return MemindMcpToolValidation.requireText(query, "query");
    }

    public int effectiveMaxItems(MemindMcpToolProperties properties) {
        return MemindMcpToolValidation.effectivePositiveInt(
                maxItems, DEFAULT_MAX_ITEMS, properties.maxItemsPerContext(), "maxItems");
    }

    public int effectiveTokenBudget(MemindMcpToolProperties properties) {
        return MemindMcpToolValidation.effectivePositiveInt(
                tokenBudget,
                properties.defaultTokenBudget(),
                properties.maxTokenBudget(),
                "tokenBudget");
    }

    public boolean effectiveIncludeSources() {
        return includeSources == null || includeSources;
    }

    static RetrievalConfig.Strategy parseStrategy(String value) {
        if (value == null || value.isBlank()) {
            return RetrievalConfig.Strategy.SIMPLE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SIMPLE" -> RetrievalConfig.Strategy.SIMPLE;
            case "DEEP" -> RetrievalConfig.Strategy.DEEP;
            default -> throw new IllegalArgumentException("strategy must be one of SIMPLE, DEEP");
        };
    }
}
