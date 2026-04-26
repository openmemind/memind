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

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphQueryBudgetContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * One-hop graph enrichment for direct retrieval candidates.
 */
public final class DefaultRetrievalGraphAssistant implements RetrievalGraphAssistant {

    private final GraphExpansionEngine graphExpansionEngine;

    public DefaultRetrievalGraphAssistant(MemoryStore store) {
        this(new GraphExpansionEngine(store));
    }

    public DefaultRetrievalGraphAssistant(GraphExpansionEngine graphExpansionEngine) {
        this.graphExpansionEngine =
                Objects.requireNonNull(graphExpansionEngine, "graphExpansionEngine");
    }

    @Override
    public Mono<RetrievalGraphAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems) {
        boolean enabled = graphSettings != null && graphSettings.enabled();
        if (!enabled || directItems == null || directItems.isEmpty()) {
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
        var graphResult = graphExpansionEngine.expand(context, config, graphSettings, directItems);
        if (graphResult.degraded()) {
            return RetrievalGraphAssistResult.degraded(
                    directItems, graphResult.enabled(), graphResult.timedOut());
        }
        if (graphResult.seedCount() == 0 && graphResult.graphItems().isEmpty()) {
            return RetrievalGraphAssistResult.directOnly(directItems, graphResult.enabled());
        }

        var directIds =
                directItems.stream()
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var finalItems =
                switch (graphSettings.mode()) {
                    case ASSIST ->
                            fuseAssistMode(graphSettings, directItems, graphResult.graphItems());
                    case EXPAND ->
                            fuseExpandMode(graphSettings, directItems, graphResult.graphItems());
                };
        int admittedGraphCandidateCount =
                (int)
                        finalItems.stream()
                                .map(ScoredResult::sourceId)
                                .filter(sourceId -> !directIds.contains(sourceId))
                                .count();

        return new RetrievalGraphAssistResult(
                finalItems,
                new RetrievalGraphAssistResult.GraphAssistStats(
                        graphResult.enabled(),
                        graphResult.degraded(),
                        graphResult.timedOut(),
                        graphResult.seedCount(),
                        graphResult.linkExpansionCount(),
                        graphResult.entityExpansionCount(),
                        graphResult.dedupedCandidateCount(),
                        admittedGraphCandidateCount,
                        countDisplacedDirectItems(directItems, finalItems, directItems.size()),
                        graphResult.overlapCount(),
                        graphResult.skippedOverFanoutEntityCount()));
    }

    private List<ScoredResult> fuseAssistMode(
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems,
            List<ScoredResult> rankedGraph) {
        int pinned = Math.min(graphSettings.protectDirectTopK(), directItems.size());
        var pinnedPrefix = directItems.subList(0, pinned);
        var pinnedIds =
                pinnedPrefix.stream()
                        .map(ScoredResult::sourceId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var directTail = directItems.subList(pinned, directItems.size());
        var graphTail =
                rankedGraph.stream()
                        .filter(candidate -> !pinnedIds.contains(candidate.sourceId()))
                        .limit(graphSettings.maxExpandedItems())
                        .toList();
        var fusedTail =
                buildFusionCandidates(directTail, graphTail, graphSettings.graphChannelWeight())
                        .values()
                        .stream()
                        .sorted(GraphFusionCandidate.ORDER)
                        .map(GraphFusionCandidate::result)
                        .toList();
        return Stream.concat(pinnedPrefix.stream(), fusedTail.stream()).toList();
    }

    private List<ScoredResult> fuseExpandMode(
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems,
            List<ScoredResult> rankedGraph) {
        int expandLimit = Math.max(directItems.size(), graphSettings.maxExpandedItems());
        return buildFusionCandidates(
                        directItems,
                        rankedGraph.stream().limit(graphSettings.maxExpandedItems()).toList(),
                        graphSettings.graphChannelWeight())
                .values()
                .stream()
                .sorted(GraphFusionCandidate.ORDER)
                .limit(expandLimit)
                .map(GraphFusionCandidate::result)
                .toList();
    }

    private Map<Long, GraphFusionCandidate> buildFusionCandidates(
            List<ScoredResult> directItems,
            List<ScoredResult> rankedGraph,
            double graphChannelWeight) {
        Map<Long, GraphFusionCandidate> candidates = new LinkedHashMap<>();
        for (int directRank = 0; directRank < directItems.size(); directRank++) {
            var direct = directItems.get(directRank);
            long itemId = requireItemId(direct.sourceId());
            candidates.put(
                    itemId,
                    new GraphFusionCandidate(
                            itemId, true, directRank, direct.finalScore(), 0.0d, direct));
        }

        for (ScoredResult graphCandidate : rankedGraph) {
            long itemId = requireItemId(graphCandidate.sourceId());
            if (candidates.containsKey(itemId)) {
                continue;
            }
            double normalizedGraphScore = graphCandidate.finalScore();
            double fusedScore = graphOnlyScore(normalizedGraphScore, graphChannelWeight);
            candidates.put(
                    itemId,
                    new GraphFusionCandidate(
                            itemId,
                            false,
                            Integer.MAX_VALUE,
                            fusedScore,
                            normalizedGraphScore,
                            rescore(graphCandidate, fusedScore)));
        }
        return candidates;
    }

    private double graphOnlyScore(double normalizedGraphScore, double graphChannelWeight) {
        return normalizedGraphScore * Math.max(0.0d, Math.min(graphChannelWeight, 1.0d));
    }

    private long requireItemId(String sourceId) {
        return parseItemId(sourceId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "graph assist requires numeric item ids: " + sourceId));
    }

    private ScoredResult rescore(ScoredResult result, double fusedScore) {
        return new ScoredResult(
                result.sourceType(),
                result.sourceId(),
                result.text(),
                result.vectorScore(),
                fusedScore,
                result.occurredAt());
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

    private Optional<Long> parseItemId(String sourceId) {
        try {
            return Optional.of(Long.parseLong(sourceId));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
