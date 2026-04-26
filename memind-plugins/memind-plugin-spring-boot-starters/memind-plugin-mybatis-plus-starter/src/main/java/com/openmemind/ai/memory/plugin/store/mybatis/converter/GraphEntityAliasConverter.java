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
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityAliasDO;
import java.time.Instant;

public final class GraphEntityAliasConverter {

    private GraphEntityAliasConverter() {}

    public static MemoryGraphEntityAliasDO toDO(MemoryId memoryId, GraphEntityAlias alias) {
        MemoryGraphEntityAliasDO dataObject = new MemoryGraphEntityAliasDO();
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setEntityKey(alias.entityKey());
        dataObject.setEntityType(alias.entityType().name());
        dataObject.setNormalizedAlias(alias.normalizedAlias());
        dataObject.setEvidenceCount(alias.evidenceCount());
        dataObject.setMetadata(alias.metadata());
        dataObject.setCreatedAt(alias.createdAt() != null ? alias.createdAt() : Instant.now());
        dataObject.setUpdatedAt(alias.updatedAt() != null ? alias.updatedAt() : Instant.now());
        return dataObject;
    }

    public static GraphEntityAlias toRecord(MemoryGraphEntityAliasDO dataObject) {
        return new GraphEntityAlias(
                dataObject.getMemoryId(),
                dataObject.getEntityKey(),
                dataObject.getEntityType() != null
                        ? GraphEntityType.valueOf(dataObject.getEntityType())
                        : GraphEntityType.OTHER,
                dataObject.getNormalizedAlias(),
                dataObject.getEvidenceCount(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }
}
