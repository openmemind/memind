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
package com.openmemind.ai.memory.server.mapper.rawdata;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminRawDataQueryMapper {

    PageResponse<AdminRawDataView> page(RawDataPageQuery query);

    Optional<AdminRawDataView> findByBizId(String rawDataId);

    List<AdminRawDataView> findByBizIds(Collection<String> rawDataIds);

    int logicalDeleteByBizIds(Collection<String> rawDataIds);
}

@Component
final class MybatisAdminRawDataQueryMapper implements AdminRawDataQueryMapper {

    private static final String INSTANT_TYPE_HANDLER =
            "typeHandler=" + InstantTypeHandler.class.getName();

    private final MemoryRawDataMapper rawDataMapper;

    MybatisAdminRawDataQueryMapper(MemoryRawDataMapper rawDataMapper) {
        this.rawDataMapper = rawDataMapper;
    }

    @Override
    public PageResponse<AdminRawDataView> page(RawDataPageQuery query) {
        Page<MemoryRawDataDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(MemoryRawDataDO.class);
        if (StringUtils.hasText(query.userId())) {
            wrapper.eq(MemoryRawDataDO::getUserId, query.userId());
        }
        if (StringUtils.hasText(query.agentId())) {
            wrapper.eq(MemoryRawDataDO::getAgentId, query.agentId());
        }
        if (query.startTimeFrom() != null) {
            wrapper.apply("start_time >= {0," + INSTANT_TYPE_HANDLER + "}", query.startTimeFrom());
        }
        if (query.startTimeTo() != null) {
            wrapper.apply("start_time <= {0," + INSTANT_TYPE_HANDLER + "}", query.startTimeTo());
        }
        wrapper.orderByDesc(MemoryRawDataDO::getStartTime, MemoryRawDataDO::getCreatedAt);
        Page<MemoryRawDataDO> result = rawDataMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream().map(MybatisAdminRawDataQueryMapper::toView).toList());
    }

    @Override
    public Optional<AdminRawDataView> findByBizId(String rawDataId) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryRawDataDO.class)
                        .eq(MemoryRawDataDO::getBizId, rawDataId);
        return Optional.ofNullable(rawDataMapper.selectOne(wrapper))
                .map(MybatisAdminRawDataQueryMapper::toView);
    }

    @Override
    public List<AdminRawDataView> findByBizIds(Collection<String> rawDataIds) {
        if (rawDataIds == null || rawDataIds.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryRawDataDO.class)
                        .in(MemoryRawDataDO::getBizId, rawDataIds);
        return rawDataMapper.selectList(wrapper).stream()
                .map(MybatisAdminRawDataQueryMapper::toView)
                .toList();
    }

    @Override
    public int logicalDeleteByBizIds(Collection<String> rawDataIds) {
        if (rawDataIds == null || rawDataIds.isEmpty()) {
            return 0;
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryRawDataDO.class)
                        .in(MemoryRawDataDO::getBizId, rawDataIds);
        return rawDataMapper.delete(wrapper);
    }

    private static AdminRawDataView toView(MemoryRawDataDO dataObject) {
        return new AdminRawDataView(
                dataObject.getBizId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getType(),
                dataObject.getSourceClient(),
                dataObject.getContentId(),
                dataObject.getSegment(),
                dataObject.getCaption(),
                dataObject.getCaptionVectorId(),
                dataObject.getMetadata(),
                dataObject.getStartTime(),
                dataObject.getEndTime(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }
}
