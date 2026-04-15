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
package com.openmemind.ai.memory.server.mapper.insight;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.insight.query.InsightPageQuery;
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminInsightQueryMapper {

    PageResponse<AdminInsightView> page(InsightPageQuery query);

    Optional<AdminInsightView> findByBizId(Long insightId);

    List<AdminInsightView> findByBizIds(Collection<Long> insightIds);
}

@Component
final class MybatisAdminInsightQueryMapper implements AdminInsightQueryMapper {

    private final MemoryInsightMapper insightMapper;

    MybatisAdminInsightQueryMapper(MemoryInsightMapper insightMapper) {
        this.insightMapper = insightMapper;
    }

    @Override
    public PageResponse<AdminInsightView> page(InsightPageQuery query) {
        Page<MemoryInsightDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(MemoryInsightDO.class);
        if (StringUtils.hasText(query.userId())) {
            wrapper.eq(MemoryInsightDO::getUserId, query.userId());
        }
        if (StringUtils.hasText(query.agentId())) {
            wrapper.eq(MemoryInsightDO::getAgentId, query.agentId());
        }
        if (StringUtils.hasText(query.scope())) {
            wrapper.eq(MemoryInsightDO::getScope, query.scope());
        }
        if (StringUtils.hasText(query.type())) {
            wrapper.eq(MemoryInsightDO::getType, query.type());
        }
        if (StringUtils.hasText(query.tier())) {
            wrapper.eq(MemoryInsightDO::getTier, query.tier());
        }
        wrapper.orderByDesc(
                MemoryInsightDO::getLastReasonedAt,
                MemoryInsightDO::getCreatedAt,
                MemoryInsightDO::getBizId);
        Page<MemoryInsightDO> result = insightMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream().map(MybatisAdminInsightQueryMapper::toView).toList());
    }

    @Override
    public Optional<AdminInsightView> findByBizId(Long insightId) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryInsightDO.class)
                        .eq(MemoryInsightDO::getBizId, insightId);
        return Optional.ofNullable(insightMapper.selectOne(wrapper))
                .map(MybatisAdminInsightQueryMapper::toView);
    }

    @Override
    public List<AdminInsightView> findByBizIds(Collection<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryInsightDO.class)
                        .in(MemoryInsightDO::getBizId, insightIds);
        return insightMapper.selectList(wrapper).stream()
                .map(MybatisAdminInsightQueryMapper::toView)
                .toList();
    }

    private static AdminInsightView toView(MemoryInsightDO dataObject) {
        return new AdminInsightView(
                dataObject.getBizId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getType(),
                dataObject.getScope(),
                dataObject.getName(),
                dataObject.getCategories(),
                dataObject.getContent(),
                dataObject.getPoints(),
                dataObject.getGroupName(),
                dataObject.getLastReasonedAt(),
                dataObject.getSummaryEmbedding(),
                dataObject.getTier(),
                dataObject.getParentInsightId(),
                dataObject.getChildInsightIds(),
                dataObject.getVersion(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }
}
