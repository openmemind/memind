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
package com.openmemind.ai.memory.core.extraction.insight.graph;

import com.openmemind.ai.memory.core.builder.InsightGraphAssistOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.graph.GraphPromptContextFormatter.GroupingClusterHint;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.BranchAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.GroupingAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.LeafAssist;
import com.openmemind.ai.memory.core.extraction.insight.graph.InsightGraphAssistContext.RootAssist;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Best-effort graph assistant over the local item subgraph for the current candidate batch.
 */
public final class DefaultInsightGraphAssistant implements InsightGraphAssistant {

    private static final Logger log = LoggerFactory.getLogger(DefaultInsightGraphAssistant.class);
    private static final List<ItemLinkType> SUPPORTED_LINK_TYPES =
            List.of(ItemLinkType.CAUSAL, ItemLinkType.TEMPORAL, ItemLinkType.SEMANTIC);
    private static final Comparator<RelationHint> RELATION_HINT_ORDER =
            Comparator.comparingInt(RelationHint::priority)
                    .thenComparing(
                            RelationHint::strength, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RelationHint::sourceId)
                    .thenComparing(RelationHint::targetId);

    private final MemoryStore store;
    private final InsightGraphAssistOptions options;
    private final GraphPromptContextFormatter formatter;

    public DefaultInsightGraphAssistant(
            MemoryStore store,
            InsightGraphAssistOptions options,
            GraphPromptContextFormatter formatter) {
        this.store = Objects.requireNonNull(store, "store");
        this.options = Objects.requireNonNull(options, "options");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    @Override
    public GroupingAssist groupingAssist(
            MemoryId memoryId, MemoryInsightType insightType, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return GroupingAssist.empty();
        }
        return withGraphFallback(
                () -> {
                    var snapshot = loadSnapshot(memoryId, items);
                    if (!snapshot.hasSignals()) {
                        return GroupingAssist.empty();
                    }

                    var clusters = deriveGroupingClusters(items, snapshot);
                    var relationHints =
                            summarizeRelations(snapshot.links(), options.maxRelationHints());
                    var context = formatter.formatGroupingHints(clusters, relationHints);
                    return context.isBlank() ? GroupingAssist.empty() : new GroupingAssist(context);
                },
                GroupingAssist.empty(),
                "grouping");
    }

