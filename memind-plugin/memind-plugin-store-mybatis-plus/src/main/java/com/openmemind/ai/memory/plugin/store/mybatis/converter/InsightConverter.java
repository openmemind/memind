package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import java.time.Instant;
import java.util.List;

public final class InsightConverter {

    private InsightConverter() {}

    public static MemoryInsightDO toDO(MemoryId memoryId, MemoryInsight record) {
        MemoryInsightDO dataObject = new MemoryInsightDO();
        dataObject.setBizId(record.id());
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setType(record.type());
        dataObject.setScope(record.scope() != null ? record.scope().name() : null);
        dataObject.setName(record.name());
        dataObject.setCategories(record.categories());
        dataObject.setContent(record.pointsContent());
        dataObject.setPoints(record.points());
        dataObject.setGroupName(record.group());
        dataObject.setConfidence(record.confidence());
        dataObject.setLastReasonedAt(record.lastReasonedAt());
        dataObject.setSummaryEmbedding(record.summaryEmbedding());
        dataObject.setTier(record.tier() != null ? record.tier().name() : null);
        dataObject.setParentInsightId(record.parentInsightId());
        dataObject.setChildInsightIds(record.childInsightIds());
        dataObject.setVersion(record.version());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        return dataObject;
    }

    public static MemoryInsight toRecord(MemoryInsightDO dataObject) {
        var points = dataObject.getPoints();
        if (points == null) {
            points = List.of();
        }
        return new MemoryInsight(
                dataObject.getBizId(),
                dataObject.getMemoryId(),
                dataObject.getType(),
                dataObject.getScope() != null ? MemoryScope.valueOf(dataObject.getScope()) : null,
                dataObject.getName(),
                dataObject.getCategories(),
                points,
                dataObject.getGroupName(),
                dataObject.getConfidence() != null ? dataObject.getConfidence() : 0.0f,
                dataObject.getLastReasonedAt(),
                dataObject.getSummaryEmbedding(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt(),
                dataObject.getTier() != null
                        ? InsightTier.valueOf(dataObject.getTier())
                        : InsightTier.LEAF,
                dataObject.getParentInsightId(),
                dataObject.getChildInsightIds() != null
                        ? dataObject.getChildInsightIds()
                        : List.of(),
                dataObject.getVersion() != null ? dataObject.getVersion() : 1);
    }
}
