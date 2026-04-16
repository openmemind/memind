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
package com.openmemind.ai.memory.core.extraction.item.graph;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizes extraction-side graph hints into bounded graph primitives.
 */
public final class GraphHintNormalizer {

    private static final Pattern ISO_DATE_LIKE_PATTERN =
            Pattern.compile("^\\d{4}(?:-\\d{2}){0,2}$");

    private static final Set<String> PRONOUN_LIKE_NAMES =
            Set.of(
                    "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours", "they",
                    "their", "theirs", "he", "him", "his", "she", "her", "hers", "it", "its", "我",
                    "我们", "你", "你们", "他", "她", "他们", "她们", "它", "自己");

    private static final Set<String> TEMPORAL_NAMES =
            Set.of(
                    "昨天",
                    "今天",
                    "明天",
                    "前天",
                    "后天",
                    "上周",
                    "这周",
                    "本周",
                    "下周",
                    "上个月",
                    "这个月",
                    "本月",
                    "下个月",
                    "今年",
                    "去年",
                    "明年",
                    "today",
                    "yesterday",
                    "tomorrow",
                    "last week",
                    "this week",
                    "next week",
                    "last month",
                    "this month",
                    "next month",
                    "this year",
                    "next year",
                    "last year");

    private static final Set<String> VALID_CAUSAL_RELATIONS =
            Set.of("caused_by", "enabled_by", "motivated_by");

    private final GraphEntityCanonicalizer entityCanonicalizer;

    public GraphHintNormalizer() {
        this(new GraphEntityCanonicalizer());
    }

    GraphHintNormalizer(GraphEntityCanonicalizer entityCanonicalizer) {
        this.entityCanonicalizer =
                Objects.requireNonNull(entityCanonicalizer, "entityCanonicalizer");
    }

    public ResolvedGraphBatch normalize(
            MemoryId memoryId,
            List<MemoryItem> persistedItems,
            List<ExtractedMemoryEntry> sourceEntries,
            ItemGraphOptions options) {
        if (options == null
                || !options.enabled()
                || persistedItems == null
                || persistedItems.isEmpty()
                || sourceEntries == null
                || sourceEntries.isEmpty()) {
            return ResolvedGraphBatch.empty();
        }

        int entryCount = Math.min(persistedItems.size(), sourceEntries.size());
        List<MemoryItem> batchItems = persistedItems.subList(0, entryCount);
        List<ExtractedMemoryEntry> batchEntries = sourceEntries.subList(0, entryCount);

        Map<String, GraphEntity> entities = new LinkedHashMap<>();
        List<ItemEntityMention> mentions = new ArrayList<>();
        List<ItemLink> causalCandidates = new ArrayList<>();

        for (int index = 0; index < entryCount; index++) {
            collectEntityMentions(
                    memoryId,
                    batchItems.get(index),
                    batchEntries.get(index),
                    options,
                    entities,
                    mentions);
            collectCausalLinks(
                    memoryId,
                    batchItems,
                    batchEntries.get(index),
                    index,
                    options,
                    causalCandidates);
        }

        List<ItemLink> itemLinks = new ArrayList<>();
        itemLinks.addAll(trimLinksBySource(causalCandidates, options.maxCausalReferencesPerItem()));
        itemLinks.addAll(
                buildTemporalLinks(memoryId, batchItems, options.maxTemporalLinksPerItem()));

        return new ResolvedGraphBatch(List.copyOf(entities.values()), mentions, itemLinks);
    }

