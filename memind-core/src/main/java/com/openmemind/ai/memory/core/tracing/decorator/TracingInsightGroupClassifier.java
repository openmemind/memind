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
package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link InsightGroupClassifier}.
 *
 * <p>Records the insight type and item count as request attributes, and the group count as a result
 * attribute.
 */
public class TracingInsightGroupClassifier extends TracingSupport
        implements InsightGroupClassifier {

    private final InsightGroupClassifier delegate;

    public TracingInsightGroupClassifier(InsightGroupClassifier delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY,
                Map.of(
                        MemoryAttributes.EXTRACTION_INSIGHT_TYPE,
                        insightType.name(),
                        MemoryAttributes.EXTRACTION_ITEM_COUNT,
                        items.size()),
                r -> Map.of(MemoryAttributes.EXTRACTION_INSIGHT_GROUP_COUNT, r.size()),
                () -> delegate.classify(insightType, items, existingGroupNames));
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String additionalContext,
            String language) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY,
                Map.of(
                        MemoryAttributes.EXTRACTION_INSIGHT_TYPE,
                        insightType.name(),
                        MemoryAttributes.EXTRACTION_ITEM_COUNT,
                        items.size()),
                r -> Map.of(MemoryAttributes.EXTRACTION_INSIGHT_GROUP_COUNT, r.size()),
                () ->
                        delegate.classify(
                                insightType,
                                items,
                                existingGroupNames,
                                additionalContext,
                                language));
    }
}
