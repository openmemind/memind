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
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemEntityMentionDO;
import java.time.Instant;

public final class ItemEntityMentionConverter {

    private ItemEntityMentionConverter() {}

    public static MemoryItemEntityMentionDO toDO(MemoryId memoryId, ItemEntityMention record) {
        MemoryItemEntityMentionDO dataObject = new MemoryItemEntityMentionDO();
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setItemId(record.itemId());
        dataObject.setEntityKey(record.entityKey());
        dataObject.setConfidence(record.confidence());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        return dataObject;
    }

    public static ItemEntityMention toRecord(MemoryItemEntityMentionDO dataObject) {
        return new ItemEntityMention(
                dataObject.getMemoryId(),
                dataObject.getItemId(),
                dataObject.getEntityKey(),
                dataObject.getConfidence(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt());
    }
}