    private void collectEntityMentions(
            MemoryId memoryId,
            MemoryItem item,
            ExtractedMemoryEntry entry,
            ItemGraphOptions options,
            Map<String, GraphEntity> entities,
            List<ItemEntityMention> mentions) {
        List<ExtractedGraphHints.ExtractedEntityHint> retained =
                entry.graphHints().entities().stream()
                        .filter(Objects::nonNull)
                        .filter(hint -> hint.name() != null && !hint.name().isBlank())
                        .sorted(
                                Comparator.comparing(
                                                ExtractedGraphHints.ExtractedEntityHint::salience,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                ExtractedGraphHints.ExtractedEntityHint::name,
                                                String.CASE_INSENSITIVE_ORDER))
                        .toList();

        Map<String, NormalizedEntity> normalized = new LinkedHashMap<>();
        for (ExtractedGraphHints.ExtractedEntityHint hint : retained) {
            if (normalized.size() >= options.maxEntitiesPerItem()) {
                break;
            }
            GraphEntityType entityType = entityCanonicalizer.normalizeType(hint.entityType());
            String normalizedName = entityCanonicalizer.normalizeName(hint.name());
            if (shouldDropEntityName(normalizedName, entityType)) {
                continue;
            }

            String entityKey = entityCanonicalizer.canonicalize(hint.name(), entityType);
            if (entityKey.isBlank() || normalized.containsKey(entityKey)) {
                continue;
            }
            normalized.put(
                    entityKey,
                    new NormalizedEntity(
                            entityKey,
                            entityCanonicalizer.normalizeDisplayName(hint.name()),
                            entityType,
                            clampScore(hint.salience())));
        }

        Instant createdAt = resolveGraphTimestamp(item);
        String memoryKey = memoryId.toIdentifier();
        normalized
                .values()
                .forEach(
                        normalizedEntity -> {
                            entities.putIfAbsent(
                                    normalizedEntity.entityKey(),
                                    new GraphEntity(
                                            normalizedEntity.entityKey(),
                                            memoryKey,
                                            normalizedEntity.displayName(),
                                            normalizedEntity.entityType(),
                                            Map.of("source", "item_extraction"),
                                            createdAt,
                                            createdAt));
                            mentions.add(
                                    new ItemEntityMention(
                                            memoryKey,
                                            item.id(),
                                            normalizedEntity.entityKey(),
                                            normalizedEntity.salience(),
                                            Map.of("source", "item_extraction"),
                                            createdAt));
                        });
    }

    private void collectCausalLinks(
            MemoryId memoryId,
            List<MemoryItem> items,
            ExtractedMemoryEntry entry,
            int currentIndex,
            ItemGraphOptions options,
            List<ItemLink> links) {
        List<ExtractedGraphHints.ExtractedCausalRelationHint> retained =
                entry.graphHints().causalRelations().stream()
                        .filter(Objects::nonNull)
                        .filter(relation -> relation.targetIndex() != null)
                        .filter(relation -> relation.targetIndex() >= 0)
                        .filter(relation -> relation.targetIndex() < currentIndex)
                        .filter(relation -> isValidCausalRelationType(relation.relationType()))
                        .sorted(
                                Comparator.comparing(
                                                ExtractedGraphHints.ExtractedCausalRelationHint
                                                        ::strength,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                ExtractedGraphHints.ExtractedCausalRelationHint
                                                        ::targetIndex))
                        .limit(options.maxCausalReferencesPerItem())
                        .toList();

        MemoryItem effectItem = items.get(currentIndex);
        String memoryKey = memoryId.toIdentifier();
        Instant createdAt = resolveGraphTimestamp(effectItem);
        retained.forEach(
                relation -> {
                    MemoryItem causeItem = items.get(relation.targetIndex());
                    links.add(
                            new ItemLink(
                                    memoryKey,
                                    causeItem.id(),
                                    effectItem.id(),
                                    ItemLinkType.CAUSAL,
                                    clampScore(relation.strength()).doubleValue(),
                                    Map.of(
                                            "relationType",
                                            normalizeRelationType(relation.relationType())),
                                    createdAt));
                });
    }

    private List<ItemLink> buildTemporalLinks(
            MemoryId memoryId, List<MemoryItem> items, int maxTemporalLinksPerItem) {
        List<MemoryItem> temporalItems =
                items.stream().filter(item -> resolveTemporalWindow(item) != null).toList();
        if (temporalItems.size() < 2) {
            return List.of();
        }

        List<MemoryItem> sortedItems =
                temporalItems.stream()
                        .sorted(
                                Comparator.comparing(
                                                (MemoryItem item) ->
                                                        resolveTemporalWindow(item).anchor())
                                        .thenComparing(MemoryItem::id))
                        .toList();

        Map<LinkIdentity, ItemLink> links = new LinkedHashMap<>();
        String memoryKey = memoryId.toIdentifier();
        for (int index = 0; index < sortedItems.size(); index++) {
            MemoryItem sourceItem = sortedItems.get(index);
            TemporalWindow sourceWindow = resolveTemporalWindow(sourceItem);
            int emitted = 0;
            for (int targetIndex = index + 1;
                    targetIndex < sortedItems.size() && emitted < maxTemporalLinksPerItem;
                    targetIndex++) {
                MemoryItem targetItem = sortedItems.get(targetIndex);
                TemporalWindow targetWindow = resolveTemporalWindow(targetItem);
                String relationType = classifyTemporalRelation(sourceWindow, targetWindow);
                if (relationType == null) {
                    continue;
                }

                ItemLink link =
                        new ItemLink(
                                memoryKey,
                                sourceItem.id(),
                                targetItem.id(),
                                ItemLinkType.TEMPORAL,
                                1.0d,
                                Map.of("relationType", relationType),
                                resolveGraphTimestamp(targetItem));
                links.putIfAbsent(new LinkIdentity(link), link);
                emitted++;
            }
        }
        return List.copyOf(links.values());
    }

