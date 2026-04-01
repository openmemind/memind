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
package com.openmemind.ai.memory.server.service.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.mapper.rawdata.AdminRawDataQueryMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RawDataQueryServiceTest {

    @Test
    void rawDataQueryUsesApprovedDefaultSort() {
        CapturingRawDataQueryMapper rawDataQueryMapper = new CapturingRawDataQueryMapper();
        RawDataQueryService service = new RawDataQueryService(rawDataQueryMapper);

        service.listRawData(RawDataPageQuery.of(1, 20, "u1", "a1", null, null));

        assertThat(rawDataQueryMapper.lastPageQuery()).isNotNull();
        assertThat(rawDataQueryMapper.lastPageQuery().pageNo()).isEqualTo(1);
        assertThat(rawDataQueryMapper.lastPageQuery().pageSize()).isEqualTo(20);
        assertThat(rawDataQueryMapper.lastPageQuery().orderBy())
                .containsExactly("start_time DESC", "created_at DESC");
    }

    @Test
    void getRawDataThrowsWhenResourceIsMissing() {
        RawDataQueryService service = new RawDataQueryService(new CapturingRawDataQueryMapper());

        assertThatThrownBy(() -> service.getRawData("rd-missing"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("rd-missing");
    }

    private static final class CapturingRawDataQueryMapper implements AdminRawDataQueryMapper {

        private RawDataPageQuery lastPageQuery;

        @Override
        public PageResponse<AdminRawDataView> page(RawDataPageQuery query) {
            this.lastPageQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 0, List.of());
        }

        @Override
        public Optional<AdminRawDataView> findByBizId(String rawDataId) {
            return Optional.empty();
        }

        @Override
        public List<AdminRawDataView> findByBizIds(Collection<String> rawDataIds) {
            return List.of();
        }

        @Override
        public int logicalDeleteByBizIds(Collection<String> rawDataIds) {
            return 0;
        }

        private RawDataPageQuery lastPageQuery() {
            return lastPageQuery;
        }
    }
}