    @Override
    public LeafAssist leafAssist(
            MemoryId memoryId,
            MemoryInsightType insightType,
            String groupName,
            List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return LeafAssist.identity(items);
        }
        return withGraphFallback(
                () -> {
                    var snapshot = loadSnapshot(memoryId, items);
                    if (!snapshot.hasSignals()) {
                        return LeafAssist.identity(items);
                    }

                    var orderedItems =
                            options.reorderEvidence()
                                    ? reorderItems(items, snapshot)
                                    : List.copyOf(items);
                    var orderedItemIds = orderedItems.stream().map(MemoryItem::id).toList();
                    var representativeEntities =
                            topRepresentativeEntities(
                                    orderedItemIds, snapshot, options.maxRepresentativeItems());
                    var relationHints =
                            summarizeRelations(snapshot.links(), options.maxRelationHints());
                    var context =
                            formatter.formatEvidenceHints(
                                    groupName,
                                    orderedItemIds,
                                    representativeEntities,
                                    relationHints);
                    return new LeafAssist(orderedItems, context);
                },
                LeafAssist.identity(items),
                "leaf");
    }

    @Override
    public BranchAssist branchAssist(
            MemoryId memoryId, MemoryInsightType insightType, List<MemoryInsight> leafInsights) {
        return BranchAssist.identity(leafInsights);
    }

    @Override
    public RootAssist rootAssist(
            MemoryId memoryId,
            MemoryInsightType rootInsightType,
            List<MemoryInsight> branchInsights) {
        return RootAssist.identity(branchInsights);
    }

    private <T> T withGraphFallback(Supplier<T> supplier, T fallback, String operation) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.debug("Insight graph assist degraded during {}: {}", operation, e.getMessage());
            return fallback;
        }
    }

    private GraphSnapshot loadSnapshot(MemoryId memoryId, List<MemoryItem> items) {
        var itemIds = items.stream().map(MemoryItem::id).filter(Objects::nonNull).toList();
        var graphOperations = store.graphOperations();
        var mentions = graphOperations.listItemEntityMentions(memoryId, itemIds);
        var links = graphOperations.listItemLinks(memoryId, itemIds, SUPPORTED_LINK_TYPES);
        var entitiesByKey = indexEntities(memoryId, mentions);

        Map<Long, List<EntityDescriptor>> entitiesByItemId = new HashMap<>();
        Map<String, Set<Long>> itemIdsByEntityKey = new HashMap<>();
        for (ItemEntityMention mention : mentions) {
            var descriptor = resolveEntityDescriptor(mention.entityKey(), entitiesByKey);
            if (descriptor == null || descriptor.entityType() == GraphEntityType.SPECIAL) {
                continue;
            }
            entitiesByItemId
                    .computeIfAbsent(mention.itemId(), ignored -> new ArrayList<>())
                    .add(descriptor);
            itemIdsByEntityKey
                    .computeIfAbsent(descriptor.entityKey(), ignored -> new LinkedHashSet<>())
                    .add(mention.itemId());
        }

        entitiesByItemId.replaceAll((ignored, values) -> dedupeEntities(values));
        return new GraphSnapshot(entitiesByItemId, itemIdsByEntityKey, List.copyOf(links));
    }

    private Map<String, GraphEntity> indexEntities(
            MemoryId memoryId, Collection<ItemEntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return Map.of();
        }
        Set<String> mentionedEntityKeys =
                mentions.stream()
                        .map(ItemEntityMention::entityKey)
                        .filter(Objects::nonNull)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return store.graphOperations().listEntities(memoryId).stream()
                .filter(entity -> mentionedEntityKeys.contains(entity.entityKey()))
                .collect(
                        HashMap::new,
                        (map, entity) -> map.put(entity.entityKey(), entity),
                        Map::putAll);
    }

    private EntityDescriptor resolveEntityDescriptor(
            String entityKey, Map<String, GraphEntity> entitiesByKey) {
        if (entityKey == null || entityKey.isBlank()) {
            return null;
        }
        var entity = entitiesByKey.get(entityKey);
        if (entity != null) {
            return new EntityDescriptor(
                    entity.entityKey(), entity.displayName(), entity.entityType());
        }
        String prefix =
                entityKey.contains(":") ? entityKey.substring(0, entityKey.indexOf(':')) : "";
        GraphEntityType entityType =
                "special".equals(prefix) ? GraphEntityType.SPECIAL : GraphEntityType.OTHER;
        String displayName =
                entityKey.contains(":")
                        ? entityKey.substring(entityKey.indexOf(':') + 1)
                        : entityKey;
        return new EntityDescriptor(entityKey, displayName.replace('_', ' '), entityType);
    }

    private List<EntityDescriptor> dedupeEntities(List<EntityDescriptor> values) {
        Map<String, EntityDescriptor> deduped = new HashMap<>();
        for (EntityDescriptor value : values) {
            deduped.putIfAbsent(value.entityKey(), value);
        }
        return deduped.values().stream()
                .sorted(
                        Comparator.comparing(
                                        EntityDescriptor::displayName,
                                        String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(EntityDescriptor::entityKey))
                .toList();
    }

    private List<GroupingClusterHint> deriveGroupingClusters(
            List<MemoryItem> items, GraphSnapshot snapshot) {
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        items.stream().map(MemoryItem::id).forEach(id -> adjacency.put(id, new LinkedHashSet<>()));

        for (ItemLink link : snapshot.links()) {
            connect(adjacency, link.sourceItemId(), link.targetItemId());
            connect(adjacency, link.targetItemId(), link.sourceItemId());
        }
        for (Set<Long> sharedItemIds : snapshot.itemIdsByEntityKey().values()) {
            List<Long> orderedIds = sharedItemIds.stream().sorted().toList();
            for (int i = 0; i < orderedIds.size(); i++) {
                for (int j = i + 1; j < orderedIds.size(); j++) {
                    connect(adjacency, orderedIds.get(i), orderedIds.get(j));
                    connect(adjacency, orderedIds.get(j), orderedIds.get(i));
                }
            }
        }

        Set<Long> visited = new HashSet<>();
        List<GroupingClusterHint> clusterHints = new ArrayList<>();
        for (MemoryItem item : items) {
            Long itemId = item.id();
            if (itemId == null || !visited.add(itemId)) {
                continue;
            }
            var component = collectComponent(itemId, adjacency, visited);
            var representativeEntities =
                    topRepresentativeEntities(
                            component, snapshot, options.maxRepresentativeItems());
            var relationCounts = countLinksWithin(component, snapshot.links());
            if (component.size() <= 1
                    && representativeEntities.isEmpty()
                    && relationCounts.values().stream().allMatch(count -> count == 0L)) {
                continue;
            }
            clusterHints.add(
                    new GroupingClusterHint(
                            component,
                            representativeEntities,
                            formatRelationCounts(relationCounts)));
        }

        return clusterHints.stream()
                .sorted(
                        Comparator.comparingInt((GroupingClusterHint hint) -> hint.itemIds().size())
                                .reversed()
                                .thenComparing(
                                        hint -> relationWeight(hint.relationSummary()),
                                        Comparator.reverseOrder())
                                .thenComparing(hint -> hint.itemIds().getFirst()))
                .limit(options.maxGroupingClusters())
                .toList();
    }

    private List<Long> collectComponent(
            Long startItemId, Map<Long, Set<Long>> adjacency, Set<Long> visited) {
        var queue = new ArrayDeque<Long>();
        var component = new ArrayList<Long>();
        queue.add(startItemId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            component.add(current);
            for (Long neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        component.sort(Comparator.naturalOrder());
        return List.copyOf(component);
    }

    private EnumMap<ItemLinkType, Long> countLinksWithin(
            List<Long> component, List<ItemLink> links) {
        Set<Long> itemIdSet = Set.copyOf(component);
        EnumMap<ItemLinkType, Long> counts = new EnumMap<>(ItemLinkType.class);
        for (ItemLinkType linkType : ItemLinkType.values()) {
            counts.put(linkType, 0L);
        }
        for (ItemLink link : links) {
            if (itemIdSet.contains(link.sourceItemId())
                    && itemIdSet.contains(link.targetItemId())) {
                counts.compute(
                        link.linkType(), (ignored, count) -> count == null ? 1L : count + 1L);
            }
        }
        return counts;
    }

    private String formatRelationCounts(EnumMap<ItemLinkType, Long> counts) {
        List<String> parts = new ArrayList<>();
        for (ItemLinkType linkType : SUPPORTED_LINK_TYPES) {
            long count = counts.getOrDefault(linkType, 0L);
            if (count > 0) {
                parts.add(linkType.name().toLowerCase() + "=" + count);
            }
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private int relationWeight(String relationSummary) {
        if (relationSummary == null
                || relationSummary.isBlank()
                || "none".equals(relationSummary)) {
            return 0;
        }
        return relationSummary.length();
    }

    private List<MemoryItem> reorderItems(List<MemoryItem> items, GraphSnapshot snapshot) {
        Map<Long, MemoryItem> itemById = new HashMap<>();
        for (MemoryItem item : items) {
            itemById.put(item.id(), item);
        }

        Map<Long, Integer> incomingEdges = new HashMap<>();
        Map<Long, Set<Long>> outgoingEdges = new HashMap<>();
        Map<Long, Integer> centrality = computeCentrality(items, snapshot);
        for (MemoryItem item : items) {
            incomingEdges.put(item.id(), 0);
            outgoingEdges.put(item.id(), new LinkedHashSet<>());
        }

        for (ItemLink link : snapshot.links()) {
            if (link.linkType() != ItemLinkType.CAUSAL
                    && link.linkType() != ItemLinkType.TEMPORAL) {
                continue;
            }
            if (!itemById.containsKey(link.sourceItemId())
                    || !itemById.containsKey(link.targetItemId())) {
                continue;
            }
            if (outgoingEdges.get(link.sourceItemId()).add(link.targetItemId())) {
                incomingEdges.compute(link.targetItemId(), (ignored, count) -> count + 1);
            }
        }

        Comparator<Long> priority =
                Comparator.comparing(
                                (Long itemId) -> resolveAnchor(itemById.get(itemId)),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                (Long itemId) -> centrality.getOrDefault(itemId, 0),
                                Comparator.reverseOrder())
                        .thenComparingLong(Long::longValue);

        PriorityQueue<Long> ready = new PriorityQueue<>(priority);
        incomingEdges.forEach(
                (itemId, indegree) -> {
                    if (indegree == 0) {
                        ready.add(itemId);
                    }
                });

        List<MemoryItem> ordered = new ArrayList<>(items.size());
        Set<Long> emitted = new HashSet<>();
        while (!ready.isEmpty()) {
            Long current = ready.poll();
            if (!emitted.add(current)) {
                continue;
            }
            ordered.add(itemById.get(current));
            for (Long targetId : outgoingEdges.getOrDefault(current, Set.of())) {
                int remaining = incomingEdges.compute(targetId, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(targetId);
                }
            }
        }

        items.stream()
                .map(MemoryItem::id)
                .filter(itemId -> !emitted.contains(itemId))
                .sorted(priority)
                .map(itemById::get)
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private Map<Long, Integer> computeCentrality(List<MemoryItem> items, GraphSnapshot snapshot) {
        Map<Long, Integer> centrality = new HashMap<>();
        items.forEach(item -> centrality.put(item.id(), 0));
        snapshot.entitiesByItemId()
                .forEach(
                        (itemId, descriptors) ->
                                centrality.compute(
                                        itemId,
                                        (ignored, value) ->
                                                (value == null ? 0 : value) + descriptors.size()));
        for (ItemLink link : snapshot.links()) {
            int weight = link.linkType() == ItemLinkType.SEMANTIC ? 1 : 2;
            centrality.compute(
                    link.sourceItemId(), (ignored, value) -> (value == null ? 0 : value) + weight);
            centrality.compute(
                    link.targetItemId(), (ignored, value) -> (value == null ? 0 : value) + weight);
        }
        return centrality;
    }

    private List<String> topRepresentativeEntities(
            Collection<Long> itemIds, GraphSnapshot snapshot, int limit) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> frequencyByDisplayName = new HashMap<>();
        Map<String, String> normalizedDisplayName = new HashMap<>();
        for (Long itemId : itemIds) {
            for (EntityDescriptor descriptor :
                    snapshot.entitiesByItemId().getOrDefault(itemId, List.of())) {
                String key = descriptor.displayName().toLowerCase();
                frequencyByDisplayName.merge(key, 1, Integer::sum);
                normalizedDisplayName.putIfAbsent(key, descriptor.displayName());
            }
        }
        return frequencyByDisplayName.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, Integer>, Integer>comparing(
                                        Map.Entry::getValue)
                                .reversed()
                                .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> normalizedDisplayName.get(entry.getKey()))
                .toList();
    }

    private List<String> summarizeRelations(List<ItemLink> links, int limit) {
        return links.stream()
                .map(this::toRelationHint)
                .sorted(RELATION_HINT_ORDER)
                .limit(limit)
                .map(RelationHint::rendered)
                .toList();
    }

    private RelationHint toRelationHint(ItemLink link) {
        String rendered =
                switch (link.linkType()) {
                    case CAUSAL -> "causal:" + link.sourceItemId() + "->" + link.targetItemId();
                    case TEMPORAL -> "temporal:" + link.sourceItemId() + "->" + link.targetItemId();
                    case SEMANTIC ->
                            "semantic:" + link.sourceItemId() + "<->" + link.targetItemId();
                };
        int priority =
                switch (link.linkType()) {
                    case CAUSAL -> 0;
                    case TEMPORAL -> 1;
                    case SEMANTIC -> 2;
                };
        return new RelationHint(
                rendered, priority, link.strength(), link.sourceItemId(), link.targetItemId());
    }

    private Instant resolveAnchor(MemoryItem item) {
        if (item == null) {
            return null;
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        return item.createdAt();
    }

    private void connect(Map<Long, Set<Long>> adjacency, Long sourceId, Long targetId) {
        if (sourceId == null || targetId == null) {
            return;
        }
        adjacency.computeIfAbsent(sourceId, ignored -> new LinkedHashSet<>()).add(targetId);
    }

    private record EntityDescriptor(
            String entityKey, String displayName, GraphEntityType entityType) {}

    private record GraphSnapshot(
            Map<Long, List<EntityDescriptor>> entitiesByItemId,
            Map<String, Set<Long>> itemIdsByEntityKey,
            List<ItemLink> links) {

        private boolean hasSignals() {
            return !entitiesByItemId.isEmpty() || !links.isEmpty();
        }
    }

    private record RelationHint(
            String rendered, int priority, Double strength, Long sourceId, Long targetId) {}
}
