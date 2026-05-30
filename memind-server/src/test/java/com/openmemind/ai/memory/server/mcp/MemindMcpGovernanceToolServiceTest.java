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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.rawdata.response.RawDataDeleteResult;
import com.openmemind.ai.memory.server.mcp.config.MemindMcpToolProperties;
import com.openmemind.ai.memory.server.mcp.response.MemindItemsGetResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindRawDataGetResponse;
import com.openmemind.ai.memory.server.service.item.ItemDeleteService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataDeleteService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemindMcpGovernanceToolServiceTest {

    private final RecordingOpenMemoryAssetQueryService assetQueryService =
            new RecordingOpenMemoryAssetQueryService();
    private final RecordingItemDeleteService itemDeleteService = new RecordingItemDeleteService();
    private final RecordingRawDataDeleteService rawDataDeleteService =
            new RecordingRawDataDeleteService();
    private final MemindMcpGovernanceToolService toolService =
            new MemindMcpGovernanceToolService(
                    assetQueryService,
                    itemDeleteService,
                    rawDataDeleteService,
                    new MemindMcpToolProperties(true, 1800, 6000, 50, 100, 50, 12000));

    @Test
    void forgetDefaultsToDryRunAndDoesNotDeleteItems() {
        assetQueryService.itemsResponse =
                new MemindItemsGetResponse(List.of(item("101")), List.of("999"));

        var response =
                toolService.forget(
                        "user-1", "agent-1", "ITEM", List.of("101", "999"), "obsolete", null);

        assertThat(response.status()).isEqualTo("DRY_RUN");
        assertThat(response.dryRun()).isTrue();
        assertThat(response.blocked()).containsExactly("101");
        assertThat(response.notFound()).containsExactly("999");
        assertThat(response.deleted()).isEmpty();
        assertThat(itemDeleteService.deletedIds).isNull();
    }

    @Test
    void forgetDeletesOnlyScopedFoundItemsWhenDryRunIsFalse() {
        assetQueryService.itemsResponse =
                new MemindItemsGetResponse(List.of(item("101"), item("102")), List.of("999"));

        var response =
                toolService.forget(
                        "user-1",
                        "agent-1",
                        "ITEM",
                        List.of("101", "102", "999"),
                        "user request",
                        false);

        assertThat(response.status()).isEqualTo("DELETED");
        assertThat(response.dryRun()).isFalse();
        assertThat(response.deleted()).containsExactly("101", "102");
        assertThat(response.notFound()).containsExactly("999");
        assertThat(response.blocked()).isEmpty();
        assertThat(itemDeleteService.deletedIds).containsExactly(101L, 102L);
    }

    @Test
    void forgetDeletesOnlyScopedFoundRawDataWhenDryRunIsFalse() {
        assetQueryService.rawDataResponse =
                new MemindRawDataGetResponse(List.of(rawData("rd-1")), List.of("rd-404"));

        var response =
                toolService.forget(
                        "user-1",
                        "agent-1",
                        "RAWDATA",
                        List.of("rd-1", "rd-404"),
                        "cleanup",
                        false);

        assertThat(response.status()).isEqualTo("DELETED");
        assertThat(response.deleted()).containsExactly("rd-1");
        assertThat(response.notFound()).containsExactly("rd-404");
        assertThat(rawDataDeleteService.deletedIds).containsExactly("rd-1");
    }

    @Test
    void forgetRejectsInsightTargetType() {
        assertThatThrownBy(
                        () ->
                                toolService.forget(
                                        "user-1",
                                        "agent-1",
                                        "INSIGHT",
                                        List.of("1"),
                                        "cleanup",
                                        true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetType must be one of ITEM, RAWDATA");
    }

    private static QueryMemoryItemsResponse.MemoryItemView item(String id) {
        return new QueryMemoryItemsResponse.MemoryItemView(
                id,
                "content " + id,
                "ALL",
                "fact",
                "FACT",
                "rd-" + id,
                "conversation",
                "mcp",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Map.of());
    }

    private static MemindRawDataGetResponse.RawData rawData(String id) {
        return new MemindRawDataGetResponse.RawData(
                id,
                "conversation",
                "mcp",
                "caption",
                Map.of(),
                null,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));
    }

    private static final class RecordingOpenMemoryAssetQueryService
            extends OpenMemoryAssetQueryService {

        private MemindItemsGetResponse itemsResponse =
                new MemindItemsGetResponse(List.of(), List.of());
        private MemindRawDataGetResponse rawDataResponse =
                new MemindRawDataGetResponse(List.of(), List.of());

        private RecordingOpenMemoryAssetQueryService() {
            super(null, null);
        }

        @Override
        public MemindItemsGetResponse getItemsByIds(
                String userId, String agentId, List<Long> itemIds) {
            return itemsResponse;
        }

        @Override
        public MemindRawDataGetResponse getRawDataByIds(
                String userId,
                String agentId,
                List<String> rawDataIds,
                boolean includeSegment,
                int maxSegmentChars) {
            return rawDataResponse;
        }
    }

    private static final class RecordingItemDeleteService extends ItemDeleteService {

        private List<Long> deletedIds;

        private RecordingItemDeleteService() {
            super(null, null);
        }

        @Override
        public BatchDeleteResult deleteItems(List<Long> itemIds) {
            this.deletedIds = List.copyOf(itemIds);
            return new BatchDeleteResult(itemIds.size(), List.of("user-1:agent-1"));
        }
    }

    private static final class RecordingRawDataDeleteService extends RawDataDeleteService {

        private List<String> deletedIds;

        private RecordingRawDataDeleteService() {
            super(null, null, null, null);
        }

        @Override
        public RawDataDeleteResult deleteRawData(List<String> rawDataIds) {
            this.deletedIds = List.copyOf(rawDataIds);
            return new RawDataDeleteResult(rawDataIds.size(), 0, List.of("user-1:agent-1"), true);
        }
    }
}
