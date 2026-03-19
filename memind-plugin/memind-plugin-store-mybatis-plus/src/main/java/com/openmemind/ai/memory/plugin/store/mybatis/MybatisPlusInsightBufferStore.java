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
package com.openmemind.ai.memory.plugin.store.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.insight.buffer.BufferEntry;
import com.openmemind.ai.memory.core.extraction.insight.buffer.InsightBufferStore;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.InsightBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.InsightBufferMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Insight buffer persistence implementation based on MyBatis Plus
 *
 */
public class MybatisPlusInsightBufferStore implements InsightBufferStore {

    private final InsightBufferMapper mapper;

    public MybatisPlusInsightBufferStore(InsightBufferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        for (var itemId : itemIds) {
            var existing = selectEntry(memoryId, insightTypeName, itemId);
            if (existing != null) {
                continue;
            }

            var record = new InsightBufferDO();
            record.setUserId(memoryId.getAttribute("userId"));
            record.setAgentId(memoryId.getAttribute("agentId"));
            record.setMemoryId(memoryId.toIdentifier());
            record.setInsightTypeName(insightTypeName);
            record.setItemId(itemId);
            record.setBuilt(false);
            mapper.insert(record);
        }
    }

    @Override
    public List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName) {
        return mapper
                .selectList(
                        baseQuery(memoryId, insightTypeName)
                                .isNull("group_name")
                                .eq("built", false))
                .stream()
                .map(this::toBufferEntry)
                .toList();
    }

    @Override
    public int countUnGrouped(MemoryId memoryId, String insightTypeName) {
        return Math.toIntExact(
                mapper.selectCount(
                        baseQuery(memoryId, insightTypeName)
                                .isNull("group_name")
                                .eq("built", false)));
    }

    @Override
    public List<BufferEntry> getGroupUnbuilt(
            MemoryId memoryId, String insightTypeName, String groupName) {
        return mapper
                .selectList(
                        baseQuery(memoryId, insightTypeName)
                                .eq("group_name", groupName)
                                .eq("built", false))
                .stream()
                .map(this::toBufferEntry)
                .toList();
    }

    @Override
    public int countGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName) {
        return Math.toIntExact(
                mapper.selectCount(
                        baseQuery(memoryId, insightTypeName)
                                .eq("group_name", groupName)
                                .eq("built", false)));
    }

    @Override
    public void assignGroup(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String groupName) {
        if (itemIds.isEmpty()) {
            return;
        }
        mapper.update(
                new UpdateWrapper<InsightBufferDO>()
                        .eq("user_id", memoryId.getAttribute("userId"))
                        .eq("agent_id", memoryId.getAttribute("agentId"))
                        .eq("insight_type_name", insightTypeName)
                        .in("item_id", itemIds)
                        .set("group_name", groupName));
    }

    @Override
    public void markBuilt(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        mapper.update(
                new UpdateWrapper<InsightBufferDO>()
                        .eq("user_id", memoryId.getAttribute("userId"))
                        .eq("agent_id", memoryId.getAttribute("agentId"))
                        .eq("insight_type_name", insightTypeName)
                        .in("item_id", itemIds)
                        .set("built", true));
    }

    @Override
    public Map<String, List<BufferEntry>> getUnbuiltByGroup(
            MemoryId memoryId, String insightTypeName) {
        return mapper
                .selectList(
                        baseQuery(memoryId, insightTypeName)
                                .isNotNull("group_name")
                                .eq("built", false))
                .stream()
                .map(this::toBufferEntry)
                .collect(Collectors.groupingBy(BufferEntry::groupName));
    }

    @Override
    public Set<String> listGroups(MemoryId memoryId, String insightTypeName) {
        return mapper
                .selectList(baseQuery(memoryId, insightTypeName).isNotNull("group_name"))
                .stream()
                .map(InsightBufferDO::getGroupName)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasWork(MemoryId memoryId, String insightTypeName) {
        return mapper.selectCount(baseQuery(memoryId, insightTypeName).eq("built", false)) > 0;
    }

    // ===== Helpers =====

    private QueryWrapper<InsightBufferDO> baseQuery(MemoryId memoryId, String insightTypeName) {
        return new QueryWrapper<InsightBufferDO>()
                .eq("user_id", memoryId.getAttribute("userId"))
                .eq("agent_id", memoryId.getAttribute("agentId"))
                .eq("insight_type_name", insightTypeName);
    }

    private InsightBufferDO selectEntry(MemoryId memoryId, String insightTypeName, Long itemId) {
        return mapper.selectOne(baseQuery(memoryId, insightTypeName).eq("item_id", itemId));
    }

    private BufferEntry toBufferEntry(InsightBufferDO record) {
        return new BufferEntry(
                record.getItemId(), record.getGroupName(), Boolean.TRUE.equals(record.getBuilt()));
    }
}
