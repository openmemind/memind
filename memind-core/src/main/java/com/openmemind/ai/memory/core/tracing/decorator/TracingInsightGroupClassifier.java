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
}
