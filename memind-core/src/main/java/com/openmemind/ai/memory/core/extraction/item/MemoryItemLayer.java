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
package com.openmemind.ai.memory.core.extraction.item;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.NoOpItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.ItemEmbeddingTextResolver;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import reactor.core.publisher.Mono;

/**
 * MemoryItem extraction layer orchestrator
 *
 * <p>3 stage pipeline: LLM extraction + filtering → deduplication → vectorization + persistence
 *
 */
public class MemoryItemLayer implements MemoryItemExtractStep {

    private final MemoryItemExtractor extractor;
    private final MemoryItemDeduplicator deduplicator;
    private final MemoryStore memoryStore;
    private final MemoryVector vector;
    private final IdUtils.SnowflakeIdGenerator idGenerator;
    private final LlmSelfVerificationStep selfVerificationStep; // nullable
    private final ItemGraphMaterializer graphMaterializer;

    public MemoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore memoryStore,
            MemoryVector vector) {
        this(
                extractor,
                deduplicator,
                memoryStore,
                vector,
                IdUtils.snowflake(),
                null,
                NoOpItemGraphMaterializer.persistItemsOnly(memoryStore.itemOperations()));
    }

    public MemoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore memoryStore,
            MemoryVector vector,
            LlmSelfVerificationStep selfVerificationStep) {
        this(
                extractor,
                deduplicator,
                memoryStore,
                vector,
                IdUtils.snowflake(),
                selfVerificationStep,
                NoOpItemGraphMaterializer.persistItemsOnly(memoryStore.itemOperations()));
    }

    public MemoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore memoryStore,
            MemoryVector vector,
            ItemGraphMaterializer graphMaterializer) {
        this(
                extractor,
                deduplicator,
                memoryStore,
                vector,
                IdUtils.snowflake(),
                null,
                graphMaterializer);
    }

    public MemoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore memoryStore,
            MemoryVector vector,
            IdUtils.SnowflakeIdGenerator idGenerator,
            LlmSelfVerificationStep selfVerificationStep) {
        this(
                extractor,
                deduplicator,
                memoryStore,
                vector,
                idGenerator,
                selfVerificationStep,
                NoOpItemGraphMaterializer.persistItemsOnly(memoryStore.itemOperations()));
    }

    public MemoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore memoryStore,
            MemoryVector vector,
            IdUtils.SnowflakeIdGenerator idGenerator,
            LlmSelfVerificationStep selfVerificationStep,
            ItemGraphMaterializer graphMaterializer) {
        this.extractor = extractor;
        this.deduplicator = deduplicator;
        this.memoryStore = memoryStore;
        this.vector = vector;
        this.idGenerator = idGenerator;
        this.selfVerificationStep = selfVerificationStep;
        this.graphMaterializer =
                graphMaterializer != null
                        ? graphMaterializer
                        : NoOpItemGraphMaterializer.persistItemsOnly(memoryStore.itemOperations());
    }

    @Override
    public Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config) {

        List<MemoryInsightType> resolvedInsightTypes = resolveInsightTypes();

        // Phase 1: LLM extraction + filtering
        return extractor
                .extract(rawDataResult.segments(), resolvedInsightTypes, config)
                .map(
                        entries ->
                                filterEntries(
                                        entries, resolvedInsightTypes, config.allowedCategories()))
                // SelfVerification (optional)
                .flatMap(
                        entries ->
                                applySelfVerification(
                                        entries,
                                        rawDataResult.segments(),
                                        resolvedInsightTypes,
                                        config.allowedCategories(),
                                        config.language()))
                .map(
                        entries ->
                                filterEntries(
                                        entries, resolvedInsightTypes, config.allowedCategories()))
                // Phase 2: deduplication
                .flatMap(entries -> deduplicator.deduplicate(memoryId, entries))
                .map(DeduplicationResult::newEntries)
                // Phase 3: vectorization + persistence
                .flatMap(
                        newEntries ->
                                vectorizeAndPersist(
                                        memoryId,
                                        newEntries,
                                        config.scope(),
                                        config.contentType(),
                                        resolvedInsightTypes));
    }

    // ===== Phase 1: filtering + normalization =====

    private List<ExtractedMemoryEntry> filterEntries(
            List<ExtractedMemoryEntry> entries,
            List<MemoryInsightType> resolvedInsightTypes,
            java.util.Set<MemoryCategory> allowedCategories) {
        if (entries.isEmpty()) {
            return entries;
        }

        Set<String> allowedInsightTypes =
                resolvedInsightTypes.stream()
                        .map(type -> normalizeKey(type.name()))
                        .collect(Collectors.toSet());

        return entries.stream()
                // Empty content filtering
                .filter(entry -> entry.content() != null && !entry.content().isBlank())
                // insightTypes normalization
                .map(entry -> entryWithNormalizedInsightTypes(entry, allowedInsightTypes))
                .map(entry -> entryWithValidatedCategory(entry, allowedCategories))
                .filter(Objects::nonNull)
                // In-batch deduplication (keep the one with the highest confidence)
                .collect(Collectors.groupingBy(ExtractedMemoryEntry::content))
                .values()
                .stream()
                .map(
                        group ->
                                group.stream()
                                        .max(
                                                Comparator.comparingDouble(
                                                        ExtractedMemoryEntry::confidence))
                                        .orElseThrow())
                .toList();
    }

    private ExtractedMemoryEntry entryWithNormalizedInsightTypes(
            ExtractedMemoryEntry entry, Set<String> allowedInsightTypes) {
        if (entry.insightTypes() == null || entry.insightTypes().isEmpty()) {
            return entry;
        }

        List<String> normalized =
                entry.insightTypes().stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(this::normalizeKey)
                        .filter(allowedInsightTypes::contains)
                        .distinct()
                        .toList();

        if (normalized.equals(entry.insightTypes())) {
            return entry;
        }

        return new ExtractedMemoryEntry(
                entry.content(),
                entry.confidence(),
                entry.occurredAt(),
                entry.occurredStart(),
                entry.occurredEnd(),
                entry.timeGranularity(),
                entry.observedAt(),
                entry.rawDataId(),
                entry.contentHash(),
                normalized,
                entry.metadata(),
                entry.type(),
                entry.category());
    }

    private ExtractedMemoryEntry entryWithValidatedCategory(
            ExtractedMemoryEntry entry, java.util.Set<MemoryCategory> allowedCategories) {
        if (entry.category() == null || allowedCategories == null) {
            return entry;
        }
        return MemoryCategory.byName(entry.category())
                        .filter(allowedCategories::contains)
                        .isPresent()
                ? entry
                : null;
    }

    // ===== SelfVerification (optional) =====

    private Mono<List<ExtractedMemoryEntry>> applySelfVerification(
            List<ExtractedMemoryEntry> entries,
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            java.util.Set<MemoryCategory> allowedCategories,
            String language) {
        if (selfVerificationStep == null) {
            return Mono.just(entries);
        }

        String originalText =
                segments.stream().map(ParsedSegment::text).collect(Collectors.joining("\n\n"));

        String rawDataId = segments.isEmpty() ? null : segments.getFirst().rawDataId();

        Instant referenceTime = resolveReferenceTime(segments);
        Instant observedAt = resolveObservedAt(segments);

        String userName = resolveUserName(segments);

        return selfVerificationStep
                .verify(
                        originalText,
                        entries,
                        rawDataId,
                        referenceTime,
                        insightTypes,
                        userName,
                        allowedCategories,
                        language,
                        observedAt)
                .map(
                        missedEntries -> {
                            if (missedEntries.isEmpty()) {
                                return entries;
                            }
                            var merged = new ArrayList<>(entries);
                            merged.addAll(missedEntries);
                            return List.copyOf(merged);
                        })
                .onErrorResume(ex -> Mono.just(entries));
    }

    // ===== Phase 3: vectorization + persistence =====

    private Mono<MemoryItemResult> vectorizeAndPersist(
            MemoryId memoryId,
            List<ExtractedMemoryEntry> newEntries,
            MemoryScope scope,
            String contentType,
            List<MemoryInsightType> resolvedInsightTypes) {

        if (newEntries.isEmpty()) {
            return Mono.just(new MemoryItemResult(List.of(), resolvedInsightTypes));
        }

        List<String> contents =
                newEntries.stream().map(ItemEmbeddingTextResolver::resolve).toList();
        List<Map<String, Object>> metadataList =
                newEntries.stream()
                        .map(
                                e -> {
                                    var meta = new LinkedHashMap<String, Object>();
                                    meta.put("memoryId", memoryId.toIdentifier());
                                    Object whenToUse =
                                            e.metadata() != null
                                                    ? e.metadata().get("whenToUse")
                                                    : null;
                                    if (shouldPersistWhenToUse(e)
                                            && whenToUse instanceof String s
                                            && !s.isBlank()) {
                                        meta.put("whenToUse", s);
                                    }
                                    return Map.<String, Object>copyOf(meta);
                                })
                        .toList();

        return vector.storeBatch(memoryId, contents, metadataList)
                .flatMap(
                        vectorIds -> {
                            List<MemoryItem> newItems =
                                    IntStream.range(0, newEntries.size())
                                            .mapToObj(
                                                    i ->
                                                            toMemoryItem(
                                                                    memoryId,
                                                                    newEntries.get(i),
                                                                    vectorIds.get(i),
                                                                    scope,
                                                                    contentType))
                                            .toList();

                            return graphMaterializer
                                    .materialize(memoryId, newItems, newEntries)
                                    .onErrorResume(
                                            error ->
                                                    vector.deleteBatch(memoryId, vectorIds)
                                                            .onErrorResume(ignore -> Mono.empty())
                                                            .then(Mono.error(error)))
                                    .thenReturn(
                                            new MemoryItemResult(newItems, resolvedInsightTypes));
                        });
    }

    // ===== Utility methods =====

    private MemoryItem toMemoryItem(
            MemoryId memoryId,
            ExtractedMemoryEntry entry,
            String vectorId,
            MemoryScope scope,
            String contentType) {

        MemoryCategory category =
                entry.category() != null
                        ? MemoryCategory.byName(entry.category()).orElse(null)
                        : null;
        MemoryScope resolvedScope = category != null ? category.scope() : scope;

        return new MemoryItem(
                idGenerator.nextId(),
                memoryId.toIdentifier(),
                entry.content(),
                resolvedScope,
                category,
                contentType,
                vectorId,
                entry.rawDataId(),
                entry.contentHash(),
                entry.occurredAt(),
                entry.occurredStart(),
                entry.occurredEnd(),
                entry.timeGranularity(),
                entry.observedAt(),
                buildItemMetadata(entry),
                Instant.now(),
                entry.type());
    }

    private Map<String, Object> buildItemMetadata(ExtractedMemoryEntry entry) {
        var result = new LinkedHashMap<String, Object>();
        if (entry.metadata() != null) {
            result.putAll(entry.metadata());
        }
        if (!shouldPersistWhenToUse(entry)) {
            result.remove("whenToUse");
        }
        if (entry.insightTypes() != null && !entry.insightTypes().isEmpty()) {
            result.put("insightTypes", entry.insightTypes());
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private boolean shouldPersistWhenToUse(ExtractedMemoryEntry entry) {
        if (entry == null || entry.category() == null) {
            return false;
        }
        return MemoryCategory.byName(entry.category()).orElse(null) == MemoryCategory.TOOL;
    }

    private List<MemoryInsightType> resolveInsightTypes() {
        return memoryStore.insightOperations().listInsightTypes();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }

    private static Instant resolveReferenceTime(List<ParsedSegment> segments) {
        Instant observedAt = resolveObservedAt(segments);
        return observedAt != null ? observedAt : Instant.now();
    }

    private static Instant resolveObservedAt(List<ParsedSegment> segments) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            ParsedSegment segment = segments.get(i);
            if (segment.runtimeContext() != null && segment.runtimeContext().observedAt() != null) {
                return segment.runtimeContext().observedAt();
            }
        }
        return null;
    }

    private static String resolveUserName(List<ParsedSegment> segments) {
        for (ParsedSegment segment : segments) {
            if (segment.runtimeContext() != null
                    && segment.runtimeContext().userName() != null
                    && !segment.runtimeContext().userName().isBlank()) {
                return segment.runtimeContext().userName();
            }
        }
        return null;
    }
}
