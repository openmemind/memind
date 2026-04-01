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
package com.openmemind.ai.memory.server.service.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.insight.query.InsightPageQuery;
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import com.openmemind.ai.memory.server.mapper.insight.AdminInsightQueryMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InsightQueryServiceTest {

    @Test
    void insightQueryUsesApprovedDefaultSort() {
        CapturingInsightQueryMapper insightQueryMapper = new CapturingInsightQueryMapper();
        InsightQueryService service = new InsightQueryService(insightQueryMapper);

        service.listInsights(InsightPageQuery.of(1, 20, "u1", "a1", "user", "profile", "LEAF"));

        assertThat(insightQueryMapper.lastPageQuery()).isNotNull();
        assertThat(insightQueryMapper.lastPageQuery().pageNo()).isEqualTo(1);
        assertThat(insightQueryMapper.lastPageQuery().pageSize()).isEqualTo(20);
        assertThat(insightQueryMapper.lastPageQuery().orderBy())
                .containsExactly("last_reasoned_at DESC", "created_at DESC", "biz_id DESC");
    }

    @Test
    void getInsightThrowsWhenResourceIsMissing() {
        InsightQueryService service = new InsightQueryService(new CapturingInsightQueryMapper());

        assertThatThrownBy(() -> service.getInsight(201L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("201");
    }

    private static final class CapturingInsightQueryMapper implements AdminInsightQueryMapper {

        private InsightPageQuery lastPageQuery;

        @Override
        public PageResponse<AdminInsightView> page(InsightPageQuery query) {
            this.lastPageQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminInsightView> findByBizId(Long insightId) {
            return Optional.empty();
        }

        @Override
        public List<AdminInsightView> findByBizIds(Collection<Long> insightIds) {
            return List.of();
        }

        private InsightPageQuery lastPageQuery() {
            return lastPageQuery;
        }
    }
}
