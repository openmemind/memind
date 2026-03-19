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
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightTypeDO;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class InsightTypeConverter {

    private InsightTypeConverter() {}

    public static MemoryInsightTypeDO toDO(MemoryId memoryId, MemoryInsightType record) {
        MemoryInsightTypeDO dataObject = new MemoryInsightTypeDO();
        dataObject.setBizId(record.id());
        dataObject.setName(record.name());
        dataObject.setDescription(record.description());
        dataObject.setDescriptionVectorId(record.descriptionVectorId());
        dataObject.setTargetTokens(record.targetTokens());
        dataObject.setCategories(record.categories());
        dataObject.setCreatedAt(record.createdAt() != null ? record.createdAt() : Instant.now());
        dataObject.setUpdatedAt(Instant.now());
        if (record.summaryPrompt() != null) {
            dataObject.setSummaryPrompt(new HashMap<>(record.summaryPrompt()));
        }
        dataObject.setLastUpdatedAt(record.lastUpdatedAt());
        dataObject.setAnalysisMode(
                record.insightAnalysisMode() != null ? record.insightAnalysisMode().name() : null);
        dataObject.setTreeConfig(record.treeConfig());
        dataObject.setScope(record.scope() != null ? record.scope().name() : null);
        return dataObject;
    }

    public static MemoryInsightType toRecord(MemoryInsightTypeDO dataObject) {
        Map<String, String> summaryPrompt = null;
        if (dataObject.getSummaryPrompt() != null) {
            summaryPrompt = mapToSummaryPrompt(dataObject.getSummaryPrompt());
        }
        return new MemoryInsightType(
                dataObject.getBizId(),
                null,
                dataObject.getName(),
                dataObject.getDescription(),
                dataObject.getDescriptionVectorId(),
                dataObject.getCategories(),
                dataObject.getTargetTokens() != null ? dataObject.getTargetTokens() : 0,
                summaryPrompt,
                dataObject.getLastUpdatedAt(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt(),
                dataObject.getAnalysisMode() != null
                        ? InsightAnalysisMode.valueOf(dataObject.getAnalysisMode())
                        : InsightAnalysisMode.BRANCH,
                dataObject.getTreeConfig(),
                dataObject.getScope() != null ? MemoryScope.valueOf(dataObject.getScope()) : null,
                null);
    }

    private static Map<String, String> mapToSummaryPrompt(Map<String, Object> map) {
        Map<String, String> result = new HashMap<>();
        map.forEach(
                (key, value) -> {
                    if (value instanceof String s) {
                        result.put(key, s);
                    } else if (value instanceof Map<?, ?> sectionMap) {
                        // backward-compatible: old format stored as {name, ordinal, content, raw}
                        var content = sectionMap.get("content");
                        if (content instanceof String s) {
                            result.put(key, s);
                        }
                    }
                });
        return result;
    }
}
