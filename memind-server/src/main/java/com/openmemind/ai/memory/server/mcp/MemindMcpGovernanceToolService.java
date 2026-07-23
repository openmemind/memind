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

import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.request.MemindForgetToolRequest;
import com.openmemind.ai.memory.server.mcp.request.MemindForgetToolRequest.TargetType;
import com.openmemind.ai.memory.server.mcp.response.MemindForgetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindItemsGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRawDataGetResponse;
import com.openmemind.ai.memory.server.service.item.ItemDeleteService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataDeleteService;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "memind.mcp", name = "governance-enabled", havingValue = "true")
public class MemindMcpGovernanceToolService implements MemindMcpToolProvider {

    private final OpenMemoryAssetQueryService assetQueryService;
    private final ItemDeleteService itemDeleteService;
    private final RawDataDeleteService rawDataDeleteService;
    private final MemindMcpToolProperties properties;

    public MemindMcpGovernanceToolService(
            OpenMemoryAssetQueryService assetQueryService,
            ItemDeleteService itemDeleteService,
            RawDataDeleteService rawDataDeleteService,
            MemindMcpToolProperties properties) {
        this.assetQueryService = assetQueryService;
        this.itemDeleteService = itemDeleteService;
        this.rawDataDeleteService = rawDataDeleteService;
        this.properties = properties;
    }

    @McpTool(
            name = "memind_forget",
            title = "Forget scoped Memind records",
            description =
                    "Delete memory items or rawdata after a scoped lookup. Defaults to dry-run "
                            + "and requires an explicit reason.")
    public MemindForgetResponse forget(
            @McpToolParam(description = "Memory user scope.") String userId,
            @McpToolParam(description = "Memory agent scope.") String agentId,
            @McpToolParam(description = "Target type: ITEM or RAWDATA.") String targetType,
            @McpToolParam(description = "Target IDs.") List<String> ids,
            @McpToolParam(description = "Reason for the forget request.") String reason,
            @McpToolParam(required = false, description = "Dry-run mode. Defaults to true.")
                    Boolean dryRun) {
        var request =
                MemindForgetToolRequest.of(
                        userId,
                        agentId,
                        targetType,
                        ids,
                        reason,
                        dryRun,
                        properties.maxIdsPerRequest());
        return switch (request.targetType()) {
            case ITEM -> forgetItems(request);
            case RAWDATA -> forgetRawData(request);
        };
    }

    private MemindForgetResponse forgetItems(MemindForgetToolRequest request) {
        MemindItemsGetResponse lookup =
                assetQueryService.getItemsByIds(
                        request.userId(),
                        request.agentId(),
                        request.longIds(properties.maxIdsPerRequest()));
        List<String> foundIds = lookup.items().stream().map(item -> item.id()).toList();
        if (request.dryRun()) {
            return MemindForgetResponse.dryRun(TargetType.ITEM, foundIds, lookup.missingItemIds());
        }
        itemDeleteService.deleteItems(foundIds.stream().map(Long::valueOf).toList());
        return MemindForgetResponse.deleted(TargetType.ITEM, foundIds, lookup.missingItemIds());
    }

    private MemindForgetResponse forgetRawData(MemindForgetToolRequest request) {
        MemindRawDataGetResponse lookup =
                assetQueryService.getRawDataByIds(
                        request.userId(),
                        request.agentId(),
                        request.ids(),
                        false,
                        properties.maxRawSegmentChars());
        List<String> foundIds = lookup.rawData().stream().map(rawData -> rawData.id()).toList();
        if (request.dryRun()) {
            return MemindForgetResponse.dryRun(
                    TargetType.RAWDATA, foundIds, lookup.missingRawDataIds());
        }
        rawDataDeleteService.deleteRawData(foundIds);
        return MemindForgetResponse.deleted(
                TargetType.RAWDATA, foundIds, lookup.missingRawDataIds());
    }
}