    private List<ItemLink> trimLinksBySource(List<ItemLink> candidates, int maxPerSource) {
        Map<Long, List<ItemLink>> linksBySource = new LinkedHashMap<>();
        candidates.forEach(
                link ->
                        linksBySource
                                .computeIfAbsent(link.sourceItemId(), ignored -> new ArrayList<>())
                                .add(link));

        Map<LinkIdentity, ItemLink> retained = new LinkedHashMap<>();
        linksBySource
                .values()
                .forEach(sourceLinks -> retainTopLinks(sourceLinks, maxPerSource, retained));
        return List.copyOf(retained.values());
    }

    private static void retainTopLinks(
            Collection<ItemLink> sourceLinks,
            int maxPerSource,
            Map<LinkIdentity, ItemLink> retained) {
        sourceLinks.stream()
                .sorted(
                        Comparator.comparing(
                                        ItemLink::strength,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(ItemLink::targetItemId))
                .limit(maxPerSource)
                .forEach(link -> retained.putIfAbsent(new LinkIdentity(link), link));
    }

    private static String classifyTemporalRelation(
            TemporalWindow sourceWindow, TemporalWindow targetWindow) {
        if (sourceWindow == null || targetWindow == null) {
            return null;
        }
        if (sourceWindow.end() != null && !sourceWindow.end().isAfter(targetWindow.start())) {
            return "before";
        }
        if (sourceWindow.start().isBefore(targetWindow.endOrAnchor())
                && targetWindow.start().isBefore(sourceWindow.endOrAnchor())) {
            return "overlap";
        }
        if (!sourceWindow.start().isAfter(targetWindow.start())
                && Duration.between(sourceWindow.anchor(), targetWindow.anchor()).abs().toHours()
                        <= 24) {
            return "nearby";
        }
        return sourceWindow.anchor().isAfter(targetWindow.anchor()) ? null : "before";
    }

    private static TemporalWindow resolveTemporalWindow(MemoryItem item) {
        Instant start = firstNonNull(item.occurredStart(), item.occurredAt(), item.observedAt());
        if (start == null) {
            return null;
        }
        Instant end = item.occurredEnd();
        Instant anchor = firstNonNull(item.occurredStart(), item.occurredAt(), item.observedAt());
        return new TemporalWindow(start, end, anchor);
    }

    private boolean shouldDropEntityName(String normalizedName, GraphEntityType entityType) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return true;
        }
        if (entityType != GraphEntityType.SPECIAL
                && Set.of("self", "user", "assistant").contains(normalizedName)) {
            return true;
        }
        return PRONOUN_LIKE_NAMES.contains(normalizedName)
                || TEMPORAL_NAMES.contains(normalizedName)
                || ISO_DATE_LIKE_PATTERN.matcher(normalizedName).matches();
    }

    private static Float clampScore(Float score) {
        if (score == null) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, score));
    }

    private static Instant resolveGraphTimestamp(MemoryItem item) {
        return firstNonNull(
                item.createdAt(),
                item.observedAt(),
                item.occurredAt(),
                item.occurredStart(),
                Instant.EPOCH);
    }

    private static String normalizeRelationType(String relationType) {
        return relationType == null ? "caused_by" : relationType.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidCausalRelationType(String relationType) {
        return relationType != null
                && VALID_CAUSAL_RELATIONS.contains(relationType.toLowerCase(Locale.ROOT));
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record NormalizedEntity(
            String entityKey, String displayName, GraphEntityType entityType, Float salience) {}

    private record TemporalWindow(Instant start, Instant end, Instant anchor) {

        private Instant endOrAnchor() {
            return end != null ? end : anchor;
        }
    }

    private record LinkIdentity(Long sourceItemId, Long targetItemId, ItemLinkType linkType) {

        private LinkIdentity(ItemLink link) {
            this(link.sourceItemId(), link.targetItemId(), link.linkType());
        }
    }
}
