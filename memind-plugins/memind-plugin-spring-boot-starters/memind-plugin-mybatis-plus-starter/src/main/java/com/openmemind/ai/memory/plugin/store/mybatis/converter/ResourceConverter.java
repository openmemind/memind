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
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryResourceDO;
import java.time.Instant;

public final class ResourceConverter {

    private ResourceConverter() {}

    public static MemoryResourceDO toDO(MemoryId memoryId, MemoryResource record) {
        MemoryResourceDO dataObject = new MemoryResourceDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setSourceUri(record.sourceUri());
        dataObject.setStorageUri(record.storageUri());
        dataObject.setFileName(record.fileName());
        dataObject.setMimeType(record.mimeType());
        dataObject.setChecksum(record.checksum());
        dataObject.setSizeBytes(record.sizeBytes());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        return dataObject;
    }

    public static MemoryResource toRecord(MemoryResourceDO dataObject) {
        return new MemoryResource(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                dataObject.getSourceUri(),
                dataObject.getStorageUri(),
                dataObject.getFileName(),
                dataObject.getMimeType(),
                dataObject.getChecksum(),
                dataObject.getSizeBytes(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt());
    }
}
