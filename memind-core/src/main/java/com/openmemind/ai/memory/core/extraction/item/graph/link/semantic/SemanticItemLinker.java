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
package com.openmemind.ai.memory.core.extraction.item.graph.link.semantic;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ItemEmbeddingTextResolver;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorBatchSearchException;
import com.openmemind.ai.memory.core.vector.VectorBatchSearchResult;
import com.openmemind.ai.memory.core.vector.VectorSearchRequest;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Builds bounded semantic item links from vector similarity search.
 */
public class SemanticItemLinker {

    static final int SEMANTIC_RESOLVE_BATCH_SIZE = 256;
    static final int SEMANTIC_UPSERT_BATCH_SIZE = 256;
    static final int SAME_BATCH_TARGET_TILE_SIZE = 512;

    private final ItemOperations itemOperations;
    private final GraphOperations graphOperations;
    private final MemoryVector vector;
    private final ItemGraphOptions options;

    public SemanticItemLinker(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            MemoryVector vector,
            ItemGraphOptions options) {
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.vector = Objects.requireNonNull(vector, "vector");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Mono<SemanticLinkingStats> link(MemoryId memoryId, List<MemoryItem> items) {
        return plan(memoryId, items)
                .flatMap(
                        plan ->
                                persistLinks(memoryId, plan.links())
                                        .map(persistStats -> plan.withStats(persistStats).stats()));
    }

    public Mono<SemanticLinkingPlan> plan(MemoryId memoryId, List<MemoryItem> items) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.just(SemanticLinkingPlan.empty());
        }

        var batchItems = List.copyOf(items);
        var batchVectorIds =
                batchItems.stream()
                        .map(MemoryItem::vectorId)
                        .filter(vectorId -> vectorId != null && !vectorId.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        long intraBatchStartedAt = System.nanoTime();
        return acquireBatchEmbeddingCache(batchItems)
                .map(
                        cache ->
                                SameBatchBatchState.enabled(
                                        cache, elapsedMillis(intraBatchStartedAt)))
                .onErrorResume(
                        error ->
                                Mono.just(
                                        SameBatchBatchState.disabled(
                                                elapsedMillis(intraBatchStartedAt),
                                                !isUnsupportedEmbeddingCapability(error))))
                .flatMap(
                        sameBatchState ->
                                Flux.fromIterable(
                                                partition(
                                                        batchItems,
                                                        options.semanticSourceWindowSize()))
                                        .concatMap(
                                                window ->
                                                        planWindow(
                                                                        memoryId,
                                                                        batchItems,
                                                                        window,
                                                                        batchVectorIds,
                                                                        sameBatchState)
                                                                .onErrorResume(
                                                                        error ->
                                                                                Mono.just(
                                                                                        SemanticLinkingPlan
                                                                                                .windowFailure())))
                                        .reduce(
                                                sameBatchState.basePlan(),
                                                SemanticLinkingPlan::plus)
                                        .defaultIfEmpty(sameBatchState.basePlan()));
    }

    private Mono<BatchEmbeddingCache> acquireBatchEmbeddingCache(List<MemoryItem> batchItems) {
        var indexedItems =
                batchItems.stream()
                        .filter(item -> item.vectorId() != null && !item.vectorId().isBlank())
                        .toList();
        var orderedVectorIds = indexedItems.stream().map(MemoryItem::vectorId).toList();

        if (orderedVectorIds.isEmpty()) {
            return Mono.just(BatchEmbeddingCache.empty());
        }

        return vector.fetchEmbeddings(orderedVectorIds)
                .map(FetchEmbeddingsAttempt::success)
                .onErrorResume(error -> Mono.just(FetchEmbeddingsAttempt.failed()))
                .flatMap(
                        attempt ->
                                attempt.rebuildRequested()
                                        ? rebuildEmbeddingCache(indexedItems)
                                        : completeEmbeddingCache(indexedItems, attempt.fetched()));
    }

