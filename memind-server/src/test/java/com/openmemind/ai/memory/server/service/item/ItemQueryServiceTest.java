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
package com.openmemind.ai.memory.server.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memory.request.MetadataFilter;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import com.openmemind.ai.memory.server.mapper.rawdata.AdminRawDataQueryMapper;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemQueryServiceTest {

    @Test
    void itemQueryUsesApprovedDefaultSort() {
        CapturingItemQueryMapper itemQueryMapper = new CapturingItemQueryMapper();
        ItemQueryService service = new ItemQueryService(itemQueryMapper);

        service.listItems(ItemPageQuery.of(1, 20, "u1", "a1", "user", "profile", "FACT", "rd-1"));

        assertThat(itemQueryMapper.lastPageQuery()).isNotNull();
        assertThat(itemQueryMapper.lastPageQuery().pageNo()).isEqualTo(1);
        assertThat(itemQueryMapper.lastPageQuery().pageSize()).isEqualTo(20);
        assertThat(itemQueryMapper.lastPageQuery().orderBy())
                .containsExactly("observed_at DESC", "created_at DESC", "biz_id DESC");
    }

    @Test
    void getItemThrowsWhenResourceIsMissing() {
        ItemQueryService service = new ItemQueryService(new CapturingItemQueryMapper());

        assertThatThrownBy(() -> service.getItem(101L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("101");
    }

    @Test
    void openItemQueryMapsAndFiltersStructuredRequest() {
        CapturingItemQueryMapper itemQueryMapper = new CapturingItemQueryMapper();
        itemQueryMapper.page =
                new PageResponse<>(
                        1,
                        2,
                        2,
                        List.of(
                                itemView(
                                        101L,
                                        "tool",
                                        "claude-code",
                                        Instant.parse("2026-05-24T12:00:00Z"),
                                        Map.of("projectSlug", "memind")),
                                itemView(
                                        102L,
                                        "tool",
                                        "claude-code",
                                        Instant.parse("2026-05-24T12:05:00Z"),
                                        Map.of("projectSlug", "other"))));
        OpenMemoryAssetQueryService service =
                new OpenMemoryAssetQueryService(
                        new ItemQueryService(itemQueryMapper),
                        new RawDataQueryService(new CapturingRawDataQueryMapper()));

        var response =
                service.queryItems(
                        new QueryMemoryItemsRequest(
                                "u1",
                                "a1",
                                "AGENT",
                                List.of("tool"),
                                List.of("claude-code"),
                                List.of("agent_timeline"),
                                new QueryMemoryItemsRequest.TimeRange(
                                        "occurredAt",
                                        Instant.parse("2026-05-01T00:00:00Z"),
                                        Instant.parse("2026-05-27T00:00:00Z")),
                                new MetadataFilter(
                                        List.of(
                                                new MetadataFilter.Condition(
                                                        "projectSlug", "eq", "memind")),
                                        List.of(),
                                        List.of()),
                                2,
                                null));

        assertThat(itemQueryMapper.lastPageQuery()).isNotNull();
        assertThat(itemQueryMapper.lastPageQuery().pageSize()).isEqualTo(2);
        assertThat(itemQueryMapper.lastPageQuery().scope()).isEqualTo("AGENT");
        assertThat(itemQueryMapper.lastPageQuery().categories()).containsExactly("tool");
        assertThat(response.items()).singleElement().extracting("id").isEqualTo("101");
    }

    private static AdminItemView itemView(
            Long itemId,
            String category,
            String sourceClient,
            Instant occurredAt,
            Map<String, Object> metadata) {
        return new AdminItemView(
                itemId,
                "u1",
                "a1",
                "u1:a1",
                "content-" + itemId,
                "AGENT",
                category,
                "vec-" + itemId,
                "rd-" + itemId,
                "hash-" + itemId,
                occurredAt,
                occurredAt,
                metadata,
                "FACT",
                "agent_timeline",
                sourceClient,
                occurredAt,
                occurredAt);
    }

    private static final class CapturingItemQueryMapper implements AdminItemQueryMapper {

        private ItemPageQuery lastPageQuery;

        @Override
        public PageResponse<AdminItemView> page(ItemPageQuery query) {
            this.lastPageQuery = query;
            return page != null
                    ? page
                    : new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminItemView> findByBizId(Long itemId) {
            return Optional.empty();
        }

        @Override
        public List<AdminItemView> findByBizIds(Collection<Long> itemIds) {
            return List.of();
        }

        @Override
        public List<AdminItemView> findByRawDataIds(Collection<String> rawDataIds) {
            return List.of();
        }

        private ItemPageQuery lastPageQuery() {
            return lastPageQuery;
        }

        private PageResponse<AdminItemView> page;
    }

    private static final class CapturingRawDataQueryMapper implements AdminRawDataQueryMapper {

        @Override
        public PageResponse<com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView>
                page(com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView>
                findByBizId(String rawDataId) {
            return Optional.empty();
        }

        @Override
        public List<com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView>
                findByBizIds(Collection<String> rawDataIds) {
            return List.of();
        }

        @Override
        public int logicalDeleteByBizIds(Collection<String> rawDataIds) {
            return 0;
        }
    }
}
