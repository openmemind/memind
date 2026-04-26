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
package com.openmemind.ai.memory.core.extraction.item.graph.link.causal;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
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

/**
 * Normalizes extracted causal hints into bounded cause/effect item links.
 */
public final class CausalHintNormalizer {

    private static final float CAUSAL_MIN_STRENGTH = 0.5f;
    private static final Set<String> VALID_CAUSAL_RELATIONS =
            Set.of("caused_by", "enabled_by", "motivated_by");

    public List<ItemLink> normalize(
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
            return List.of();
        }

        int entryCount = Math.min(persistedItems.size(), sourceEntries.size());
        List<ItemLink> causalCandidates = new ArrayList<>();
        for (int index = 0; index < entryCount; index++) {
            collectCausalLinks(
                    memoryId, persistedItems, sourceEntries.get(index), options, causalCandidates);
        }
        return trimLinksBySource(causalCandidates, options.maxCausalReferencesPerItem());
    }

    private void collectCausalLinks(
            MemoryId memoryId,
            List<MemoryItem> items,
            ExtractedMemoryEntry entry,
            ItemGraphOptions options,
            List<ItemLink> links) {
        int itemCount = items.size();
        List<ExtractedGraphHints.ExtractedCausalRelationHint> retained =
                entry.graphHints().causalRelations().stream()
                        .filter(Objects::nonNull)
                        .filter(relation -> relation.causeIndex() != null)
                        .filter(relation -> relation.effectIndex() != null)
                        .filter(relation -> relation.causeIndex() >= 0)
                        .filter(relation -> relation.effectIndex() >= 0)
                        .filter(relation -> relation.causeIndex() < itemCount)
                        .filter(relation -> relation.effectIndex() < itemCount)
                        .filter(
                                relation ->
                                        !Objects.equals(
                                                relation.causeIndex(), relation.effectIndex()))
                        .filter(relation -> isValidCausalRelationType(relation.relationType()))
                        .filter(relation -> normalizedStrength(relation.strength()) != null)
                        .filter(
                                relation ->
                                        normalizedStrength(relation.strength())
                                                >= CAUSAL_MIN_STRENGTH)
                        .sorted(
                                Comparator
                                        .<ExtractedGraphHints.ExtractedCausalRelationHint, Float>
                                                comparing(
                                                        relation ->
                                                                normalizedStrength(
                                                                        relation.strength()),
                                                        Comparator.reverseOrder())
                                        .thenComparing(
                                                ExtractedGraphHints.ExtractedCausalRelationHint
                                                        ::causeIndex)
                                        .thenComparing(
                                                ExtractedGraphHints.ExtractedCausalRelationHint
                                                        ::effectIndex))
                        .limit(options.maxCausalReferencesPerItem())
                        .toList();

        String memoryKey = memoryId.toIdentifier();
        retained.forEach(
                relation -> {
                    MemoryItem causeItem = items.get(relation.causeIndex());
                    MemoryItem effectItem = items.get(relation.effectIndex());
                    Instant createdAt = resolveGraphTimestamp(effectItem);
                    links.add(
                            new ItemLink(
                                    memoryKey,
                                    causeItem.id(),
                                    effectItem.id(),
                                    ItemLinkType.CAUSAL,
                                    normalizedStrength(relation.strength()).doubleValue(),
                                    Map.of(
                                            "relationType",
                                            normalizeRelationType(relation.relationType())),
                                    createdAt));
                });
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

    private static Float normalizedStrength(Float score) {
        if (score == null) {
            return null;
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

    private record LinkIdentity(Long sourceItemId, Long targetItemId, ItemLinkType linkType) {

        private LinkIdentity(ItemLink link) {
            this(link.sourceItemId(), link.targetItemId(), link.linkType());
        }
    }
}