    private Mono<BatchEmbeddingCache> completeEmbeddingCache(
            List<MemoryItem> indexedItems, Map<String, List<Float>> fetched) {
        var resolved = new LinkedHashMap<String, List<Float>>();
        var missingItems = new ArrayList<MemoryItem>();

        for (var item : indexedItems) {
            var embedding = fetched.get(item.vectorId());
            if (embedding == null || embedding.isEmpty()) {
                missingItems.add(item);
            } else {
                resolved.put(item.vectorId(), List.copyOf(embedding));
            }
        }

        if (missingItems.isEmpty()) {
            return Mono.just(new BatchEmbeddingCache(resolved));
        }

        return vector.embedAll(
                        missingItems.stream().map(ItemEmbeddingTextResolver::resolve).toList())
                .map(
                        recomputed -> {
                            if (recomputed.size() != missingItems.size()) {
                                throw new IllegalStateException(
                                        "embedAll result size mismatch for missing batch"
                                                + " embeddings");
                            }
                            for (int index = 0; index < missingItems.size(); index++) {
                                resolved.put(
                                        missingItems.get(index).vectorId(),
                                        List.copyOf(recomputed.get(index)));
                            }
                            return new BatchEmbeddingCache(resolved);
                        });
    }

    private Mono<BatchEmbeddingCache> rebuildEmbeddingCache(List<MemoryItem> indexedItems) {
        return vector.embedAll(
                        indexedItems.stream().map(ItemEmbeddingTextResolver::resolve).toList())
                .map(
                        embeddings -> {
                            if (embeddings.size() != indexedItems.size()) {
                                throw new IllegalStateException(
                                        "embedAll result size mismatch for full batch rebuild");
                            }
                            var resolved = new LinkedHashMap<String, List<Float>>();
                            for (int index = 0; index < indexedItems.size(); index++) {
                                resolved.put(
                                        indexedItems.get(index).vectorId(),
                                        List.copyOf(embeddings.get(index)));
                            }
                            return new BatchEmbeddingCache(resolved);
                        });
    }

    private Mono<SemanticLinkingPlan> planWindow(
            MemoryId memoryId,
            List<MemoryItem> fullBatch,
            List<MemoryItem> window,
            Set<String> batchVectorIds,
            SameBatchBatchState sameBatchState) {
        long searchStartedAt = System.nanoTime();
        return collectSearchHits(memoryId, window)
                .flatMap(
                        searchOutcome -> {
                            long searchDurationMs = elapsedMillis(searchStartedAt);
                            long resolveStartedAt = System.nanoTime();
                            return resolveCandidates(memoryId, searchOutcome)
                                    .flatMap(
                                            resolveOutcome -> {
                                                long resolveDurationMs =
                                                        elapsedMillis(resolveStartedAt);
                                                var sameBatchWindow =
                                                        sameBatchState
                                                                .computeWindowCandidatesSafely(
                                                                        fullBatch, window, options);
                                                var normalized =
                                                        normalizeWindow(
                                                                memoryId,
                                                                searchOutcome.sources(),
                                                                resolveOutcome.resolvedByVectorId(),
                                                                sameBatchWindow
                                                                        .candidatesBySourceItemId(),
                                                                batchVectorIds);
                                                return Mono.just(
                                                        new SemanticLinkingPlan(
                                                                normalized.links(),
                                                                sameBatchWindow
                                                                        .stats()
                                                                        .plus(searchOutcome.stats())
                                                                        .plus(
                                                                                resolveOutcome
                                                                                        .stats())
                                                                        .plus(normalized.stats())
                                                                        .plus(
                                                                                SemanticLinkingStats
                                                                                        .windowProcessed(
                                                                                                searchDurationMs,
                                                                                                resolveDurationMs,
                                                                                                0L))));
                                            });
                        });
    }

