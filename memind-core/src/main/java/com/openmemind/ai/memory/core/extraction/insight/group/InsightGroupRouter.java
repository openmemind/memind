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

    public Mono<Map<String, List<MemoryItem>>> groupSemantic(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String additionalContext,
            String language) {
        return llmClassifier.classify(
                insightType, items, existingGroupNames, additionalContext, language);
    }

    public Mono<Map<String, List<MemoryItem>>> group(
            MemoryInsightType insightType,
            MemoryCategory category,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String additionalContext,
            String language) {
        return switch (category.groupingStrategy()) {
            case GroupingStrategy.LlmClassify() ->
                    groupSemantic(
                            insightType, items, existingGroupNames, additionalContext, language);
            case GroupingStrategy.MetadataField(var fieldName) ->
                    Mono.just(groupByMetadata(items, fieldName));
        };
    }

    private Map<String, List<MemoryItem>> groupByMetadata(List<MemoryItem> items, String field) {
        return items.stream()
                .collect(
                        Collectors.groupingBy(
                                item -> {
                                    if (item.metadata() == null) {
                                        return "unknown";
                                    }
                                    var value = item.metadata().get(field);
                                    return value != null ? String.valueOf(value) : "unknown";
                                }));
    }
}
