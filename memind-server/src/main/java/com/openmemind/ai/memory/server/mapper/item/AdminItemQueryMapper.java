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
package com.openmemind.ai.memory.server.mapper.item;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminItemQueryMapper {

    PageResponse<AdminItemView> page(ItemPageQuery query);

    Optional<AdminItemView> findByBizId(Long itemId);

    List<AdminItemView> findByBizIds(Collection<Long> itemIds);

    List<AdminItemView> findByRawDataIds(Collection<String> rawDataIds);
}

@Component
final class MybatisAdminItemQueryMapper implements AdminItemQueryMapper {

    private final MemoryItemMapper itemMapper;

    MybatisAdminItemQueryMapper(MemoryItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    public PageResponse<AdminItemView> page(ItemPageQuery query) {
        Page<MemoryItemDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(MemoryItemDO.class);
        if (StringUtils.hasText(query.userId())) {
            wrapper.eq(MemoryItemDO::getUserId, query.userId());
        }
        if (StringUtils.hasText(query.agentId())) {
            wrapper.eq(MemoryItemDO::getAgentId, query.agentId());
        }
        if (StringUtils.hasText(query.scope())) {
            wrapper.eq(MemoryItemDO::getScope, query.scope());
        }
        if (StringUtils.hasText(query.category())) {
            wrapper.eq(MemoryItemDO::getCategory, query.category());
        }
        if (StringUtils.hasText(query.type())) {
            wrapper.eq(MemoryItemDO::getType, query.type());
        }
        if (StringUtils.hasText(query.rawDataId())) {
            wrapper.eq(MemoryItemDO::getRawDataId, query.rawDataId());
        }
        wrapper.orderByDesc(
                MemoryItemDO::getObservedAt, MemoryItemDO::getCreatedAt, MemoryItemDO::getBizId);
        Page<MemoryItemDO> result = itemMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream().map(MybatisAdminItemQueryMapper::toView).toList());
    }

    @Override
    public Optional<AdminItemView> findByBizId(Long itemId) {
        var wrapper = Wrappers.lambdaQuery(MemoryItemDO.class).eq(MemoryItemDO::getBizId, itemId);
        return Optional.ofNullable(itemMapper.selectOne(wrapper))
                .map(MybatisAdminItemQueryMapper::toView);
    }

    @Override
    public List<AdminItemView> findByBizIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        var wrapper = Wrappers.lambdaQuery(MemoryItemDO.class).in(MemoryItemDO::getBizId, itemIds);
        return itemMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemQueryMapper::toView)
                .toList();
    }

    @Override
    public List<AdminItemView> findByRawDataIds(Collection<String> rawDataIds) {
        if (rawDataIds == null || rawDataIds.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryItemDO.class).in(MemoryItemDO::getRawDataId, rawDataIds);
        return itemMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemQueryMapper::toView)
                .toList();
    }

    private static AdminItemView toView(MemoryItemDO dataObject) {
        return new AdminItemView(
                dataObject.getBizId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getContent(),
                dataObject.getScope(),
                dataObject.getCategory(),
                dataObject.getVectorId(),
                dataObject.getRawDataId(),
                dataObject.getContentHash(),
                dataObject.getOccurredAt(),
                dataObject.getObservedAt(),
                dataObject.getMetadata(),
                dataObject.getType(),
                dataObject.getRawDataType(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }
}
