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
package com.openmemind.ai.memory.core.store.graph;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link GraphOperations}.
 */
public class InMemoryGraphOperations implements GraphOperations {

    private final Map<String, Map<String, GraphEntity>> entities = new ConcurrentHashMap<>();
    private final Map<String, Map<MentionKey, ItemEntityMention>> mentions =
            new ConcurrentHashMap<>();
    private final Map<String, Map<LinkKey, ItemLink>> itemLinks = new ConcurrentHashMap<>();
    private final Map<String, Map<CooccurrenceKey, EntityCooccurrence>> entityCooccurrences =
            new ConcurrentHashMap<>();

    @Override
    public void upsertEntities(MemoryId memoryId, List<GraphEntity> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, GraphEntity> scoped =
                entities.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(entity -> scoped.put(entity.entityKey(), entity));
    }

    @Override
    public void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<MentionKey, ItemEntityMention> scoped =
                mentions.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(
                mention ->
                        scoped.put(new MentionKey(mention.itemId(), mention.entityKey()), mention));
    }

    @Override
    public void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> affectedEntityKeys = normalizeEntityKeys(entityKeys);
        if (affectedEntityKeys.isEmpty()) {
            return;
        }

        String memoryKey = memoryId.toIdentifier();
        Map<CooccurrenceKey, EntityCooccurrence> scoped =
                entityCooccurrences.computeIfAbsent(
                        memoryKey, ignored -> new ConcurrentHashMap<>());
        scoped.keySet()
                .removeIf(
                        key ->
                                affectedEntityKeys.contains(key.leftEntityKey())
                                        || affectedEntityKeys.contains(key.rightEntityKey()));

        Map<Long, Set<String>> mentionsByItem = new HashMap<>();
        mentions.getOrDefault(memoryKey, Map.of())
                .values()
                .forEach(
                        mention ->
                                mentionsByItem
                                        .computeIfAbsent(
                                                mention.itemId(), ignored -> new HashSet<>())
                                        .add(mention.entityKey()));

        Map<CooccurrenceKey, Integer> counts = new HashMap<>();
        mentionsByItem
                .values()
                .forEach(entitySet -> accumulateCounts(entitySet, affectedEntityKeys, counts));

        Instant rebuiltAt = Instant.now();
        counts.forEach(
                (key, count) ->
                        scoped.put(
                                key,
                                new EntityCooccurrence(
                                        memoryKey,
                                        key.leftEntityKey(),
                                        key.rightEntityKey(),
                                        count,
                                        Map.of(),
                                        rebuiltAt)));
    }

    @Override
    public void upsertItemLinks(MemoryId memoryId, List<ItemLink> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<LinkKey, ItemLink> scoped =
                itemLinks.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(
                link ->
                        scoped.put(
                                new LinkKey(
                                        link.sourceItemId(), link.targetItemId(), link.linkType()),
                                link));
    }

    @Override
    public List<GraphEntity> listEntities(MemoryId memoryId) {
        return entities.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(Comparator.comparing(GraphEntity::entityKey))
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId) {
        return mentions.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(ItemEntityMention::itemId)
                                .thenComparing(ItemEntityMention::entityKey))
                .toList();
    }

    @Override
    public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
        return entityCooccurrences.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(EntityCooccurrence::leftEntityKey)
                                .thenComparing(EntityCooccurrence::rightEntityKey))
                .toList();
    }

    @Override
    public List<ItemLink> listItemLinks(MemoryId memoryId) {
        return itemLinks.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(ItemLink::sourceItemId)
                                .thenComparing(ItemLink::targetItemId)
                                .thenComparing(ItemLink::linkType))
                .toList();
    }

    private static void accumulateCounts(
            Set<String> entitySet,
            Set<String> affectedEntityKeys,
            Map<CooccurrenceKey, Integer> counts) {
        List<String> sortedKeys = entitySet.stream().filter(Objects::nonNull).sorted().toList();
        for (int i = 0; i < sortedKeys.size(); i++) {
            for (int j = i + 1; j < sortedKeys.size(); j++) {
                String leftEntityKey = sortedKeys.get(i);
                String rightEntityKey = sortedKeys.get(j);
                if (!affectedEntityKeys.contains(leftEntityKey)
                        && !affectedEntityKeys.contains(rightEntityKey)) {
                    continue;
                }
                counts.merge(new CooccurrenceKey(leftEntityKey, rightEntityKey), 1, Integer::sum);
            }
        }
    }

    private static Set<String> normalizeEntityKeys(Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        entityKeys.stream().filter(Objects::nonNull).forEach(normalized::add);
        return normalized;
    }

    private record MentionKey(Long itemId, String entityKey) {}

    private record LinkKey(Long sourceItemId, Long targetItemId, ItemLinkType linkType) {}

    private record CooccurrenceKey(String leftEntityKey, String rightEntityKey) {}
}
