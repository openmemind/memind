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
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadMembershipDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadProjectionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMembershipMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadProjectionMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminItemMemoryThreadView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminMemoryThreadQueryMapper {

    PageResponse<AdminMemoryThreadView> page(MemoryThreadPageQuery query);

    Optional<AdminMemoryThreadView> findByThreadKey(String memoryId, String threadKey);

    List<AdminMemoryThreadItemView> findItemsByThreadKey(String memoryId, String threadKey);

    List<AdminItemMemoryThreadView> findThreadsByItemId(String memoryId, Long itemId);
}

@Component
final class MybatisAdminMemoryThreadQueryMapper implements AdminMemoryThreadQueryMapper {

    private final MemoryThreadProjectionMapper threadMapper;
    private final MemoryThreadMembershipMapper membershipMapper;

    MybatisAdminMemoryThreadQueryMapper(
            MemoryThreadProjectionMapper threadMapper,
            MemoryThreadMembershipMapper membershipMapper) {
        this.threadMapper = threadMapper;
        this.membershipMapper = membershipMapper;
    }

    @Override
    public PageResponse<AdminMemoryThreadView> page(MemoryThreadPageQuery query) {
        Page<MemoryThreadProjectionDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(MemoryThreadProjectionDO.class);
        applyMemoryScope(wrapper, query.userId(), query.agentId());
        if (StringUtils.hasText(query.status())) {
            wrapper.eq(
                    MemoryThreadProjectionDO::getLifecycleStatus,
                    query.status().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(
                MemoryThreadProjectionDO::getLastEventAt,
                MemoryThreadProjectionDO::getUpdatedAt,
                MemoryThreadProjectionDO::getId);
        Page<MemoryThreadProjectionDO> result = threadMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminMemoryThreadQueryMapper::toView)
                        .toList());
    }

