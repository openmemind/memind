package com.openmemind.ai.memory.core.extraction.insight.group;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Insight Semantic Group Classifier
 *
 * <p>Divides items into subgroups by semantics (e.g., profile → basic information/professional background/family relationships),
 * each InsightType has only 1 LLM call.
 *
 */
public interface InsightGroupClassifier {

    /**
     * Groups items
     *
     * @param insightType        Insight type
     * @param items              Items to be grouped
     * @param existingGroupNames Existing group names (preferentially assigned to existing groups)
     * @return groupName → items mapping
     */
    Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType, List<MemoryItem> items, List<String> existingGroupNames);

    default Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {
        return classify(insightType, items, existingGroupNames);
    }
}
