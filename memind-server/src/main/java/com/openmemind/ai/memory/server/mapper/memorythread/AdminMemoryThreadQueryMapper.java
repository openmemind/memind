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
package com.openmemind.ai.memory.server.mapper.memorythread;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminMemoryThreadQueryMapper {

    PageResponse<AdminMemoryThreadView> page(MemoryThreadPageQuery query);

    Optional<AdminMemoryThreadView> findByBizId(Long threadId);

    List<AdminMemoryThreadItemView> findItemsByThreadId(Long threadId);

    Optional<AdminMemoryThreadItemView> findByItemId(Long itemId);

    List<String> listDistinctMemoryIds();
}

@Component
final class MybatisAdminMemoryThreadQueryMapper implements AdminMemoryThreadQueryMapper {

    private final MemoryThreadMapper threadMapper;
    private final MemoryThreadItemMapper threadItemMapper;

    MybatisAdminMemoryThreadQueryMapper(
            MemoryThreadMapper threadMapper, MemoryThreadItemMapper threadItemMapper) {
        this.threadMapper = threadMapper;
        this.threadItemMapper = threadItemMapper;
    }

    @Override
    public PageResponse<AdminMemoryThreadView> page(MemoryThreadPageQuery query) {
        Page<MemoryThreadDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(MemoryThreadDO.class);
        if (StringUtils.hasText(query.userId())) {
            wrapper.eq(MemoryThreadDO::getUserId, query.userId());
        }
        if (StringUtils.hasText(query.agentId())) {
            wrapper.eq(MemoryThreadDO::getAgentId, query.agentId());
        }
        if (StringUtils.hasText(query.status())) {
            wrapper.eq(MemoryThreadDO::getStatus, query.status().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(
                MemoryThreadDO::getLastActivityAt,
                MemoryThreadDO::getUpdatedAt,
                MemoryThreadDO::getBizId);
        Page<MemoryThreadDO> result = threadMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminMemoryThreadQueryMapper::toView)
                        .toList());
    }

    @Override
    public Optional<AdminMemoryThreadView> findByBizId(Long threadId) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadDO.class).eq(MemoryThreadDO::getBizId, threadId);
        return Optional.ofNullable(threadMapper.selectOne(wrapper))
                .map(MybatisAdminMemoryThreadQueryMapper::toView);
    }

    @Override
    public List<AdminMemoryThreadItemView> findItemsByThreadId(Long threadId) {
        MemoryThreadDO thread = threadByBizId(threadId);
        if (thread == null) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadItemDO.class)
                        .eq(MemoryThreadItemDO::getThreadId, threadId)
                        .orderByAsc(
                                MemoryThreadItemDO::getSequenceHint, MemoryThreadItemDO::getBizId);
        return threadItemMapper.selectList(wrapper).stream()
                .map(item -> toItemView(item, thread.getThreadKey()))
                .toList();
    }

    @Override
    public Optional<AdminMemoryThreadItemView> findByItemId(Long itemId) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadItemDO.class)
                        .eq(MemoryThreadItemDO::getItemId, itemId);
        MemoryThreadItemDO item = threadItemMapper.selectOne(wrapper);
        if (item == null) {
            return Optional.empty();
        }
        MemoryThreadDO thread = threadByBizId(item.getThreadId());
        String threadKey = thread != null ? thread.getThreadKey() : null;
        return Optional.of(toItemView(item, threadKey));
    }

    @Override
    public List<String> listDistinctMemoryIds() {
        var wrapper = Wrappers.query(MemoryThreadDO.class).select("DISTINCT memory_id");
        return threadMapper.selectObjs(wrapper).stream().map(String::valueOf).toList();
    }

    private MemoryThreadDO threadByBizId(Long threadId) {
        if (threadId == null) {
            return null;
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadDO.class).eq(MemoryThreadDO::getBizId, threadId);
        return threadMapper.selectOne(wrapper);
    }

    private static AdminMemoryThreadView toView(MemoryThreadDO dataObject) {
        return new AdminMemoryThreadView(
                dataObject.getBizId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getEpisodeType(),
                dataObject.getTitle(),
                dataObject.getSummarySnapshot(),
                normalizeLabel(dataObject.getStatus()),
                dataObject.getConfidence() != null ? dataObject.getConfidence() : 0.0d,
                dataObject.getStartAt(),
                dataObject.getEndAt(),
                dataObject.getLastActivityAt(),
                dataObject.getOriginItemId(),
                dataObject.getAnchorItemId(),
                dataObject.getDisplayOrderHint() != null ? dataObject.getDisplayOrderHint() : 0,
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static AdminMemoryThreadItemView toItemView(
            MemoryThreadItemDO dataObject, String threadKey) {
        return new AdminMemoryThreadItemView(
                dataObject.getBizId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getThreadId(),
                threadKey,
                dataObject.getItemId(),
                normalizeLabel(dataObject.getRole()),
                dataObject.getMembershipWeight() != null ? dataObject.getMembershipWeight() : 0.0d,
                dataObject.getSequenceHint() != null ? dataObject.getSequenceHint() : 0,
                dataObject.getJoinedAt(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static String normalizeLabel(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
