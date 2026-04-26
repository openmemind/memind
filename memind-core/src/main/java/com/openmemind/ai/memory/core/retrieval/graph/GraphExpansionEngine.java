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
package com.openmemind.ai.memory.core.retrieval.graph;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.retrieval.ItemRetrievalGuard;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GraphExpansionEngine {

    private static final List<ItemLinkType> SUPPORTED_LINK_TYPES =
            List.of(ItemLinkType.SEMANTIC, ItemLinkType.TEMPORAL, ItemLinkType.CAUSAL);
    private static final Comparator<SeedAdjacentLink> SEED_ADJACENT_LINK_ORDER =
            Comparator.comparing(SeedAdjacentLink::overlap)
                    .reversed()
                    .thenComparing(SeedAdjacentLink::family, relationFamilyOrder())
                    .thenComparing(SeedAdjacentLink::strength, Comparator.reverseOrder())
                    .thenComparingLong(SeedAdjacentLink::neighborItemId)
                    .thenComparingLong(link -> link.link().sourceItemId())
                    .thenComparingLong(link -> link.link().targetItemId());
    private static final Map<RelationFamily, Double> DEFAULT_RELATION_WEIGHTS =
            Map.of(
                    RelationFamily.SEMANTIC, 1.00d,
                    RelationFamily.TEMPORAL, 0.90d,
                    RelationFamily.CAUSAL, 0.95d,
                    RelationFamily.ENTITY_SIBLING, 0.85d);

    private final MemoryStore store;

    public GraphExpansionEngine(MemoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public GraphExpansionResult expand(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds) {
        boolean enabled = settings != null && settings.enabled();
        if (!enabled || seeds == null || seeds.isEmpty()) {
            return GraphExpansionResult.empty(enabled);
        }

        var directIds =
                seeds.stream()
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var materializedSeeds = materializeEligibleSeeds(context, seeds, settings.maxSeedItems());
        if (materializedSeeds.isEmpty()) {
            return GraphExpansionResult.empty(true);
        }

        var candidateBundle =
                collectGraphCandidates(context, config, settings, materializedSeeds, directIds);
        var rankedGraph =
                rankGraphCandidates(candidateBundle.candidates()).stream()
                        .filter(candidate -> !directIds.contains(candidate.sourceId()))
                        .toList();

        return new GraphExpansionResult(
                rankedGraph,
                true,
                false,
                false,
                materializedSeeds.size(),
                candidateBundle.linkExpansionCount(),
                candidateBundle.entityExpansionCount(),
                candidateBundle.candidates().size(),
                candidateBundle.overlapCount(),
                candidateBundle.skippedOverFanoutEntityCount());
    }

    private List<MaterializedSeed> materializeEligibleSeeds(
            QueryContext context, List<ScoredResult> directItems, int maxSeedItems) {
        var requestedSeedIds =
                directItems.stream()
                        .limit(maxSeedItems)
                        .map(ScoredResult::sourceId)
                        .map(this::parseItemId)
                        .flatMap(Optional::stream)
                        .toList();
        if (requestedSeedIds.isEmpty()) {
            return List.of();
        }

        var directById =
                directItems.stream()
                        .collect(
                                Collectors.toMap(
                                        ScoredResult::sourceId,
                                        Function.identity(),
                                        (left, right) -> left,
                                        LinkedHashMap::new));
        var directRanks = new HashMap<String, Integer>();
        for (int i = 0; i < directItems.size(); i++) {
            directRanks.putIfAbsent(directItems.get(i).sourceId(), i);
        }

        return store.itemOperations().getItemsByIds(context.memoryId(), requestedSeedIds).stream()
                .filter(item -> ItemRetrievalGuard.allows(item, context))
                .sorted(
                        Comparator.comparingInt(
                                item ->
                                        directRanks.getOrDefault(
                                                String.valueOf(item.id()), Integer.MAX_VALUE)))
                .map(
                        item -> {
                            var direct = directById.get(String.valueOf(item.id()));
                            return direct == null
                                    ? null
                                    : new MaterializedSeed(item, direct.finalScore());
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    private GraphCandidateBundle collectGraphCandidates(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings graphSettings,
            List<MaterializedSeed> seeds,
            Set<String> directIds) {
        GraphOperations graphOps = store.graphOperations();
        Map<Long, GraphCandidateAccumulator> rawCandidates = new LinkedHashMap<>();
        int linkExpansionCount = 0;
        int entityExpansionCount = 0;
        Set<Long> overlapItemIds = new LinkedHashSet<>();

        var seedIds = seeds.stream().map(seed -> seed.item().id()).toList();
        var seedMentionsByItem =
                graphOps.listItemEntityMentions(context.memoryId(), seedIds).stream()
                        .filter(this::hasEntitySignal)
                        .filter(
                                mention ->
                                        mention.confidence()
                                                >= graphSettings.minMentionConfidence())
                        .filter(mention -> !isSpecialEntity(mention.entityKey()))
                        .collect(Collectors.groupingBy(ItemEntityMention::itemId));

        var reverseMentionBundle =
                loadReverseMentionBundle(
                        context, graphSettings, seeds, seedMentionsByItem, graphOps);
        int skippedOverFanoutEntityCount = reverseMentionBundle.overFanoutEntityKeys().size();
        var adjacentLinksBySeed =
                indexAdjacentLinksBySeed(
                        seedIds,
                        directIds,
                        graphOps.listAdjacentItemLinks(
                                context.memoryId(), seedIds, SUPPORTED_LINK_TYPES));

        for (MaterializedSeed seed : seeds) {
            var perSeedRelationCounts = new EnumMap<RelationFamily, Integer>(RelationFamily.class);
            var orderedAdjacentLinks =
                    adjacentLinksBySeed.getOrDefault(seed.item().id(), List.of()).stream()
                            .sorted(SEED_ADJACENT_LINK_ORDER)
                            .toList();
            for (SeedAdjacentLink adjacent : orderedAdjacentLinks) {
                if (adjacent.strength() < graphSettings.minLinkStrength()) {
                    continue;
                }
                if (!adjacent.overlap()) {
                    int offered = perSeedRelationCounts.getOrDefault(adjacent.family(), 0);
                    if (offered >= maxNeighborsPerSeed(graphSettings, adjacent.family())) {
                        continue;
                    }
                    perSeedRelationCounts.put(adjacent.family(), offered + 1);
                    linkExpansionCount++;
                }
                rawCandidates
                        .computeIfAbsent(adjacent.neighborItemId(), GraphCandidateAccumulator::new)
                        .accept(
                                adjacent.family(),
                                seed.item().id(),
                                baseScore(
                                        adjacent.family(),
                                        seed.seedRelevance(),
                                        adjacent.strength()));
                if (adjacent.overlap()) {
                    overlapItemIds.add(adjacent.neighborItemId());
                }
            }

            int entitySiblingCount = 0;
            var seedEntityKeys =
                    seedMentionsByItem.getOrDefault(seed.item().id(), List.of()).stream()
                            .map(ItemEntityMention::entityKey)
                            .distinct()
                            .toList();
            for (String entityKey : seedEntityKeys) {
                if (reverseMentionBundle.overFanoutEntityKeys().contains(entityKey)) {
                    continue;
                }
                var mentionsForEntity =
                        reverseMentionBundle
                                .mentionsByEntityKey()
                                .getOrDefault(entityKey, List.of());
                for (ItemEntityMention mention : mentionsForEntity) {
                    if (Objects.equals(mention.itemId(), seed.item().id())) {
                        continue;
                    }
                    boolean overlap = directIds.contains(String.valueOf(mention.itemId()));
                    if (!overlap) {
                        if (entitySiblingCount >= graphSettings.maxEntitySiblingItemsPerSeed()) {
                            break;
                        }
                        entitySiblingCount++;
                        entityExpansionCount++;
                    }
                    rawCandidates
                            .computeIfAbsent(mention.itemId(), GraphCandidateAccumulator::new)
                            .accept(
                                    RelationFamily.ENTITY_SIBLING,
                                    seed.item().id(),
                                    baseScore(
                                            RelationFamily.ENTITY_SIBLING,
                                            seed.seedRelevance(),
                                            mention.confidence().doubleValue()));
                    if (overlap) {
                        overlapItemIds.add(mention.itemId());
                    }
                }
            }
        }

        if (rawCandidates.isEmpty()) {
            return new GraphCandidateBundle(
                    Map.of(),
                    linkExpansionCount,
                    entityExpansionCount,
                    overlapItemIds.size(),
                    skippedOverFanoutEntityCount);
        }

        var candidateItemsById =
                store
                        .itemOperations()
                        .getItemsByIds(context.memoryId(), rawCandidates.keySet())
                        .stream()
                        .collect(Collectors.toMap(MemoryItem::id, Function.identity()));
        Map<Long, GraphCandidate> filteredCandidates = new LinkedHashMap<>();
        for (var entry : rawCandidates.entrySet()) {
            var item = candidateItemsById.get(entry.getKey());
            if (item == null || !ItemRetrievalGuard.allows(item, context)) {
                continue;
            }
            filteredCandidates.put(
                    entry.getKey(), entry.getValue().resolve(item, context, config, graphSettings));
        }

        return new GraphCandidateBundle(
                filteredCandidates,
                linkExpansionCount,
                entityExpansionCount,
                overlapItemIds.size(),
                skippedOverFanoutEntityCount);
    }

    private Map<Long, List<SeedAdjacentLink>> indexAdjacentLinksBySeed(
            List<Long> seedIds, Set<String> directIds, List<ItemLink> adjacentLinks) {
        var seedIdSet = Set.copyOf(seedIds);
        var buckets = new LinkedHashMap<Long, List<SeedAdjacentLink>>();
        seedIds.forEach(seedId -> buckets.put(seedId, new ArrayList<>()));
        for (ItemLink link : adjacentLinks) {
            if (seedIdSet.contains(link.sourceItemId())) {
                long neighborItemId = link.targetItemId();
                buckets.get(link.sourceItemId())
                        .add(
                                seedAdjacentLink(
                                        link, link.sourceItemId(), neighborItemId, directIds));
            }
            if (seedIdSet.contains(link.targetItemId())) {
                long neighborItemId = link.sourceItemId();
                buckets.get(link.targetItemId())
                        .add(
                                seedAdjacentLink(
                                        link, link.targetItemId(), neighborItemId, directIds));
            }
        }
        return buckets.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> List.copyOf(entry.getValue()),
                                (left, right) -> left,
                                LinkedHashMap::new));
    }

    private ReverseMentionBundle loadReverseMentionBundle(
            QueryContext context,
            RetrievalGraphSettings graphSettings,
            List<MaterializedSeed> seeds,
            Map<Long, List<ItemEntityMention>> seedMentionsByItem,
            GraphOperations graphOps) {
        var uniqueSeedEntityKeys =
                seeds.stream()
                        .map(seed -> seedMentionsByItem.getOrDefault(seed.item().id(), List.of()))
                        .flatMap(Collection::stream)
                        .map(ItemEntityMention::entityKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueSeedEntityKeys.isEmpty()) {
            return ReverseMentionBundle.empty();
        }

        var mentionsByEntityKey =
                graphOps
                        .listItemEntityMentionsByEntityKeys(
                                context.memoryId(),
                                uniqueSeedEntityKeys,
                                graphSettings.maxItemsPerEntity() + 1)
                        .stream()
                        .filter(this::hasEntitySignal)
                        .filter(
                                mention ->
                                        mention.confidence()
                                                >= graphSettings.minMentionConfidence())
                        .filter(mention -> !isSpecialEntity(mention.entityKey()))
                        .sorted(
                                Comparator.comparing(ItemEntityMention::entityKey)
                                        .thenComparing(ItemEntityMention::itemId))
                        .collect(
                                Collectors.groupingBy(
                                        ItemEntityMention::entityKey,
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        var overFanoutEntityKeys =
                mentionsByEntityKey.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getValue().size()
                                                == graphSettings.maxItemsPerEntity() + 1)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        overFanoutEntityKeys.forEach(mentionsByEntityKey::remove);
        return new ReverseMentionBundle(
                Map.copyOf(mentionsByEntityKey), Set.copyOf(overFanoutEntityKeys));
    }

    private List<ScoredResult> rankGraphCandidates(Map<Long, GraphCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        var sorted =
                candidates.values().stream()
                        .sorted(
                                Comparator.comparingDouble(GraphCandidate::score)
                                        .reversed()
                                        .thenComparingLong(GraphCandidate::itemId))
                        .toList();
        double maxGraphScore = sorted.getFirst().score();
        double divisor = maxGraphScore > 0.0d ? maxGraphScore : 1.0d;
        return sorted.stream()
                .map(
                        candidate ->
                                new ScoredResult(
                                        ScoredResult.SourceType.ITEM,
                                        String.valueOf(candidate.itemId()),
                                        candidate.content() == null ? "" : candidate.content(),
                                        0f,
                                        candidate.score() / divisor,
                                        candidate.occurredAt()))
                .toList();
    }

    private SeedAdjacentLink seedAdjacentLink(
            ItemLink link, long seedItemId, long neighborItemId, Set<String> directIds) {
        return new SeedAdjacentLink(
                link,
                seedItemId,
                neighborItemId,
                toRelationFamily(link.linkType()),
                directIds.contains(String.valueOf(neighborItemId)),
                link.strength() == null ? 1.0d : link.strength());
    }

    private RelationFamily toRelationFamily(ItemLinkType linkType) {
        return switch (linkType) {
            case SEMANTIC -> RelationFamily.SEMANTIC;
            case TEMPORAL -> RelationFamily.TEMPORAL;
            case CAUSAL -> RelationFamily.CAUSAL;
        };
    }

    private static Comparator<RelationFamily> relationFamilyOrder() {
        return Comparator.comparingInt(
                family ->
                        switch (family) {
                            case SEMANTIC -> 0;
                            case TEMPORAL -> 1;
                            case CAUSAL -> 2;
                            case ENTITY_SIBLING -> 3;
                        });
    }

    private int maxNeighborsPerSeed(RetrievalGraphSettings graphSettings, RelationFamily family) {
        return switch (family) {
            case SEMANTIC -> graphSettings.maxSemanticNeighborsPerSeed();
            case TEMPORAL -> graphSettings.maxTemporalNeighborsPerSeed();
            case CAUSAL -> graphSettings.maxCausalNeighborsPerSeed();
            case ENTITY_SIBLING -> graphSettings.maxEntitySiblingItemsPerSeed();
        };
    }

    private double baseScore(RelationFamily family, double seedRelevance, double relationStrength) {
        double relationWeight = DEFAULT_RELATION_WEIGHTS.getOrDefault(family, 1.0d);
        return seedRelevance * relationWeight * relationStrength;
    }

    private boolean hasEntitySignal(ItemEntityMention mention) {
        return mention.confidence() != null
                && mention.entityKey() != null
                && !mention.entityKey().isBlank();
    }

    private boolean isSpecialEntity(String entityKey) {
        return entityKey != null && entityKey.startsWith("special:");
    }

    private Optional<Long> parseItemId(String sourceId) {
        try {
            return Optional.of(Long.parseLong(sourceId));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private record MaterializedSeed(MemoryItem item, double seedRelevance) {}

    private record SeedAdjacentLink(
            ItemLink link,
            long seedItemId,
            long neighborItemId,
            RelationFamily family,
            boolean overlap,
            double strength) {}

    private static final class GraphCandidateAccumulator {
        private final long itemId;
        private final Map<Long, Double> semanticBaseScoresBySeed = new LinkedHashMap<>();
        private double bestNonSemanticBaseScore;

        private GraphCandidateAccumulator(long itemId) {
            this.itemId = itemId;
        }

        private void accept(RelationFamily family, long seedItemId, double baseScore) {
            if (family == RelationFamily.SEMANTIC) {
                semanticBaseScoresBySeed.merge(seedItemId, baseScore, Math::max);
                return;
            }
            bestNonSemanticBaseScore = Math.max(bestNonSemanticBaseScore, baseScore);
        }

        private GraphCandidate resolve(
                MemoryItem item,
                QueryContext context,
                RetrievalConfig config,
                RetrievalGraphSettings graphSettings) {
            double recencyAdjustment =
                    TimeDecay.factor(item.occurredAt(), context, config.scoring());
            double semanticScore =
                    accumulateSemantic(graphSettings.semanticEvidenceDecayFactor())
                            * recencyAdjustment;
            double nonSemanticScore = bestNonSemanticBaseScore * recencyAdjustment;
            return new GraphCandidate(
                    itemId,
                    Math.max(semanticScore, nonSemanticScore),
                    item.content(),
                    item.occurredAt());
        }

        private double accumulateSemantic(double decayFactor) {
            if (semanticBaseScoresBySeed.isEmpty()) {
                return 0.0d;
            }

            var contributions =
                    semanticBaseScoresBySeed.values().stream()
                            .sorted(Comparator.reverseOrder())
                            .toList();
            double raw = 0.0d;
            for (int i = 0; i < contributions.size(); i++) {
                double decay = 1.0d / (1.0d + (i * decayFactor));
                raw += contributions.get(i) * decay;
            }
            return Math.tanh(raw);
        }
    }

    private record GraphCandidate(
            long itemId, double score, String content, java.time.Instant occurredAt) {}

    private enum RelationFamily {
        SEMANTIC,
        TEMPORAL,
        CAUSAL,
        ENTITY_SIBLING
    }

    private record GraphCandidateBundle(
            Map<Long, GraphCandidate> candidates,
            int linkExpansionCount,
            int entityExpansionCount,
            int overlapCount,
            int skippedOverFanoutEntityCount) {}

    private record ReverseMentionBundle(
            Map<String, List<ItemEntityMention>> mentionsByEntityKey,
            Set<String> overFanoutEntityKeys) {

        private static ReverseMentionBundle empty() {
            return new ReverseMentionBundle(Map.of(), Set.of());
        }
    }
}
