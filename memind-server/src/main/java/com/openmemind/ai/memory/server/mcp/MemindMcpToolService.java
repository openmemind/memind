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
package com.openmemind.ai.memory.server.mcp;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.MetadataFilter;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.mcp.compiler.MemindMcpContextCompiler;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.request.MemindAddMessageToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindCommitToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindCompileContextToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindExtractRawDataToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindExtractTextToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindGetItemsToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindGetRawDataToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindItemSourcesToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindQueryItemsToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindQueryRawDataToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindRecentToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindRetrieveToolRequest;
import com.openmemind.ai.memory.server.mcp.response.MemindCompiledContextResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindItemSourcesResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindItemsGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRawDataGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRecentResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MemindMcpToolService implements MemindMcpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(MemindMcpToolService.class);

    private final OpenMemoryApplicationService memoryService;
    private final OpenMemoryAssetQueryService assetQueryService;
    private final MemindMcpContextCompiler contextCompiler;
    private final MemindMcpToolProperties properties;
    private final JsonMapper rawDataObjectMapper;

    public MemindMcpToolService(
            OpenMemoryApplicationService memoryService,
            OpenMemoryAssetQueryService assetQueryService,
            MemindMcpContextCompiler contextCompiler,
            MemindMcpToolProperties properties,
            JsonMapper rawDataObjectMapper) {
        this.memoryService = memoryService;
        this.assetQueryService = assetQueryService;
        this.contextCompiler = contextCompiler;
        this.properties = properties;
        this.rawDataObjectMapper = rawDataObjectMapper;
    }

    @McpTool(
            name = "memind_compile_context",
            title = "Compile Memind context",
            description =
                    "Retrieve Memind memory and compile it into a concise, sectioned context "
                            + "payload for an AI agent.")
    public MemindCompiledContextResponse compileContext(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Natural-language retrieval query.") String query,
            @McpToolParam(
                            required = false,
                            description = "Retrieval strategy: SIMPLE or DEEP. Defaults to SIMPLE.")
                    String strategy,
            @McpToolParam(required = false, description = "Maximum memory items to render.")
                    Integer maxItems,
            @McpToolParam(required = false, description = "Approximate token budget.")
                    Integer tokenBudget,
            @McpToolParam(required = false, description = "Whether to include source references.")
                    Boolean includeSources,
            @McpToolParam(required = false, description = "Optional metadata filter.")
                    MetadataFilter metadataFilter) {
        var request =
                new MemindCompileContextToolRequest(
                        userId,
                        agentId,
                        query,
                        strategy,
                        maxItems,
                        tokenBudget,
                        includeSources,
                        metadataFilter);
        RetrieveMemoryResponse response = memoryService.retrieve(request.toApplicationRequest());
        return contextCompiler.compile(
                response,
                request.effectiveMaxItems(properties),
                request.effectiveTokenBudget(properties),
                request.effectiveIncludeSources());
    }

    @McpTool(
            name = "memind_retrieve",
            title = "Retrieve Memind memory",
            description =
                    "Retrieve memory for a userId and agentId using a natural-language query. "
                            + "Use strategy SIMPLE for low-latency retrieval or DEEP for "
                            + "LLM-assisted retrieval.")
    public RetrieveMemoryResponse retrieve(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Natural-language retrieval query.") String query,
            @McpToolParam(
                            required = false,
                            description = "Retrieval strategy: SIMPLE or DEEP. Defaults to SIMPLE.")
                    String strategy,
            @McpToolParam(
                            required = false,
                            description = "Whether to include retrieval trace when supported.")
                    Boolean trace) {
        var request = new MemindRetrieveToolRequest(userId, agentId, query, strategy, trace);
        log.debug(
                "MCP tool memind_retrieve invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.retrieve(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_recent",
            title = "List recent Memind memory assets",
            description = "List recent memory items and rawdata for a userId and agentId.")
    public MemindRecentResponse recent(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(required = false, description = "Entry types: ITEM and/or RAWDATA.")
                    List<String> types,
            @McpToolParam(required = false, description = "Maximum entries to return.")
                    Integer limit,
            @McpToolParam(required = false, description = "Reserved pagination cursor.")
                    String cursor,
            @McpToolParam(required = false, description = "Optional metadata filter.")
                    MetadataFilter metadataFilter) {
        var request =
                new MemindRecentToolRequest(userId, agentId, types, limit, cursor, metadataFilter);
        int effectiveLimit = request.effectiveLimit(properties);
        List<MemindRecentResponse.Entry> entries = new ArrayList<>();
        if (request.effectiveTypes().contains(MemindRecentToolRequest.EntryType.ITEM)) {
            var items =
                    assetQueryService.queryItems(
                            new QueryMemoryItemsRequest(
                                    request.requiredUserId(),
                                    request.requiredAgentId(),
                                    null,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    null,
                                    metadataFilter,
                                    effectiveLimit,
                                    cursor));
            entries.addAll(items.items().stream().map(MemindMcpToolService::recentItem).toList());
        }
        if (request.effectiveTypes().contains(MemindRecentToolRequest.EntryType.RAWDATA)) {
            var rawData =
                    assetQueryService.queryRawData(
                            new QueryMemoryRawDataRequest(
                                    request.requiredUserId(),
                                    request.requiredAgentId(),
                                    List.of(),
                                    List.of(),
                                    null,
                                    metadataFilter,
                                    new QueryMemoryRawDataRequest.IncludeOptions(false, true),
                                    effectiveLimit,
                                    cursor));
            entries.addAll(
                    rawData.rawData().stream().map(MemindMcpToolService::recentRawData).toList());
        }
        List<MemindRecentResponse.Entry> sorted =
                entries.stream()
                        .sorted(Comparator.comparing(MemindMcpToolService::sortTime).reversed())
                        .limit(effectiveLimit)
                        .toList();
        return new MemindRecentResponse(sorted, null);
    }

    @McpTool(
            name = "memind_extract_text",
            title = "Extract standalone text into Memind memory",
            description =
                    "Extract memory immediately from one standalone text payload, such as pasted "
                            + "notes, document excerpts, or summaries. For ongoing chat turns, "
                            + "use memind_add_message and memind_commit instead.")
    public ExtractMemoryResponse extractText(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Standalone text to extract into memory.") String text,
            @McpToolParam(
                            required = false,
                            description =
                                    "Source marker stored with the extraction request. Defaults "
                                            + "to mcp.")
                    String sourceClient) {
        var request = new MemindExtractTextToolRequest(userId, agentId, text, sourceClient);
        log.debug(
                "MCP tool memind_extract_text invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.extract(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_extract_rawdata",
            title = "Extract typed rawdata into Memind memory",
            description =
                    "Extract memory immediately from a typed rawdata payload using Memind's "
                            + "registered rawdata content types.")
    public ExtractMemoryResponse extractRawData(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Rawdata type discriminator, such as conversation.")
                    String type,
            @McpToolParam(description = "Rawdata content payload without a type key.")
                    Map<String, Object> content,
            @McpToolParam(required = false, description = "Metadata to attach to the rawdata.")
                    Map<String, Object> metadata,
            @McpToolParam(required = false, description = "Source marker. Defaults to mcp.")
                    String sourceClient) {
        var request =
                new MemindExtractRawDataToolRequest(
                        userId, agentId, type, content, metadata, sourceClient);
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.putAll(request.requiredContent());
        rawPayload.put("type", request.requiredType());
        RawContent rawContent = rawDataObjectMapper.convertValue(rawPayload, RawContent.class);
        if (!request.effectiveMetadata().isEmpty()) {
            rawContent = rawContent.withMetadata(request.effectiveMetadata());
        }
        return memoryService.extract(
                new ExtractMemoryRequest(
                        request.requiredUserId(),
                        request.requiredAgentId(),
                        rawContent,
                        request.effectiveSourceClient()));
    }

    @McpTool(
            name = "memind_add_message",
            title = "Add a conversation message to Memind memory",
            description =
                    "Add one user or assistant conversation message to Memind's buffered "
                            + "conversation flow. Use this for conversation-style memory, then "
                            + "call memind_commit to flush pending messages.")
    public AddMessageResponse addMessage(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Message role: user or assistant.") String role,
            @McpToolParam(description = "Plain text message body.") String content,
            @McpToolParam(
                            required = false,
                            description = "Source marker attached to the message. Defaults to mcp.")
                    String sourceClient) {
        var request = new MemindAddMessageToolRequest(userId, agentId, role, content, sourceClient);
        log.debug(
                "MCP tool memind_add_message invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.addMessage(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_commit",
            title = "Commit pending Memind conversation memory",
            description =
                    "Flush pending conversation messages for a userId and agentId. Use this after "
                            + "memind_add_message when conversation turns should be committed to "
                            + "memory.")
    public ExtractMemoryResponse commit(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(
                            required = false,
                            description =
                                    "Source marker for the commit operation. Defaults to mcp.")
                    String sourceClient) {
        var request = new MemindCommitToolRequest(userId, agentId, sourceClient);
        log.debug(
                "MCP tool memind_commit invoked for userId={} agentId={}",
                request.userId(),
                request.agentId());
        return memoryService.commit(request.toApplicationRequest());
    }

    @McpTool(
            name = "memind_items_search",
            title = "Search Memind memory items",
            description = "Search persisted memory items by scope, category, source and metadata.")
    public QueryMemoryItemsResponse itemsSearch(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(required = false, description = "Memory item scope filter.") String scope,
            @McpToolParam(required = false, description = "Memory item category filters.")
                    List<String> categories,
            @McpToolParam(required = false, description = "Source client filters.")
                    List<String> sourceClients,
            @McpToolParam(required = false, description = "Rawdata type filters.")
                    List<String> rawDataTypes,
            @McpToolParam(required = false, description = "Occurred-at time range.")
                    QueryMemoryItemsRequest.TimeRange timeRange,
            @McpToolParam(required = false, description = "Metadata filter.")
                    MetadataFilter metadataFilter,
            @McpToolParam(required = false, description = "Maximum results.") Integer limit,
            @McpToolParam(required = false, description = "Reserved pagination cursor.")
                    String cursor) {
        var request =
                new MemindQueryItemsToolRequest(
                        userId,
                        agentId,
                        scope,
                        categories,
                        sourceClients,
                        rawDataTypes,
                        timeRange,
                        metadataFilter,
                        limit,
                        cursor);
        return assetQueryService.queryItems(request.toApplicationRequest(properties));
    }

    @McpTool(
            name = "memind_items_get",
            title = "Get Memind memory items",
            description = "Fetch memory items by ID after userId and agentId scope filtering.")
    public MemindItemsGetResponse itemsGet(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Memory item IDs.") List<String> ids) {
        var request = new MemindGetItemsToolRequest(userId, agentId, ids);
        return assetQueryService.getItemsByIds(
                request.requiredUserId(),
                request.requiredAgentId(),
                request.requiredLongIds(properties.maxIdsPerRequest()));
    }

    @McpTool(
            name = "memind_items_sources",
            title = "Get Memind memory item sources",
            description =
                    "Fetch source rawdata records for memory item IDs with optional segments.")
    public MemindItemSourcesResponse itemsSources(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Memory item IDs.") List<String> ids,
            @McpToolParam(required = false, description = "Whether to include rawdata segments.")
                    Boolean includeSegment) {
        var request = new MemindItemSourcesToolRequest(userId, agentId, ids, includeSegment);
        return assetQueryService.getItemSourcesByItemIds(
                request.requiredUserId(),
                request.requiredAgentId(),
                request.requiredLongIds(properties.maxIdsPerRequest()),
                request.effectiveIncludeSegment(),
                properties.maxRawSegmentChars());
    }

    @McpTool(
            name = "memind_rawdata_search",
            title = "Search Memind rawdata",
            description = "Search rawdata records by type, source, time range and metadata.")
    public QueryMemoryRawDataResponse rawDataSearch(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(required = false, description = "Rawdata type filters.")
                    List<String> types,
            @McpToolParam(required = false, description = "Source client filters.")
                    List<String> sourceClients,
            @McpToolParam(required = false, description = "Rawdata start time range.")
                    QueryMemoryRawDataRequest.TimeRange timeRange,
            @McpToolParam(required = false, description = "Metadata filter.")
                    MetadataFilter metadataFilter,
            @McpToolParam(required = false, description = "Whether to include rawdata segments.")
                    Boolean includeSegment,
            @McpToolParam(required = false, description = "Whether to include rawdata metadata.")
                    Boolean includeMetadata,
            @McpToolParam(required = false, description = "Maximum results.") Integer limit,
            @McpToolParam(required = false, description = "Reserved pagination cursor.")
                    String cursor) {
        var request =
                new MemindQueryRawDataToolRequest(
                        userId,
                        agentId,
                        types,
                        sourceClients,
                        timeRange,
                        metadataFilter,
                        includeSegment,
                        includeMetadata,
                        limit,
                        cursor);
        return assetQueryService.queryRawData(request.toApplicationRequest(properties));
    }

    @McpTool(
            name = "memind_rawdata_get",
            title = "Get Memind rawdata",
            description = "Fetch rawdata records by ID after userId and agentId scope filtering.")
    public MemindRawDataGetResponse rawDataGet(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Rawdata IDs.") List<String> ids,
            @McpToolParam(required = false, description = "Whether to include rawdata segments.")
                    Boolean includeSegment) {
        var request = new MemindGetRawDataToolRequest(userId, agentId, ids, includeSegment);
        return assetQueryService.getRawDataByIds(
                request.requiredUserId(),
                request.requiredAgentId(),
                request.requiredIds(properties.maxIdsPerRequest()),
                request.effectiveIncludeSegment(),
                properties.maxRawSegmentChars());
    }

    private static MemindRecentResponse.Entry recentItem(
            QueryMemoryItemsResponse.MemoryItemView item) {
        return new MemindRecentResponse.Entry(
                "ITEM",
                item.id(),
                item.category(),
                item.text(),
                item.sourceClient(),
                firstNonNull(item.occurredAt(), item.observedAt()),
                item.createdAt(),
                item.metadata());
    }

    private static MemindRecentResponse.Entry recentRawData(
            QueryMemoryRawDataResponse.MemoryRawDataView rawData) {
        return new MemindRecentResponse.Entry(
                "RAWDATA",
                rawData.id(),
                rawData.type(),
                rawData.caption(),
                rawData.sourceClient(),
                firstNonNull(rawData.startTime(), rawData.endTime()),
                rawData.createdAt(),
                rawData.metadata());
    }

    private static Instant sortTime(MemindRecentResponse.Entry entry) {
        return firstNonNull(entry.occurredAt(), entry.createdAt(), Instant.EPOCH);
    }

    private static Instant firstNonNull(Instant first, Instant second) {
        return first != null ? first : second;
    }

    private static Instant firstNonNull(Instant first, Instant second, Instant third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }
}
