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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenMemoryAssetQueryServiceMcpTest {

    private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
    private final RawDataQueryService rawDataQueryService = mock(RawDataQueryService.class);
    private final OpenMemoryAssetQueryService service =
            new OpenMemoryAssetQueryService(itemQueryService, rawDataQueryService);

    @Test
    void getItemsByIdsFiltersByUserAndAgentScope() {
        when(itemQueryService.listItemsByIds(List.of(1L, 2L)))
                .thenReturn(List.of(item("u1", "a1", 1L, "raw-1"), item("u2", "a1", 2L, "raw-2")));

        var result = service.getItemsByIds("u1", "a1", List.of(1L, 2L));

        assertThat(result.items())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo("1"));
        assertThat(result.missingItemIds()).containsExactly("2");
    }

    @Test
    void getRawDataByIdsFiltersByUserAndAgentScopeAndHidesSegmentByDefault() {
        when(rawDataQueryService.listRawDataByIds(List.of("raw-1", "raw-2")))
                .thenReturn(List.of(raw("u1", "a1", "raw-1"), raw("u2", "a1", "raw-2")));

        var result = service.getRawDataByIds("u1", "a1", List.of("raw-1", "raw-2"), false, 100);

        assertThat(result.rawData())
                .singleElement()
                .satisfies(raw -> assertThat(raw.id()).isEqualTo("raw-1"));
        assertThat(result.rawData().get(0).segment()).isNull();
        assertThat(result.missingRawDataIds()).containsExactly("raw-2");
    }

    @Test
    void getItemSourcesReturnsScopedRawDataForScopedItemsOnly() {
        when(itemQueryService.listItemsByIds(List.of(1L, 2L)))
                .thenReturn(List.of(item("u1", "a1", 1L, "raw-1"), item("u2", "a1", 2L, "raw-2")));
        when(rawDataQueryService.listRawDataByIds(List.of("raw-1")))
                .thenReturn(List.of(raw("u1", "a1", "raw-1")));

        var result = service.getItemSourcesByItemIds("u1", "a1", List.of(1L, 2L), true, 100);

        assertThat(result.sources())
                .singleElement()
                .satisfies(
                        source -> {
                            assertThat(source.itemId()).isEqualTo("1");
                            assertThat(source.rawDataId()).isEqualTo("raw-1");
                            assertThat(source.segment()).containsEntry("text", "segment");
                        });
        assertThat(result.missingItemIds()).containsExactly("2");
    }

    private static AdminItemView item(
            String userId, String agentId, Long itemId, String rawDataId) {
        return new AdminItemView(
                itemId,
                userId,
                agentId,
                userId + ":" + agentId,
                "content " + itemId,
                "ALL",
                "fact",
                "vector-" + itemId,
                rawDataId,
                "hash-" + itemId,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Map.of(),
                "FACT",
                "conversation",
                "mcp",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));
    }

    private static AdminRawDataView raw(String userId, String agentId, String rawDataId) {
        return new AdminRawDataView(
                rawDataId,
                userId,
                agentId,
                userId + ":" + agentId,
                "conversation",
                "mcp",
                "content-id",
                Map.of("text", "segment"),
                "caption",
                "caption-vector",
                Map.of("key", "value"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));
    }
}
