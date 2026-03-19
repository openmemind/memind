package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import java.time.Instant;

public final class ItemConverter {

    private ItemConverter() {}

    public static MemoryItemDO toDO(MemoryId memoryId, MemoryItem record) {
        MemoryItemDO dataObject = new MemoryItemDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setContent(record.content());
        dataObject.setScope(record.scope() != null ? record.scope().name() : null);
        dataObject.setCategory(record.category() != null ? record.category().name() : null);
        dataObject.setRawDataType(
                record.contentType() != null ? record.contentType() : ContentTypes.CONVERSATION);
        dataObject.setVectorId(record.vectorId());
        dataObject.setRawDataId(record.rawDataId());
        dataObject.setContentHash(record.contentHash());
        dataObject.setOccurredAt(record.occurredAt());
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        dataObject.setType(
                record.type() != null ? record.type().name() : MemoryItemType.FACT.name());
        return dataObject;
    }

    public static MemoryItem toRecord(MemoryItemDO dataObject) {
        return new MemoryItem(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                dataObject.getContent(),
                dataObject.getScope() != null ? MemoryScope.valueOf(dataObject.getScope()) : null,
                parseMemoryCategory(dataObject.getCategory()),
                parseContentType(dataObject.getRawDataType()),
                dataObject.getVectorId(),
                dataObject.getRawDataId(),
                dataObject.getContentHash(),
                dataObject.getOccurredAt(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getType() != null
                        ? MemoryItemType.valueOf(dataObject.getType())
                        : MemoryItemType.FACT);
    }

    private static String parseContentType(String value) {
        if (value == null || value.isBlank()) {
            return ContentTypes.CONVERSATION;
        }
        return value;
    }

    private static MemoryCategory parseMemoryCategory(String value) {
        if (value == null) return null;
        try {
            return MemoryCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return MemoryCategory.byName(value).orElse(null);
        }
    }
}
