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
package com.openmemind.ai.memory.server.service.memory;

import com.openmemind.ai.memory.core.retrieval.filter.MetadataFilterMatcher;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memory.request.MetadataFilter;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.mcp.response.MemindItemSourcesResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindItemsGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRawDataGetResponse;
import com.openmemind.ai.memory.server.mcp.support.MemindMcpSegmentLimiter;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OpenMemoryAssetQueryService {

    private final ItemQueryService itemQueryService;
    private final RawDataQueryService rawDataQueryService;

    public OpenMemoryAssetQueryService(
            ItemQueryService itemQueryService, RawDataQueryService rawDataQueryService) {
        this.itemQueryService = itemQueryService;
        this.rawDataQueryService = rawDataQueryService;
    }

    public QueryMemoryItemsResponse queryItems(QueryMemoryItemsRequest request) {
        QueryMemoryItemsRequest.TimeRange timeRange = request.timeRange();
        var page =
                itemQueryService.listItems(
                        ItemPageQuery.openApi(
                                1,
                                request.effectiveLimit(),
                                request.userId(),
                                request.agentId(),
                                request.scope(),
                                request.categories(),
                                request.sourceClients(),
                                request.rawDataTypes(),
                                timeRange == null ? null : timeRange.from(),
                                timeRange == null ? null : timeRange.to()));
        List<QueryMemoryItemsResponse.MemoryItemView> items =
                page.items().stream()
                        .filter(item -> matchesMetadata(item.metadata(), request.metadataFilter()))
                        .map(OpenMemoryAssetQueryService::toItemView)
                        .toList();
        return new QueryMemoryItemsResponse(items, null);
    }

    public MemindItemsGetResponse getItemsByIds(String userId, String agentId, List<Long> itemIds) {
        List<AdminItemView> scopedItems =
                itemQueryService.listItemsByIds(itemIds).stream()
                        .filter(
                                item ->
                                        matchesScope(
                                                item.userId(), item.agentId(), userId, agentId))
                        .toList();
        List<String> foundIds =
                scopedItems.stream().map(AdminItemView::itemId).map(String::valueOf).toList();
        List<String> requestedIds = itemIds.stream().map(String::valueOf).toList();
        return new MemindItemsGetResponse(
                scopedItems.stream().map(OpenMemoryAssetQueryService::toItemView).toList(),
                missingIds(requestedIds, foundIds));
    }

    public MemindRawDataGetResponse getRawDataByIds(
            String userId,
            String agentId,
            List<String> rawDataIds,
            boolean includeSegment,
            int maxSegmentChars) {
        List<AdminRawDataView> scopedRawData =
                rawDataQueryService.listRawDataByIds(rawDataIds).stream()
                        .filter(raw -> matchesScope(raw.userId(), raw.agentId(), userId, agentId))
                        .toList();
        List<String> foundIds = scopedRawData.stream().map(AdminRawDataView::rawDataId).toList();
        return new MemindRawDataGetResponse(
                scopedRawData.stream()
                        .map(raw -> toMcpRawData(raw, includeSegment, maxSegmentChars))
                        .toList(),
                missingIds(rawDataIds, foundIds));
    }

    public MemindItemSourcesResponse getItemSourcesByItemIds(
            String userId,
            String agentId,
            List<Long> itemIds,
            boolean includeSegment,
            int maxSegmentChars) {
        List<AdminItemView> scopedItems =
                itemQueryService.listItemsByIds(itemIds).stream()
                        .filter(
                                item ->
                                        matchesScope(
                                                item.userId(), item.agentId(), userId, agentId))
                        .toList();
        List<String> foundItemIds =
                scopedItems.stream().map(AdminItemView::itemId).map(String::valueOf).toList();
        List<String> rawDataIds =
                scopedItems.stream()
                        .map(AdminItemView::rawDataId)
                        .filter(OpenMemoryAssetQueryService::hasText)
                        .distinct()
                        .toList();
        Map<String, AdminRawDataView> rawDataById =
                rawDataQueryService.listRawDataByIds(rawDataIds).stream()
                        .filter(raw -> matchesScope(raw.userId(), raw.agentId(), userId, agentId))
                        .collect(
                                Collectors.toMap(
                                        AdminRawDataView::rawDataId,
                                        Function.identity(),
                                        (left, right) -> left));
        List<MemindItemSourcesResponse.Source> sources =
                scopedItems.stream()
                        .map(
                                item ->
                                        toItemSource(
                                                item,
                                                rawDataById.get(item.rawDataId()),
                                                includeSegment,
                                                maxSegmentChars))
                        .filter(Objects::nonNull)
                        .toList();
        List<String> requestedItemIds = itemIds.stream().map(String::valueOf).toList();
        return new MemindItemSourcesResponse(sources, missingIds(requestedItemIds, foundItemIds));
    }

    public QueryMemoryRawDataResponse queryRawData(QueryMemoryRawDataRequest request) {
        QueryMemoryRawDataRequest.TimeRange timeRange = request.timeRange();
        var page =
                rawDataQueryService.listRawData(
                        RawDataPageQuery.openApi(
                                1,
                                request.effectiveLimit(),
                                request.userId(),
                                request.agentId(),
                                timeRange == null ? null : timeRange.from(),
                                timeRange == null ? null : timeRange.to(),
                                request.types(),
                                request.sourceClients()));
        QueryMemoryRawDataRequest.IncludeOptions include = request.effectiveInclude();
        List<QueryMemoryRawDataResponse.MemoryRawDataView> rawData =
                page.items().stream()
                        .filter(raw -> matchesMetadata(raw.metadata(), request.metadataFilter()))
                        .map(raw -> toRawDataView(raw, include))
                        .toList();
        return new QueryMemoryRawDataResponse(rawData, null);
    }

    private static MemindRawDataGetResponse.RawData toMcpRawData(
            AdminRawDataView rawData, boolean includeSegment, int maxSegmentChars) {
        return new MemindRawDataGetResponse.RawData(
                rawData.rawDataId(),
                rawData.type(),
                rawData.sourceClient(),
                rawData.caption(),
                rawData.metadata(),
                includeSegment
                        ? MemindMcpSegmentLimiter.limit(rawData.segment(), maxSegmentChars)
                        : null,
                rawData.startTime(),
                rawData.endTime(),
                rawData.createdAt());
    }

    private static MemindItemSourcesResponse.Source toItemSource(
            AdminItemView item,
            AdminRawDataView rawData,
            boolean includeSegment,
            int maxSegmentChars) {
        if (rawData == null) {
            return null;
        }
        return new MemindItemSourcesResponse.Source(
                String.valueOf(item.itemId()),
                rawData.rawDataId(),
                rawData.type(),
                rawData.caption(),
                rawData.sourceClient(),
                rawData.metadata(),
                includeSegment
                        ? MemindMcpSegmentLimiter.limit(rawData.segment(), maxSegmentChars)
                        : null);
    }

    private static boolean matchesScope(
            String actualUserId,
            String actualAgentId,
            String expectedUserId,
            String expectedAgentId) {
        return Objects.equals(actualUserId, expectedUserId)
                && Objects.equals(actualAgentId, expectedAgentId);
    }

    private static List<String> missingIds(List<String> requestedIds, List<String> foundIds) {
        var found = new LinkedHashSet<>(foundIds);
        return requestedIds.stream().filter(id -> !found.contains(id)).toList();
    }

    private static boolean matchesMetadata(Map<String, Object> metadata, MetadataFilter filter) {
        return filter == null
                || filter.isEmpty()
                || MetadataFilterMatcher.matches(metadata, filter.toCoreFilter());
    }

    private static QueryMemoryItemsResponse.MemoryItemView toItemView(AdminItemView item) {
        return new QueryMemoryItemsResponse.MemoryItemView(
                item.itemId() == null ? null : String.valueOf(item.itemId()),
                item.content(),
                item.scope(),
                item.category(),
                item.type(),
                item.rawDataId(),
                item.rawDataType(),
                item.sourceClient(),
                item.occurredAt(),
                item.observedAt(),
                createdAt(item),
                item.metadata());
    }

    private static QueryMemoryRawDataResponse.MemoryRawDataView toRawDataView(
            AdminRawDataView rawData, QueryMemoryRawDataRequest.IncludeOptions include) {
        return new QueryMemoryRawDataResponse.MemoryRawDataView(
                rawData.rawDataId(),
                rawData.type(),
                rawData.sourceClient(),
                rawData.caption(),
                include.includeMetadata() ? rawData.metadata() : Map.of(),
                include.includeSegment() ? rawData.segment() : null,
                rawData.startTime(),
                rawData.endTime(),
                rawData.createdAt());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Instant createdAt(AdminItemView item) {
        return item.createdAt();
    }
}