    @Override
    public Optional<AdminMemoryThreadView> findByThreadKey(String memoryId, String threadKey) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadProjectionDO.class)
                        .eq(MemoryThreadProjectionDO::getMemoryId, memoryId)
                        .eq(MemoryThreadProjectionDO::getThreadKey, threadKey);
        return Optional.ofNullable(threadMapper.selectOne(wrapper))
                .map(MybatisAdminMemoryThreadQueryMapper::toView);
    }

    @Override
    public List<AdminMemoryThreadItemView> findItemsByThreadKey(String memoryId, String threadKey) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadMembershipDO.class)
                        .eq(MemoryThreadMembershipDO::getMemoryId, memoryId)
                        .eq(MemoryThreadMembershipDO::getThreadKey, threadKey)
                        .orderByDesc(MemoryThreadMembershipDO::getPrimary)
                        .orderByDesc(MemoryThreadMembershipDO::getRelevanceWeight)
                        .orderByAsc(
                                MemoryThreadMembershipDO::getItemId,
                                MemoryThreadMembershipDO::getId);
        return membershipMapper.selectList(wrapper).stream().map(item -> toItemView(item)).toList();
    }

    @Override
    public List<AdminItemMemoryThreadView> findThreadsByItemId(String memoryId, Long itemId) {
        var wrapper =
                Wrappers.lambdaQuery(MemoryThreadMembershipDO.class)
                        .eq(MemoryThreadMembershipDO::getMemoryId, memoryId)
                        .eq(MemoryThreadMembershipDO::getItemId, itemId)
                        .orderByDesc(MemoryThreadMembershipDO::getPrimary)
                        .orderByDesc(MemoryThreadMembershipDO::getRelevanceWeight)
                        .orderByAsc(MemoryThreadMembershipDO::getThreadKey);
        List<MemoryThreadMembershipDO> memberships = membershipMapper.selectList(wrapper);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<String, MemoryThreadProjectionDO> projectionsByKey = new HashMap<>();
        for (MemoryThreadProjectionDO projection :
                threadMapper.selectList(
                        Wrappers.lambdaQuery(MemoryThreadProjectionDO.class)
                                .eq(MemoryThreadProjectionDO::getMemoryId, memoryId)
                                .in(
                                        MemoryThreadProjectionDO::getThreadKey,
                                        memberships.stream()
                                                .map(MemoryThreadMembershipDO::getThreadKey)
                                                .distinct()
                                                .toList()))) {
            projectionsByKey.put(projection.getThreadKey(), projection);
        }
        return memberships.stream()
                .map(
                        membership ->
                                toItemThreadView(
                                        membership,
                                        projectionsByKey.get(membership.getThreadKey())))
                .toList();
    }

    private static void applyMemoryScope(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            MemoryThreadProjectionDO>
                    wrapper,
            String userId,
            String agentId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        if (StringUtils.hasText(agentId)) {
            wrapper.eq(
                    MemoryThreadProjectionDO::getMemoryId,
                    DefaultMemoryId.of(userId, agentId).toIdentifier());
            return;
        }
        wrapper.and(
                scoped ->
                        scoped.eq(MemoryThreadProjectionDO::getMemoryId, userId)
                                .or()
                                .likeRight(MemoryThreadProjectionDO::getMemoryId, userId + ":"));
    }

    private static AdminMemoryThreadView toView(MemoryThreadProjectionDO dataObject) {
        MemoryScope scope = MemoryScope.from(dataObject.getMemoryId());
        return new AdminMemoryThreadView(
                scope.userId(),
                scope.agentId(),
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                normalizeLabel(dataObject.getThreadType()),
                normalizeLabel(dataObject.getAnchorKind()),
                dataObject.getAnchorKey(),
                dataObject.getDisplayLabel(),
                normalizeLabel(dataObject.getLifecycleStatus()),
                normalizeLabel(dataObject.getObjectState()),
                dataObject.getHeadline(),
                dataObject.getSnapshotJson() != null ? dataObject.getSnapshotJson() : Map.of(),
                dataObject.getSnapshotVersion() != null ? dataObject.getSnapshotVersion() : 0,
                dataObject.getOpenedAt(),
                dataObject.getLastEventAt(),
                dataObject.getLastMeaningfulUpdateAt(),
                dataObject.getClosedAt(),
                dataObject.getEventCount() != null ? dataObject.getEventCount() : 0L,
                dataObject.getMemberCount() != null ? dataObject.getMemberCount() : 0L,
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static AdminMemoryThreadItemView toItemView(MemoryThreadMembershipDO dataObject) {
        MemoryScope scope = MemoryScope.from(dataObject.getMemoryId());
        return new AdminMemoryThreadItemView(
                scope.userId(),
                scope.agentId(),
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getItemId(),
                normalizeLabel(dataObject.getRole()),
                Boolean.TRUE.equals(dataObject.getPrimary()),
                dataObject.getRelevanceWeight() != null ? dataObject.getRelevanceWeight() : 0.0d,
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static AdminItemMemoryThreadView toItemThreadView(
            MemoryThreadMembershipDO membership, MemoryThreadProjectionDO projection) {
        MemoryScope scope = MemoryScope.from(membership.getMemoryId());
        return new AdminItemMemoryThreadView(
                scope.userId(),
                scope.agentId(),
                membership.getMemoryId(),
                membership.getThreadKey(),
                normalizeLabel(projection != null ? projection.getThreadType() : null),
                normalizeLabel(projection != null ? projection.getAnchorKind() : null),
                projection != null ? projection.getAnchorKey() : null,
                projection != null ? projection.getDisplayLabel() : null,
                normalizeLabel(projection != null ? projection.getLifecycleStatus() : null),
                normalizeLabel(projection != null ? projection.getObjectState() : null),
                projection != null ? projection.getHeadline() : null,
                membership.getItemId(),
                normalizeLabel(membership.getRole()),
                Boolean.TRUE.equals(membership.getPrimary()),
                membership.getRelevanceWeight() != null ? membership.getRelevanceWeight() : 0.0d,
                membership.getCreatedAt(),
                latest(
                        membership.getUpdatedAt(),
                        projection != null ? projection.getUpdatedAt() : null));
    }

    private static Instant latest(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static String normalizeLabel(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private record MemoryScope(String userId, String agentId) {

        private static MemoryScope from(String memoryId) {
            if (!StringUtils.hasText(memoryId)) {
                return new MemoryScope(null, null);
            }
            int separator = memoryId.indexOf(':');
            if (separator < 0) {
                return new MemoryScope(memoryId, null);
            }
            return new MemoryScope(
                    memoryId.substring(0, separator), memoryId.substring(separator + 1));
        }
    }
}
