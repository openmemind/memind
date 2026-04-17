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
import com.openmemind.ai.memory.core.retrieval.scoring.ResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Duration;
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
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * One-hop graph enrichment for direct retrieval candidates.
 */
public final class DefaultRetrievalGraphAssistant implements RetrievalGraphAssistant {

    private static final List<ItemLinkType> SUPPORTED_LINK_TYPES =
            List.of(ItemLinkType.SEMANTIC, ItemLinkType.TEMPORAL, ItemLinkType.CAUSAL);
    private static final Map<RelationFamily, Double> DEFAULT_RELATION_WEIGHTS =
            Map.of(
                    RelationFamily.SEMANTIC, 1.00d,
                    RelationFamily.TEMPORAL, 0.90d,
                    RelationFamily.CAUSAL, 0.95d,
                    RelationFamily.ENTITY_SIBLING, 0.85d);

    private final MemoryStore store;

    public DefaultRetrievalGraphAssistant(MemoryStore store) {
        this.store = store;
    }

    @Override
    public Mono<RetrievalGraphAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems) {
        boolean enabled = graphSettings != null && graphSettings.enabled();
        if (!enabled || directItems == null || directItems.isEmpty() || store == null) {
            return Mono.just(RetrievalGraphAssistResult.directOnly(directItems, enabled));
        }

        Duration timeout = graphSettings.timeout();
        return Mono.fromCallable(
                        () -> {
                            try (var ignored = GraphQueryBudgetContext.open(timeout)) {
                                return expandAndFuse(context, config, graphSettings, directItems);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(timeout)
                .onErrorResume(
                        TimeoutException.class,
                        error ->
                                Mono.just(
                                        RetrievalGraphAssistResult.degraded(
                                                directItems, true, true)))
                .onErrorResume(
                        error ->
                                Mono.just(
                                        RetrievalGraphAssistResult.degraded(
                                                directItems, true, false)));
    }

    private RetrievalGraphAssistResult expandAndFuse(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems) {
        var directIds =
                directItems.stream()
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var seeds = materializeEligibleSeeds(context, directItems, graphSettings.maxSeedItems());
        if (seeds.isEmpty()) {
            return RetrievalGraphAssistResult.directOnly(directItems, true);
        }

        var candidateBundle =
                collectGraphCandidates(context, config, graphSettings, seeds, directIds);
        var rankedGraph =
                candidateBundle.candidates().values().stream()
                        .sorted(Comparator.comparingDouble(GraphCandidate::score).reversed())
                        .limit(graphSettings.maxExpandedItems())
                        .map(GraphCandidate::toScoredResult)
                        .toList();

        int pinned = Math.min(graphSettings.protectDirectTopK(), directItems.size());
        var pinnedPrefix = directItems.subList(0, pinned);
        var directTail = directItems.subList(pinned, directItems.size());
        var fusedTail =
                rankedGraph.isEmpty()
                        ? directTail
                        : ResultMerger.merge(
                                config.scoring(),
                                List.of(directTail, rankedGraph),
                                1.0d,
                                graphSettings.graphChannelWeight());

        var finalItems = Stream.concat(pinnedPrefix.stream(), fusedTail.stream()).toList();
        int admittedGraphCandidateCount =
                (int)
                        finalItems.stream()
                                .map(ScoredResult::sourceId)
                                .filter(sourceId -> !directIds.contains(sourceId))
                                .count();

        return new RetrievalGraphAssistResult(
                finalItems,
                new RetrievalGraphAssistResult.GraphAssistStats(
                        true,
                        false,
                        false,
                        seeds.size(),
                        candidateBundle.linkExpansionCount(),
                        candidateBundle.entityExpansionCount(),
                        candidateBundle.candidates().size(),
                        admittedGraphCandidateCount,
                        countDisplacedDirectItems(directItems, finalItems, directItems.size()),
                        candidateBundle.overlapCount(),
                        candidateBundle.skippedOverFanoutEntityCount()));
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
        Map<Long, GraphCandidate> rawCandidates = new LinkedHashMap<>();
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

        for (MaterializedSeed seed : seeds) {
            var perSeedRelationCounts = new EnumMap<RelationFamily, Integer>(RelationFamily.class);
            var adjacentLinks =
                    graphOps.listAdjacentItemLinks(
                            context.memoryId(), List.of(seed.item().id()), SUPPORTED_LINK_TYPES);
            for (ItemLink link : adjacentLinks) {
                if (link.strength() == null || link.strength() < graphSettings.minLinkStrength()) {
                    continue;
                }

                long neighborItemId =
                        Objects.equals(link.sourceItemId(), seed.item().id())
                                ? link.targetItemId()
                                : link.sourceItemId();
                if (directIds.contains(String.valueOf(neighborItemId))) {
                    overlapItemIds.add(neighborItemId);
                    continue;
                }

                var family = toRelationFamily(link.linkType());
                int offered = perSeedRelationCounts.getOrDefault(family, 0);
                if (offered >= maxNeighborsPerSeed(graphSettings, family)) {
                    continue;
                }
                perSeedRelationCounts.put(family, offered + 1);
                linkExpansionCount++;

                var incoming =
                        new GraphCandidate(
                                neighborItemId,
                                family,
                                seed.item().id(),
                                seed.seedRelevance(),
                                link.strength(),
                                scoreCandidate(
                                        family, seed.seedRelevance(), link.strength(), 1.0d));
                rawCandidates.merge(
                        neighborItemId,
                        incoming,
                        (existing, replacement) ->
                                replacement.provisionalScore() > existing.provisionalScore()
                                        ? replacement
                                        : existing);
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
                    if (directIds.contains(String.valueOf(mention.itemId()))) {
                        overlapItemIds.add(mention.itemId());
                        continue;
                    }
                    if (entitySiblingCount >= graphSettings.maxEntitySiblingItemsPerSeed()) {
                        break;
                    }
                    entitySiblingCount++;
                    entityExpansionCount++;

                    var incoming =
                            new GraphCandidate(
                                    mention.itemId(),
                                    RelationFamily.ENTITY_SIBLING,
                                    seed.item().id(),
                                    seed.seedRelevance(),
                                    mention.confidence().doubleValue(),
                                    scoreCandidate(
                                            RelationFamily.ENTITY_SIBLING,
                                            seed.seedRelevance(),
                                            mention.confidence().doubleValue(),
                                            1.0d));
                    rawCandidates.merge(
                            mention.itemId(),
                            incoming,
                            (existing, replacement) ->
                                    replacement.provisionalScore() > existing.provisionalScore()
                                            ? replacement
                                            : existing);
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
                    entry.getKey(),
                    entry.getValue()
                            .withResolvedItem(
                                    item,
                                    scoreCandidate(
                                            entry.getValue().family(),
                                            entry.getValue().seedRelevance(),
                                            entry.getValue().relationStrength(),
                                            TimeDecay.factor(
                                                    item.occurredAt(),
                                                    context,
                                                    config.scoring()))));
        }

        return new GraphCandidateBundle(
                filteredCandidates,
                linkExpansionCount,
                entityExpansionCount,
                overlapItemIds.size(),
                skippedOverFanoutEntityCount);
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

    private int countDisplacedDirectItems(
            List<ScoredResult> directItems, List<ScoredResult> finalItems, int observationWindow) {
        int window = Math.min(observationWindow, Math.min(directItems.size(), finalItems.size()));
        var originalTopDirectIds =
                directItems.stream()
                        .limit(window)
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var fusedTopIds =
                finalItems.stream()
                        .limit(window)
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toSet());
        originalTopDirectIds.removeIf(fusedTopIds::contains);
        return originalTopDirectIds.size();
    }

    private RelationFamily toRelationFamily(ItemLinkType linkType) {
        return switch (linkType) {
            case SEMANTIC -> RelationFamily.SEMANTIC;
            case TEMPORAL -> RelationFamily.TEMPORAL;
            case CAUSAL -> RelationFamily.CAUSAL;
        };
    }

    private int maxNeighborsPerSeed(RetrievalGraphSettings graphSettings, RelationFamily family) {
        return switch (family) {
            case SEMANTIC -> graphSettings.maxSemanticNeighborsPerSeed();
            case TEMPORAL -> graphSettings.maxTemporalNeighborsPerSeed();
            case CAUSAL -> graphSettings.maxCausalNeighborsPerSeed();
            case ENTITY_SIBLING -> graphSettings.maxEntitySiblingItemsPerSeed();
        };
    }

    private double scoreCandidate(
            RelationFamily family,
            double seedRelevance,
            double relationStrength,
            double recencyAdjustment) {
        double relationWeight = DEFAULT_RELATION_WEIGHTS.getOrDefault(family, 1.0d);
        return seedRelevance * relationWeight * relationStrength * recencyAdjustment;
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

    private record GraphCandidate(
            long itemId,
            RelationFamily family,
            long seedItemId,
            double seedRelevance,
            double relationStrength,
            double provisionalScore,
            String content,
            java.time.Instant occurredAt) {

        private GraphCandidate(
                long itemId,
                RelationFamily family,
                long seedItemId,
                double seedRelevance,
                double relationStrength,
                double provisionalScore) {
            this(
                    itemId,
                    family,
                    seedItemId,
                    seedRelevance,
                    relationStrength,
                    provisionalScore,
                    null,
                    null);
        }

        private GraphCandidate withResolvedItem(MemoryItem item, double resolvedScore) {
            return new GraphCandidate(
                    itemId,
                    family,
                    seedItemId,
                    seedRelevance,
                    relationStrength,
                    resolvedScore,
                    item.content(),
                    item.occurredAt());
        }

        private double score() {
            return provisionalScore;
        }

        private ScoredResult toScoredResult() {
            return new ScoredResult(
                    ScoredResult.SourceType.ITEM,
                    String.valueOf(itemId),
                    content == null ? "" : content,
                    0f,
                    provisionalScore,
                    occurredAt);
        }
    }

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
