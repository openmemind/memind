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
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemLinkDO;
import java.time.Instant;

public final class ItemLinkConverter {

    private ItemLinkConverter() {}

    public static MemoryItemLinkDO toDO(MemoryId memoryId, ItemLink record) {
        MemoryItemLinkDO dataObject = new MemoryItemLinkDO();
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setSourceItemId(record.sourceItemId());
        dataObject.setTargetItemId(record.targetItemId());
        dataObject.setLinkType(record.linkType().name());
        dataObject.setRelationCode(record.relationCode());
        dataObject.setEvidenceSource(record.evidenceSource());
        dataObject.setStrength(record.strength());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(dataObject.getCreatedAt());
        return dataObject;
    }

    public static ItemLink toRecord(MemoryItemLinkDO dataObject) {
        return new ItemLink(
                dataObject.getMemoryId(),
                dataObject.getSourceItemId(),
                dataObject.getTargetItemId(),
                ItemLinkType.valueOf(dataObject.getLinkType()),
                dataObject.getRelationCode(),
                dataObject.getEvidenceSource(),
                dataObject.getStrength(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt());
    }
}