    private Mono<SearchOutcome> collectSearchHits(MemoryId memoryId, List<MemoryItem> window) {
        int requestedTopK =
                options.maxSemanticLinksPerItem() + options.semanticSearchHeadroom() + 1;
        int concurrency = Math.max(1, options.semanticLinkConcurrency());

        var plans =
                window.stream()
                        .map(item -> new SearchPlan(item, ItemEmbeddingTextResolver.resolve(item)))
                        .toList();
        var searchablePlans = plans.stream().filter(plan -> !plan.query().isBlank()).toList();

        if (searchablePlans.isEmpty()) {
            return Mono.just(SearchOutcome.blankWindow(plans));
        }

        var requests =
                searchablePlans.stream()
                        .map(
                                plan ->
                                        new VectorSearchRequest(
                                                plan.query(),
                                                requestedTopK,
                                                options.semanticMinScore(),
                                                Map.of()))
                        .toList();

        return vector.searchBatch(memoryId, requests, concurrency)
                .flatMap(bundle -> mapBatchSuccess(plans, searchablePlans, bundle))
                .onErrorResume(
                        error ->
                                fallbackSearchWindow(
                                        memoryId,
                                        plans,
                                        searchablePlans,
                                        requestedTopK,
                                        concurrency,
                                        error));
    }

    private Mono<SearchOutcome> mapBatchSuccess(
            List<SearchPlan> allPlans,
            List<SearchPlan> searchablePlans,
            VectorBatchSearchResult bundle) {
        if (bundle.results().size() != searchablePlans.size()) {
            return Mono.error(
                    new VectorBatchSearchException(
                            "Ordered batch result size mismatch",
                            new IllegalArgumentException("Ordered batch result size mismatch"),
                            bundle.invocationCount()));
        }

        var indexedResults = new HashMap<MemoryItem, List<VectorSearchResult>>();
        int totalHitCount = 0;
        for (int index = 0; index < searchablePlans.size(); index++) {
            var results = List.copyOf(bundle.results().get(index));
            indexedResults.put(searchablePlans.get(index).item(), results);
            totalHitCount += results.size();
        }

        var sources =
                allPlans.stream()
                        .map(
                                plan ->
                                        new SourceSearchOutcome(
                                                plan.item(),
                                                indexedResults.getOrDefault(
                                                        plan.item(), List.of())))
                        .toList();

        return Mono.just(
                new SearchOutcome(
                        sources,
                        SemanticLinkingStats.searchWindow(
                                searchablePlans.size(),
                                bundle.invocationCount(),
                                totalHitCount,
                                0,
                                false)));
    }

    private Mono<SearchOutcome> fallbackSearchWindow(
            MemoryId memoryId,
            List<SearchPlan> allPlans,
            List<SearchPlan> searchablePlans,
            int requestedTopK,
            int concurrency,
            Throwable error) {
        int attemptedInvocationCount =
                error instanceof VectorBatchSearchException typed
                        ? typed.attemptedInvocationCount()
                        : 0;

        return Flux.fromIterable(searchablePlans)
                .flatMapSequential(
                        plan ->
                                vector.search(
                                                memoryId,
                                                plan.query(),
                                                requestedTopK,
                                                options.semanticMinScore(),
                                                null)
                                        .collectList()
                                        .map(
                                                results ->
                                                        FallbackSourceResult.success(
                                                                plan.item(), results))
                                        .onErrorResume(
                                                fallbackError ->
                                                        Mono.just(
                                                                FallbackSourceResult.failed(
                                                                        plan.item()))),
                        concurrency,
                        1)
                .collectList()
                .map(
                        fallbackResults -> {
                            var indexedResults =
                                    new HashMap<MemoryItem, List<VectorSearchResult>>();
                            int totalHitCount = 0;
                            for (var fallbackResult : fallbackResults) {
                                indexedResults.put(fallbackResult.item(), fallbackResult.results());
                                totalHitCount += fallbackResult.results().size();
                            }
                            var sources =
                                    allPlans.stream()
                                            .map(
                                                    plan ->
                                                            new SourceSearchOutcome(
                                                                    plan.item(),
                                                                    indexedResults.getOrDefault(
                                                                            plan.item(),
                                                                            List.of())))
                                            .toList();
                            return new SearchOutcome(
                                    sources,
                                    SemanticLinkingStats.searchWindow(
                                            searchablePlans.size(),
                                            attemptedInvocationCount + searchablePlans.size(),
                                            totalHitCount,
                                            searchablePlans.size(),
                                            true));
                        });
    }

