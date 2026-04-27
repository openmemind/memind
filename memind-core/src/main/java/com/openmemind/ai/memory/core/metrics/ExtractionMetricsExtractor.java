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
package com.openmemind.ai.memory.core.metrics;

import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.ExtractionStatus;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;

public final class ExtractionMetricsExtractor {

    private ExtractionMetricsExtractor() {}

    public static ExtractionMetrics extract(ExtractionResult result, String source) {
        if (result == null) {
            return new ExtractionMetrics(
                    "unknown", 0, null, 0, null, 0, null, null, null, sourceOrCore(source));
        }
        MemoryItemResult itemResult = result.memoryItemResult();
        ItemGraphMaterializationResult graph =
                itemResult == null ? null : itemResult.graphMaterializationResult();
        ItemGraphMaterializationResult.Stats stats = graph == null ? null : graph.stats();
        return new ExtractionMetrics(
                status(result.status()),
                result.rawDataResult() == null ? 0 : result.rawDataResult().rawDataList().size(),
                result.rawDataResult() == null ? null : result.rawDataResult().segments().size(),
                itemResult == null ? 0 : itemResult.newCount(),
                null,
                result.totalInsights(),
                stats == null ? null : stats.entityCount(),
                stats == null ? null : stats.mentionCount(),
                finalRelationCount(stats),
                sourceOrCore(source));
    }

    private static Integer finalRelationCount(ItemGraphMaterializationResult.Stats stats) {
        if (stats == null || stats.finalRelationStats() == null) {
            return null;
        }
        var finalStats = stats.finalRelationStats();
        return finalStats.semanticRelationCount()
                + finalStats.temporalRelationCount()
                + finalStats.causalRelationCount()
                + finalStats.itemLinkCount();
    }

    private static String status(ExtractionStatus status) {
        return status == null ? "unknown" : status.name().toLowerCase();
    }

    private static String sourceOrCore(String source) {
        return source == null || source.isBlank() ? "core" : source;
    }
}
