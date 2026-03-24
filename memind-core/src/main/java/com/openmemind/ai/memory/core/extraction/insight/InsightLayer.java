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
package com.openmemind.ai.memory.core.extraction.insight;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Insight layer entry (thin distribution layer)
 *
 * <p>Parse InsightType, group items by type, and then delegate to {@link InsightBuildScheduler} for asynchronous construction.
 *
 */
public class InsightLayer implements InsightExtractStep {

    private static final Logger log = LoggerFactory.getLogger(InsightLayer.class);

    private final MemoryStore memoryStore;
    private final InsightBuildScheduler scheduler;
    private final Set<String> unsupportedContentTypes;

    public InsightLayer(MemoryStore memoryStore, InsightBuildScheduler scheduler) {
        this(memoryStore, scheduler, Set.of());
    }

    /**
     * @param memoryStore             memory store
     * @param scheduler               insight build scheduler
     * @param unsupportedContentTypes content types that should be excluded from insight building
     *                                (derived from processors where supportsInsight() returns false)
     */
    public InsightLayer(
            MemoryStore memoryStore,
            InsightBuildScheduler scheduler,
            Set<String> unsupportedContentTypes) {
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.unsupportedContentTypes =
                unsupportedContentTypes != null ? Set.copyOf(unsupportedContentTypes) : Set.of();
    }

    @Override
    public Mono<InsightResult> extract(MemoryId memoryId, MemoryItemResult memoryItemResult) {
        return extract(memoryId, memoryItemResult, null);
    }

    @Override
    public Mono<InsightResult> extract(
            MemoryId memoryId, MemoryItemResult memoryItemResult, String language) {
        // Only take FACT type items whose content type supports insight building
        var factItems =
                memoryItemResult.newItems().stream()
                        .filter(item -> item.type() == MemoryItemType.FACT)
                        .filter(
                                item ->
                                        item.contentType() == null
                                                || !unsupportedContentTypes.contains(
                                                        item.contentType()))
                        .toList();

        if (factItems.isEmpty()) {
            return Mono.just(InsightResult.empty());
        }

        var insightTypes = resolveInsightTypes(memoryId, memoryItemResult);
        var branchTypes =
                insightTypes.stream()
                        .filter(t -> t.insightAnalysisMode() != InsightAnalysisMode.ROOT)
                        .toList();
        var grouped = groupItemsByInsightType(branchTypes, factItems);

        for (var entry : grouped.entrySet()) {
            var itemIds = entry.getValue().stream().map(MemoryItem::id).toList();
            scheduler.submit(memoryId, entry.getKey().name(), itemIds, language);
        }

        if (!grouped.isEmpty()) {
            var summary =
                    grouped.entrySet().stream()
                            .map(e -> e.getKey().name() + "=" + e.getValue().size())
                            .collect(Collectors.joining(", "));
            log.info(
                    "Insight routing completed [memoryId={}, types: {}]",
                    memoryId.toIdentifier(),
                    summary);
        }

        return Mono.just(InsightResult.empty());
    }

    /**
     * Force refresh all remaining buffer entries of InsightType
     *
     * <p>First wait for all submitted asynchronous pipelines to complete, then synchronously execute flush (bypassing threshold guard).
     *
     * <p>Timing of invocation: end of extraction, end of session, application shutdown, or user-triggered.
     *
     * <p><b>Thread-safety:</b> This method is NOT safe to call concurrently with
     * {@link #extract} for the same memoryId. Callers must ensure that all
     * extract calls for the target memoryId have returned before calling flush.
     */
    public void flush(MemoryId memoryId) {
        flush(memoryId, null);
    }

