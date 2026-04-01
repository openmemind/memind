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
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import java.util.Collection;
import java.util.List;
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

    private static final class CapturingItemQueryMapper implements AdminItemQueryMapper {

        private ItemPageQuery lastPageQuery;

        @Override
        public PageResponse<AdminItemView> page(ItemPageQuery query) {
            this.lastPageQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
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
    }
}
