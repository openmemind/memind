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

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.prompt.extraction.insight.InsightGroupPrompts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Insight semantic grouping classifier based on ChatClient
 *
 */
public class LlmInsightGroupClassifier implements InsightGroupClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightGroupClassifier.class);

    private final ChatClient chatClient;

    public LlmInsightGroupClassifier(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames) {
        return classify(insightType, items, existingGroupNames, null);
    }

    @Override
    public Mono<Map<String, List<MemoryItem>>> classify(
            MemoryInsightType insightType,
            List<MemoryItem> items,
            List<String> existingGroupNames,
            String language) {

        if (items.isEmpty()) {
            return Mono.just(Map.of());
        }

        var promptResult =
                InsightGroupPrompts.build(insightType, items, existingGroupNames, language)
                        .render(language);

        // item id → item mapping
        Map<String, MemoryItem> itemById =
                items.stream()
                        .collect(
                                Collectors.toMap(
                                        item -> String.valueOf(item.id()),
                                        Function.identity(),
                                        (a, b) -> a));

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .system(promptResult.systemPrompt())
                                            .user(promptResult.userPrompt())
                                            .call()
                                            .entity(InsightGroupClassifyResponse.class);

                            if (response == null || response.assignments() == null) {
                                return fallbackSingleGroup(items);
                            }

                            Map<String, List<MemoryItem>> result = new LinkedHashMap<>();
                            for (var assignment : response.assignments()) {
                                var groupItems = new ArrayList<MemoryItem>();
                                for (var itemId : assignment.itemIds()) {
                                    var item = itemById.get(itemId);
                                    if (item != null) {
                                        groupItems.add(item);
                                    }
                                }
                                if (!groupItems.isEmpty()) {
                                    result.computeIfAbsent(
                                                    assignment.groupName(), k -> new ArrayList<>())
                                            .addAll(groupItems);
                                }
                            }

                            // Unassigned items remain ungrouped, waiting for the next round to
                            // regroup
                            var assigned =
                                    result.values().stream()
                                            .flatMap(List::stream)
                                            .map(MemoryItem::id)
                                            .collect(Collectors.toSet());
                            var unassignedCount = items.size() - assigned.size();
                            if (unassignedCount > 0) {
                                log.debug(
                                        "After grouping, {} items were not assigned, remaining"
                                                + " ungrouped [type={}]",
                                        unassignedCount,
                                        insightType.name());
                            }

                            return result;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(10))
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "Insight grouping failed, retrying for the"
                                                                + " {} time [type={}]: {}",
                                                        signal.totalRetries() + 1,
                                                        insightType.name(),
                                                        signal.failure().getMessage())))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Insight grouping ultimately failed, degrading to single group"
                                            + " [type={}]: {}",
                                    insightType.name(),
                                    e.getMessage());
                            return Mono.just(fallbackSingleGroup(items));
                        });
    }

    private Map<String, List<MemoryItem>> fallbackSingleGroup(List<MemoryItem> items) {
        return Map.of("General", new ArrayList<>(items));
    }
}
