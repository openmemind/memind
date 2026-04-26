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
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import java.time.Instant;

public final class ItemConverter {

    private ItemConverter() {}

    public static MemoryItemDO toDO(MemoryId memoryId, MemoryItem record) {
        return toDO(memoryId, null, record);
    }

    public static MemoryItemDO toDO(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, MemoryItem record) {
        MemoryItemDO dataObject = new MemoryItemDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setContent(record.content());
        dataObject.setScope(record.scope() != null ? record.scope().name() : null);
        dataObject.setCategory(record.category() != null ? record.category().name() : null);
        dataObject.setRawDataType(
                record.contentType() != null ? record.contentType() : ConversationContent.TYPE);
        dataObject.setVectorId(record.vectorId());
        dataObject.setRawDataId(record.rawDataId());
        dataObject.setContentHash(record.contentHash());
        dataObject.setOccurredAt(record.occurredAt());
        dataObject.setOccurredStart(record.occurredStart());
        dataObject.setOccurredEnd(record.occurredEnd());
        dataObject.setTimeGranularity(record.timeGranularity());
        dataObject.setObservedAt(record.observedAt());
        applyDerivedTemporalLookupColumns(dataObject, record);
        dataObject.setMetadata(record.metadata());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        dataObject.setType(
                record.type() != null ? record.type().name() : MemoryItemType.FACT.name());
        dataObject.setExtractionBatchId(
                extractionBatchId != null ? extractionBatchId.value() : null);
        return dataObject;
    }

    static void applyDerivedTemporalLookupColumns(MemoryItemDO dataObject, MemoryItem record) {
        Instant temporalStart =
                firstNonNull(record.occurredStart(), record.occurredAt(), record.observedAt());
        Instant temporalAnchor = temporalStart;
        Instant temporalEndOrAnchor =
                firstNonNull(
                        record.occurredEnd(),
                        record.occurredStart(),
                        record.occurredAt(),
                        record.observedAt());
        dataObject.setTemporalStart(temporalStart);
        dataObject.setTemporalEndOrAnchor(temporalEndOrAnchor);
        dataObject.setTemporalAnchor(temporalAnchor);
    }

    public static MemoryItem toRecord(MemoryItemDO dataObject) {
        Instant occurredAt = dataObject.getOccurredAt();
        Instant occurredStart = dataObject.getOccurredStart();
        if (occurredStart == null) {
            occurredStart = occurredAt;
        }
        String timeGranularity = dataObject.getTimeGranularity();
        if (occurredStart != null && (timeGranularity == null || timeGranularity.isBlank())) {
            timeGranularity = "unknown";
        }
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
                occurredAt,
                occurredStart,
                dataObject.getOccurredEnd(),
                timeGranularity,
                dataObject.getObservedAt(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getType() != null
                        ? MemoryItemType.valueOf(dataObject.getType())
                        : MemoryItemType.FACT);
    }

    private static String parseContentType(String value) {
        if (value == null || value.isBlank()) {
            return ConversationContent.TYPE;
        }
        return value;
    }

    private static MemoryCategory parseMemoryCategory(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MemoryCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return MemoryCategory.byName(value).orElse(null);
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