    private Mono<ResolveOutcome> resolveCandidates(MemoryId memoryId, SearchOutcome searchOutcome) {
        var candidateVectorIds =
                searchOutcome.sources().stream()
                        .flatMap(source -> source.results().stream())
                        .map(VectorSearchResult::vectorId)
                        .filter(vectorId -> vectorId != null && !vectorId.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();

        if (candidateVectorIds.isEmpty()) {
            return Mono.just(ResolveOutcome.empty());
        }

        return Flux.fromIterable(chunk(candidateVectorIds, SEMANTIC_RESOLVE_BATCH_SIZE))
                .concatMap(
                        vectorIdChunk ->
                                Mono.fromSupplier(
                                                () ->
                                                        itemOperations.getItemsByVectorIds(
                                                                memoryId, vectorIdChunk))
                                        .map(ResolveOutcome::success)
                                        .onErrorResume(error -> Mono.just(ResolveOutcome.failed())))
                .reduce(ResolveOutcome.empty(), ResolveOutcome::plus)
                .map(outcome -> outcome.allChunksFailed() ? outcome.withWindowFailure() : outcome);
    }

    private static Map<Long, List<CandidateHit>> computeWindowCandidates(
            List<MemoryItem> fullBatch,
            List<MemoryItem> window,
            BatchEmbeddingCache cache,
            ItemGraphOptions options) {
        var candidatesBySourceItemId = new LinkedHashMap<Long, List<CandidateHit>>();
        int perSourcePreCap =
                options.maxSemanticLinksPerItem() + options.semanticSearchHeadroom() + 1;
        var batchTargets =
                fullBatch.stream()
                        .filter(
                                item ->
                                        item.vectorId() != null
                                                && !item.vectorId().isBlank()
                                                && cache.contains(item.vectorId()))
                        .toList();

        for (var source : window) {
            var sourceVectorId = source.vectorId();
            if (sourceVectorId == null
                    || sourceVectorId.isBlank()
                    || !cache.contains(sourceVectorId)) {
                continue;
            }

            var candidates = new ArrayList<CandidateHit>();
            for (var targetTile : chunk(batchTargets, SAME_BATCH_TARGET_TILE_SIZE)) {
                for (var target : targetTile) {
                    if (Objects.equals(source.id(), target.id())) {
                        continue;
                    }
                    float score =
                            cosineSimilarity(
                                    cache.embedding(sourceVectorId),
                                    cache.embedding(target.vectorId()));
                    if (score >= options.semanticMinScore()) {
                        candidates.add(new CandidateHit(target, target.vectorId(), score));
                    }
                }
            }

            if (candidates.isEmpty()) {
                continue;
            }

            var preCappedCandidates =
                    candidates.stream()
                            .sorted(
                                    Comparator.comparingDouble(CandidateHit::score)
                                            .reversed()
                                            .thenComparing(CandidateHit::vectorId)
                                            .thenComparing(hit -> hit.item().id()))
                            .limit(perSourcePreCap)
                            .toList();
            candidatesBySourceItemId.put(source.id(), preCappedCandidates);
        }

        return Map.copyOf(candidatesBySourceItemId);
    }

    private static float cosineSimilarity(List<Float> left, List<Float> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("embedding dimension mismatch");
        }

        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }

        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            throw new IllegalArgumentException("zero-length embedding vector");
        }
        return (float) (dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private WindowNormalization normalizeWindow(
            MemoryId memoryId,
            List<SourceSearchOutcome> sources,
            Map<String, MemoryItem> resolvedByVectorId,
            Map<Long, List<CandidateHit>> sameBatchCandidatesBySourceItemId,
            Set<String> batchVectorIds) {
        var allLinks = new ArrayList<ItemLink>();
        SemanticLinkingStats stats = SemanticLinkingStats.empty();

        for (var source : sources) {
            Map<String, CandidateHit> mergedByVectorId = new LinkedHashMap<>();
            source.results().stream()
                    .filter(result -> result.vectorId() != null && !result.vectorId().isBlank())
                    .forEach(
                            result -> {
                                var candidate = resolvedByVectorId.get(result.vectorId());
                                if (candidate == null
                                        || Objects.equals(candidate.id(), source.item().id())) {
                                    return;
                                }
                                mergedByVectorId.merge(
                                        result.vectorId(),
                                        new CandidateHit(
                                                candidate, result.vectorId(), result.score()),
                                        (left, right) ->
                                                left.score() >= right.score() ? left : right);
                            });
            sameBatchCandidatesBySourceItemId
                    .getOrDefault(source.item().id(), List.of())
                    .forEach(
                            candidate ->
                                    mergedByVectorId.merge(
                                            candidate.vectorId(),
                                            candidate,
                                            (left, right) ->
                                                    left.score() >= right.score() ? left : right));

            Instant createdAt = resolveLinkTimestamp(source.item());
            var normalizedCandidates =
                    mergedByVectorId.values().stream()
                            .sorted(
                                    Comparator.comparingDouble(CandidateHit::score)
                                            .reversed()
                                            .thenComparing(CandidateHit::vectorId)
                                            .thenComparing(hit -> hit.item().id()))
                            .toList();

            int sameBatchHitCount =
                    (int)
                            normalizedCandidates.stream()
                                    .limit(options.maxSemanticLinksPerItem())
                                    .map(CandidateHit::vectorId)
                                    .filter(batchVectorIds::contains)
                                    .count();

            var links =
                    normalizedCandidates.stream()
                            .limit(options.maxSemanticLinksPerItem())
                            .map(
                                    hit ->
                                            new ItemLink(
                                                    memoryId.toIdentifier(),
                                                    source.item().id(),
                                                    hit.item().id(),
                                                    ItemLinkType.SEMANTIC,
                                                    Double.valueOf(hit.score()),
                                                    Map.of("source", "vector_search"),
                                                    createdAt))
                            .toList();

            allLinks.addAll(links);
            stats =
                    stats.plus(
                            SemanticLinkingStats.createdLinks(
                                    normalizedCandidates.size(), links.size(), sameBatchHitCount));
        }

        return new WindowNormalization(List.copyOf(allLinks), stats);
    }

    private Mono<SemanticLinkingStats> persistLinks(MemoryId memoryId, List<ItemLink> links) {
        if (links.isEmpty()) {
            return Mono.just(SemanticLinkingStats.empty());
        }

        var orderedLinks =
                links.stream()
                        .sorted(
                                Comparator.comparing(ItemLink::sourceItemId)
                                        .thenComparing(ItemLink::targetItemId)
                                        .thenComparing(ItemLink::linkType))
                        .toList();

        return Flux.fromIterable(chunk(orderedLinks, SEMANTIC_UPSERT_BATCH_SIZE))
                .concatMap(
                        linkChunk ->
                                Mono.fromRunnable(
                                                () ->
                                                        graphOperations.upsertItemLinks(
                                                                memoryId, linkChunk))
                                        .thenReturn(SemanticLinkingStats.upsertBatchSuccess())
                                        .onErrorResume(
                                                error ->
                                                        Mono.just(
                                                                SemanticLinkingStats
                                                                        .upsertBatchFailure())))
                .reduce(SemanticLinkingStats.empty(), SemanticLinkingStats::plus);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static boolean isUnsupportedEmbeddingCapability(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnsupportedOperationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record FetchEmbeddingsAttempt(
            Map<String, List<Float>> fetched, boolean rebuildRequested) {

        private FetchEmbeddingsAttempt {
            fetched = Map.copyOf(fetched);
        }

        static FetchEmbeddingsAttempt success(Map<String, List<Float>> fetched) {
            return new FetchEmbeddingsAttempt(fetched, false);
        }

        static FetchEmbeddingsAttempt failed() {
            return new FetchEmbeddingsAttempt(Map.of(), true);
        }
    }

    private record BatchEmbeddingCache(Map<String, List<Float>> embeddingsByVectorId) {

        private BatchEmbeddingCache {
            var copied = new LinkedHashMap<String, List<Float>>();
            embeddingsByVectorId.forEach(
                    (vectorId, embedding) -> copied.put(vectorId, List.copyOf(embedding)));
            embeddingsByVectorId = Map.copyOf(copied);
        }

        static BatchEmbeddingCache empty() {
            return new BatchEmbeddingCache(Map.of());
        }

        boolean contains(String vectorId) {
            return embeddingsByVectorId.containsKey(vectorId);
        }

        List<Float> embedding(String vectorId) {
            return embeddingsByVectorId.get(vectorId);
        }
    }

    private record SameBatchBatchState(
            BatchEmbeddingCache cache, SemanticLinkingStats baseStats, boolean enabled) {

        static SameBatchBatchState enabled(BatchEmbeddingCache cache, long phaseDurationMs) {
            return new SameBatchBatchState(
                    cache,
                    SemanticLinkingStats.intraBatch(0, phaseDurationMs, false),
                    !cache.embeddingsByVectorId().isEmpty());
        }

        static SameBatchBatchState disabled(long phaseDurationMs, boolean degraded) {
            return new SameBatchBatchState(
                    BatchEmbeddingCache.empty(),
                    SemanticLinkingStats.intraBatch(0, phaseDurationMs, degraded),
                    false);
        }

        SemanticLinkingPlan basePlan() {
            return new SemanticLinkingPlan(List.of(), baseStats);
        }

        SameBatchWindowOutcome computeWindowCandidatesSafely(
                List<MemoryItem> fullBatch, List<MemoryItem> window, ItemGraphOptions options) {
            if (!enabled) {
                return SameBatchWindowOutcome.empty();
            }

            long startedAt = System.nanoTime();
            try {
                var candidatesBySourceItemId =
                        computeWindowCandidates(fullBatch, window, cache, options);
                int candidateCount =
                        candidatesBySourceItemId.values().stream().mapToInt(List::size).sum();
                return new SameBatchWindowOutcome(
                        candidatesBySourceItemId,
                        SemanticLinkingStats.intraBatch(
                                candidateCount, elapsedMillis(startedAt), false));
            } catch (RuntimeException error) {
                return new SameBatchWindowOutcome(
                        Map.of(),
                        SemanticLinkingStats.intraBatch(0, elapsedMillis(startedAt), true));
            }
        }
    }

    private record SameBatchWindowOutcome(
            Map<Long, List<CandidateHit>> candidatesBySourceItemId, SemanticLinkingStats stats) {

        private SameBatchWindowOutcome {
            var copied = new LinkedHashMap<Long, List<CandidateHit>>();
            candidatesBySourceItemId.forEach(
                    (sourceItemId, candidates) ->
                            copied.put(sourceItemId, List.copyOf(candidates)));
            candidatesBySourceItemId = Map.copyOf(copied);
            stats = stats == null ? SemanticLinkingStats.empty() : stats;
        }

        static SameBatchWindowOutcome empty() {
            return new SameBatchWindowOutcome(Map.of(), SemanticLinkingStats.empty());
        }
    }

    private static <T> List<List<T>> partition(List<T> items, int windowSize) {
        if (items.isEmpty()) {
            return List.of();
        }
        var partitions = new ArrayList<List<T>>();
        for (int index = 0; index < items.size(); index += windowSize) {
            partitions.add(items.subList(index, Math.min(items.size(), index + windowSize)));
        }
        return List.copyOf(partitions);
    }

    private static <T> List<List<T>> chunk(List<T> items, int chunkSize) {
        if (items.isEmpty()) {
            return List.of();
        }
        var chunks = new ArrayList<List<T>>();
        for (int index = 0; index < items.size(); index += chunkSize) {
            chunks.add(items.subList(index, Math.min(items.size(), index + chunkSize)));
        }
        return List.copyOf(chunks);
    }

    private static Instant resolveLinkTimestamp(MemoryItem item) {
        if (item.createdAt() != null) {
            return item.createdAt();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        return Instant.EPOCH;
    }

    private record SearchPlan(MemoryItem item, String query) {}

    private record SourceSearchOutcome(MemoryItem item, List<VectorSearchResult> results) {

        private SourceSearchOutcome {
            results = List.copyOf(results);
        }
    }

    private record SearchOutcome(List<SourceSearchOutcome> sources, SemanticLinkingStats stats) {

        private SearchOutcome {
            sources = List.copyOf(sources);
        }

        static SearchOutcome blankWindow(List<SearchPlan> plans) {
            return new SearchOutcome(
                    plans.stream()
                            .map(plan -> new SourceSearchOutcome(plan.item(), List.of()))
                            .toList(),
                    SemanticLinkingStats.empty());
        }
    }

    private record FallbackSourceResult(
            MemoryItem item, List<VectorSearchResult> results, boolean failed) {

        static FallbackSourceResult success(MemoryItem item, List<VectorSearchResult> results) {
            return new FallbackSourceResult(item, List.copyOf(results), false);
        }

        static FallbackSourceResult failed(MemoryItem item) {
            return new FallbackSourceResult(item, List.of(), true);
        }
    }

    private record ResolveOutcome(
            Map<String, MemoryItem> resolvedByVectorId,
            SemanticLinkingStats stats,
            int attemptedChunkCount,
            int successfulChunkCount) {

        static ResolveOutcome empty() {
            return new ResolveOutcome(Map.of(), SemanticLinkingStats.empty(), 0, 0);
        }

        static ResolveOutcome success(List<MemoryItem> items) {
            var resolved =
                    items.stream()
                            .filter(item -> item.vectorId() != null && !item.vectorId().isBlank())
                            .collect(
                                    Collectors.toMap(
                                            MemoryItem::vectorId,
                                            Function.identity(),
                                            (left, right) -> left,
                                            LinkedHashMap::new));
            return new ResolveOutcome(
                    Map.copyOf(resolved),
                    SemanticLinkingStats.resolveSuccess(resolved.size()),
                    1,
                    1);
        }

        static ResolveOutcome failed() {
            return new ResolveOutcome(Map.of(), SemanticLinkingStats.resolveChunkFailure(), 1, 0);
        }

        ResolveOutcome plus(ResolveOutcome other) {
            var merged = new LinkedHashMap<String, MemoryItem>(resolvedByVectorId);
            merged.putAll(other.resolvedByVectorId);
            return new ResolveOutcome(
                    Map.copyOf(merged),
                    stats.plus(other.stats),
                    attemptedChunkCount + other.attemptedChunkCount,
                    successfulChunkCount + other.successfulChunkCount);
        }

        boolean allChunksFailed() {
            return attemptedChunkCount > 0 && successfulChunkCount == 0;
        }

        ResolveOutcome withWindowFailure() {
            return new ResolveOutcome(
                    resolvedByVectorId,
                    stats.plus(SemanticLinkingStats.windowMarkedFailed()),
                    attemptedChunkCount,
                    successfulChunkCount);
        }
    }

    public record SemanticLinkingStats(
            int searchRequestCount,
            int searchInvocationCount,
            int searchHitCount,
            int resolvedCandidateCount,
            int createdLinkCount,
            int upsertBatchCount,
            int sourceWindowCount,
            int failedResolveChunkCount,
            int failedWindowCount,
            int failedUpsertBatchCount,
            int sameBatchHitCount,
            int searchFallbackCount,
            int intraBatchCandidateCount,
            long searchPhaseDurationMs,
            long resolvePhaseDurationMs,
            long upsertPhaseDurationMs,
            long intraBatchPhaseDurationMs,
            boolean degraded) {

        public static SemanticLinkingStats empty() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, false);
        }

        public static SemanticLinkingStats stageFailure() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, true);
        }

        static SemanticLinkingStats searchWindow(
                int searchRequestCount,
                int searchInvocationCount,
                int searchHitCount,
                int searchFallbackCount,
                boolean degraded) {
            return new SemanticLinkingStats(
                    searchRequestCount,
                    searchInvocationCount,
                    searchHitCount,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    searchFallbackCount,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    degraded);
        }

        static SemanticLinkingStats resolveSuccess(int resolvedCount) {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, false);
        }

        static SemanticLinkingStats resolveChunkFailure() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, true);
        }

        static SemanticLinkingStats createdLinks(
                int resolvedCandidateCount, int createdLinkCount, int sameBatchHitCount) {
            return new SemanticLinkingStats(
                    0,
                    0,
                    0,
                    resolvedCandidateCount,
                    createdLinkCount,
                    0,
                    0,
                    0,
                    0,
                    0,
                    sameBatchHitCount,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    false);
        }

        static SemanticLinkingStats upsertBatchSuccess() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, false);
        }

        static SemanticLinkingStats upsertBatchFailure() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0L, 0L, 0L, 0L, true);
        }

        static SemanticLinkingStats intraBatch(
                int candidateCount, long phaseDurationMs, boolean degraded) {
            return new SemanticLinkingStats(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    candidateCount,
                    0L,
                    0L,
                    0L,
                    phaseDurationMs,
                    degraded);
        }

        static SemanticLinkingStats windowProcessed(
                long searchPhaseDurationMs,
                long resolvePhaseDurationMs,
                long upsertPhaseDurationMs) {
            return new SemanticLinkingStats(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    searchPhaseDurationMs,
                    resolvePhaseDurationMs,
                    upsertPhaseDurationMs,
                    0L,
                    false);
        }

        static SemanticLinkingStats windowMarkedFailed() {
            return new SemanticLinkingStats(
                    0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0L, 0L, 0L, 0L, true);
        }

        static SemanticLinkingStats windowFailure() {
            return windowProcessed(0L, 0L, 0L).plus(windowMarkedFailed());
        }

        SemanticLinkingStats plus(SemanticLinkingStats other) {
            return new SemanticLinkingStats(
                    searchRequestCount + other.searchRequestCount,
                    searchInvocationCount + other.searchInvocationCount,
                    searchHitCount + other.searchHitCount,
                    resolvedCandidateCount + other.resolvedCandidateCount,
                    createdLinkCount + other.createdLinkCount,
                    upsertBatchCount + other.upsertBatchCount,
                    sourceWindowCount + other.sourceWindowCount,
                    failedResolveChunkCount + other.failedResolveChunkCount,
                    failedWindowCount + other.failedWindowCount,
                    failedUpsertBatchCount + other.failedUpsertBatchCount,
                    sameBatchHitCount + other.sameBatchHitCount,
                    searchFallbackCount + other.searchFallbackCount,
                    intraBatchCandidateCount + other.intraBatchCandidateCount,
                    searchPhaseDurationMs + other.searchPhaseDurationMs,
                    resolvePhaseDurationMs + other.resolvePhaseDurationMs,
                    upsertPhaseDurationMs + other.upsertPhaseDurationMs,
                    intraBatchPhaseDurationMs + other.intraBatchPhaseDurationMs,
                    degraded || other.degraded);
        }
    }

    private record CandidateHit(MemoryItem item, String vectorId, float score) {}

    private record WindowNormalization(List<ItemLink> links, SemanticLinkingStats stats) {}

    public record SemanticLinkingPlan(List<ItemLink> links, SemanticLinkingStats stats) {

        public SemanticLinkingPlan {
            links = links == null ? List.of() : List.copyOf(links);
            stats = stats == null ? SemanticLinkingStats.empty() : stats;
        }

        public static SemanticLinkingPlan empty() {
            return new SemanticLinkingPlan(List.of(), SemanticLinkingStats.empty());
        }

        public static SemanticLinkingPlan windowFailure() {
            return new SemanticLinkingPlan(List.of(), SemanticLinkingStats.windowFailure());
        }

        public static SemanticLinkingPlan stageFailure() {
            return new SemanticLinkingPlan(List.of(), SemanticLinkingStats.stageFailure());
        }

        SemanticLinkingPlan plus(SemanticLinkingPlan other) {
            var mergedLinks = new ArrayList<ItemLink>(links);
            mergedLinks.addAll(other.links);
            return new SemanticLinkingPlan(mergedLinks, stats.plus(other.stats));
        }

        SemanticLinkingPlan withStats(SemanticLinkingStats additionalStats) {
            return new SemanticLinkingPlan(links, stats.plus(additionalStats));
        }
    }
}