    public void flush(MemoryId memoryId, String language) {
        scheduler.awaitPending(memoryId, 5, TimeUnit.MINUTES);

        var insightTypes = memoryStore.insightOperations().listInsightTypes();
        if (insightTypes.isEmpty()) {
            insightTypes = DefaultInsightTypes.all();
        }

        var branchTypes =
                insightTypes.stream()
                        .filter(t -> t.insightAnalysisMode() != InsightAnalysisMode.ROOT)
                        .toList();

        var flushExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var futures =
                branchTypes.stream()
                        .map(
                                type ->
                                        CompletableFuture.runAsync(
                                                () ->
                                                        scheduler.flushSync(
                                                                memoryId, type.name(), language),
                                                flushExecutor))
                        .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(futures).join();
        } catch (Exception e) {
            log.warn(
                    "flush partially failed [memoryId={}]: {}",
                    memoryId.toIdentifier(),
                    e.getMessage());
        }

        // Compensate: force-summarize any BRANCH that has LEAFs but no summary yet.
        // Uses the MemoryInsightType objects already in hand to avoid a store lookup.
        for (var type : branchTypes) {
            scheduler.forceResummarizeBranchIfEmpty(memoryId, type, language);
        }

        scheduler.drainRootTasks(memoryId, 5, TimeUnit.MINUTES);
    }

    // ===== InsightType parsing =====

    private List<MemoryInsightType> resolveInsightTypes(
            MemoryId memoryId, MemoryItemResult result) {
        if (!result.resolvedInsightTypes().isEmpty()) {
            return result.resolvedInsightTypes();
        }
        var stored = memoryStore.insightOperations().listInsightTypes();
        return stored.isEmpty() ? DefaultInsightTypes.all() : stored;
    }

    // ===== Group by InsightType =====

    /**
     * Group items by InsightType
     *
     * <p>Use item.metadata["insightTypes"] for explicit routing.
     */
    private Map<MemoryInsightType, List<MemoryItem>> groupItemsByInsightType(
            List<MemoryInsightType> insightTypes, List<MemoryItem> newItems) {

        Map<String, MemoryInsightType> typeByName =
                insightTypes.stream()
                        .collect(
                                Collectors.toMap(
                                        MemoryInsightType::name,
                                        t -> t,
                                        (a, b) -> a,
                                        LinkedHashMap::new));

        var grouped = new LinkedHashMap<MemoryInsightType, List<MemoryItem>>();
        for (var item : newItems) {
            for (String insightTypeName : resolveExplicitInsightTypes(item, insightTypes)) {
                var insightType = typeByName.get(insightTypeName);
                if (insightType == null) {
                    continue;
                }
                if (insightType.acceptContentTypes() != null
                        && !insightType.acceptContentTypes().contains(item.contentType())) {
                    continue;
                }
                grouped.computeIfAbsent(insightType, ignored -> new ArrayList<>()).add(item);
            }
        }
        return grouped;
    }

    private List<String> resolveExplicitInsightTypes(
            MemoryItem item, List<MemoryInsightType> insightTypes) {
        List<String> rawTypes;
        if (item.metadata() == null) {
            rawTypes = List.of();
        } else {
            Object value = item.metadata().get("insightTypes");
            if (!(value instanceof List<?> listValue)) {
                rawTypes = List.of();
            } else {
                rawTypes =
                        listValue.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .map(String::strip)
                                .filter(name -> !name.isBlank())
                                .toList();
            }
        }
        return validateInsightTypes(item, rawTypes, insightTypes);
    }

    private List<String> validateInsightTypes(
            MemoryItem item, List<String> llmOutput, List<MemoryInsightType> insightTypes) {
        if (item.category() == null) {
            return llmOutput;
        }
        if (llmOutput != null && !llmOutput.isEmpty()) {
            return llmOutput;
        }

        // Dynamically find the first insightType whose categories contain this item's category name
        String categoryName = item.category().categoryName();
        String defaultType =
                insightTypes.stream()
                        .filter(
                                it ->
                                        it.categories() != null
                                                && it.categories().contains(categoryName))
                        .map(MemoryInsightType::name)
                        .findFirst()
                        .orElse(categoryName);

        return List.of(defaultType);
    }
}
