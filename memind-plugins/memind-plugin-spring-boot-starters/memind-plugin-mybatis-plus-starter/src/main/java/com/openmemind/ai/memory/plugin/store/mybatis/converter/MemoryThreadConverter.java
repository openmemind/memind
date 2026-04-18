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
package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadItemDO;
import java.time.Instant;

public final class MemoryThreadConverter {

    private MemoryThreadConverter() {}

    public static MemoryThreadDO toDO(MemoryId memoryId, MemoryThread record) {
        MemoryThreadDO dataObject = new MemoryThreadDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setThreadKey(record.threadKey());
        dataObject.setEpisodeType(record.episodeType());
        dataObject.setTitle(record.title());
        dataObject.setSummarySnapshot(record.summarySnapshot());
        dataObject.setStatus(record.status() != null ? record.status().name() : null);
        dataObject.setConfidence(record.confidence());
        dataObject.setStartAt(record.startAt());
        dataObject.setEndAt(record.endAt());
        dataObject.setLastActivityAt(record.lastActivityAt());
        dataObject.setOriginItemId(record.originItemId());
        dataObject.setAnchorItemId(record.anchorItemId());
        dataObject.setDisplayOrderHint(record.displayOrderHint());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(record.updatedAt() != null ? record.updatedAt() : Instant.now());
        dataObject.setDeleted(record.deleted());
        return dataObject;
    }

    public static MemoryThread toRecord(MemoryThreadDO dataObject) {
        return new MemoryThread(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getEpisodeType(),
                dataObject.getTitle(),
                dataObject.getSummarySnapshot(),
                dataObject.getStatus() != null
                        ? MemoryThreadStatus.valueOf(dataObject.getStatus())
                        : MemoryThreadStatus.OPEN,
                dataObject.getConfidence() != null ? dataObject.getConfidence() : 0.0d,
                dataObject.getStartAt(),
                dataObject.getEndAt(),
                dataObject.getLastActivityAt(),
                dataObject.getOriginItemId(),
                dataObject.getAnchorItemId(),
                dataObject.getDisplayOrderHint() != null ? dataObject.getDisplayOrderHint() : 0,
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt(),
                Boolean.TRUE.equals(dataObject.getDeleted()));
    }

    public static MemoryThreadItemDO toDO(MemoryId memoryId, MemoryThreadItem record) {
        MemoryThreadItemDO dataObject = new MemoryThreadItemDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setThreadId(record.threadId());
        dataObject.setItemId(record.itemId());
        dataObject.setMembershipWeight(record.membershipWeight());
        dataObject.setRole(record.role() != null ? record.role().name() : null);
        dataObject.setSequenceHint(record.sequenceHint());
        dataObject.setJoinedAt(record.joinedAt());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(record.updatedAt() != null ? record.updatedAt() : Instant.now());
        dataObject.setDeleted(record.deleted());
        return dataObject;
    }

    public static MemoryThreadItem toRecord(MemoryThreadItemDO dataObject) {
        return new MemoryThreadItem(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                dataObject.getThreadId(),
                dataObject.getItemId(),
                dataObject.getMembershipWeight() != null ? dataObject.getMembershipWeight() : 0.0d,
                dataObject.getRole() != null
                        ? MemoryThreadRole.valueOf(dataObject.getRole())
                        : MemoryThreadRole.CORE,
                dataObject.getSequenceHint() != null ? dataObject.getSequenceHint() : 1,
                dataObject.getJoinedAt(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt(),
                Boolean.TRUE.equals(dataObject.getDeleted()));
    }
}
