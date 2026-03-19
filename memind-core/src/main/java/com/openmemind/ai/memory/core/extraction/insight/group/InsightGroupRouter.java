package com.openmemind.ai.memory.core.extraction.insight.group;

import com.openmemind.ai.memory.core.data.GroupingStrategy;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Insight Group Router
 *
 * <p>According to {@link MemoryCategory#groupingStrategy()} select LLM classification or metadata grouping.
 *
 */
public class InsightGroupRouter {

    private final InsightGroupClassifier llmClassifier;

    public InsightGroupRouter(InsightGroupClassifier llmClassifier) {
        this.llmClassifier = Objects.requireNonNull(llmClassifier);
    }

    public Mono<Map<String, List<MemoryItem>>> group(
            MemoryInsightType insightType,
            MemoryCategory category,
            List<MemoryItem> items,
            List<String> existingGroupNames) {
        return group(insightType, category, items, existingGroupNames, null);
    }

    public Mono<Map<String, List<MemoryItem>>> group(
            MemoryInsightType insightType,
            MemoryCategory category,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {

        return switch (category.groupingStrategy()) {
            case GroupingStrategy.LlmClassify() ->
                    llmClassifier.classify(insightType, items, existingGroupNames, language);
            case GroupingStrategy.MetadataField(var fieldName) ->
                    Mono.just(groupByMetadata(items, fieldName));
        };
    }

    private Map<String, List<MemoryItem>> groupByMetadata(List<MemoryItem> items, String field) {
        return items.stream()
                .collect(
                        Collectors.groupingBy(
                                item -> {
                                    if (item.metadata() == null) return "unknown";
                                    var value = item.metadata().get(field);
                                    return value != null ? String.valueOf(value) : "unknown";
                                }));
    }
}
