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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateMatch;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Post-persistence temporal linker that combines same-batch and historical candidates.
 */
public class TemporalItemLinker {

    static final int TEMPORAL_QUERY_BATCH_SIZE = 128;

    private final ItemOperations itemOperations;
    private final GraphOperations graphOperations;
    private final TemporalRelationClassifier classifier;
    private final ItemGraphOptions options;

    public TemporalItemLinker(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            TemporalRelationClassifier classifier,
            ItemGraphOptions options) {
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Mono<TemporalLinkingStats> link(MemoryId memoryId, List<MemoryItem> items) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.just(TemporalLinkingStats.empty());
        }

        return Mono.fromSupplier(
                () -> {
                    var probePolicy = TemporalProbePolicy.fromOptions(options);
                    var incoming =
                            items.stream()
                                    .map(
                                            item ->
                                                    new IncomingTemporalItem(
                                                            item, classifier.resolveWindow(item)))
                                    .filter(entry -> entry.window() != null)
                                    .toList();
                    if (incoming.isEmpty()) {
                        return TemporalLinkingStats.empty();
                    }

                    var excludedItemIds =
                            items.stream()
                                    .map(MemoryItem::id)
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
                    var requestBatches =
                            partition(
                                    incoming.stream()
                                            .map(
                                                    entry ->
                                                            probePolicy.toRequest(
                                                                    entry.item(), entry.window()))
                                            .toList(),
                                    TEMPORAL_QUERY_BATCH_SIZE);
                    var historyMatchesBySource = new LinkedHashMap<Long, List<MemoryItem>>();
                    boolean degraded = false;

                    long queryStartedAt = System.nanoTime();
                    for (var requestBatch : requestBatches) {
                        try {
                            mergeHistoryMatches(
                                    historyMatchesBySource,
                                    itemOperations.listTemporalCandidateMatches(
                                            memoryId, requestBatch, excludedItemIds));
                        } catch (Exception ignored) {
                            degraded = true;
                        }
                    }
                    long queryDurationMs = elapsedMillis(queryStartedAt);

                    long buildStartedAt = System.nanoTime();
                    var finalLinks = new LinkedHashMap<LinkIdentity, ItemLink>();
                    int selectedPairs = 0;
                    for (var incomingItem : incoming) {
                        var candidates =
                                mergeCandidates(incomingItem, incoming, historyMatchesBySource);
                        var selected = selectTopCandidates(incomingItem, candidates);
                        selectedPairs += selected.size();
                        selected.stream()
                                .map(candidate -> canonicalize(memoryId, incomingItem, candidate))
                                .forEach(
                                        link ->
                                                finalLinks.putIfAbsent(
                                                        new LinkIdentity(link), link));
                    }
                    long buildDurationMs = elapsedMillis(buildStartedAt);

                    long upsertStartedAt = System.nanoTime();
                    try {
                        graphOperations.upsertItemLinks(memoryId, List.copyOf(finalLinks.values()));
                    } catch (Exception error) {
                        return TemporalLinkingStats.stageFailure(
                                incoming.size(),
                                requestBatches.size(),
                                historyMatchesBySource.values().stream().mapToInt(List::size).sum(),
                                countSameBatchCandidates(incoming),
                                selectedPairs,
                                finalLinks.size(),
                                queryDurationMs,
                                buildDurationMs,
                                elapsedMillis(upsertStartedAt));
                    }

                    return TemporalLinkingStats.success(
                            incoming.size(),
                            requestBatches.size(),
                            historyMatchesBySource.values().stream().mapToInt(List::size).sum(),
                            countSameBatchCandidates(incoming),
                            selectedPairs,
                            finalLinks.size(),
                            queryDurationMs,
                            buildDurationMs,
                            elapsedMillis(upsertStartedAt),
                            degraded);
                });
    }

    private void mergeHistoryMatches(
            Map<Long, List<MemoryItem>> historyMatchesBySource,
            List<TemporalCandidateMatch> matches) {
        for (var match : matches) {
            historyMatchesBySource
                    .computeIfAbsent(match.sourceItemId(), ignored -> new ArrayList<>())
                    .add(match.candidateItem());
        }
    }

    private List<TemporalCandidate> mergeCandidates(
            IncomingTemporalItem source,
            List<IncomingTemporalItem> incoming,
            Map<Long, List<MemoryItem>> historyMatchesBySource) {
        var merged = new LinkedHashMap<Long, TemporalCandidate>();

        for (var candidate : incoming) {
            if (candidate.item().id().equals(source.item().id())
                    || !Objects.equals(candidate.item().type(), source.item().type())
                    || !Objects.equals(candidate.item().category(), source.item().category())) {
                continue;
            }
            merged.putIfAbsent(
                    candidate.item().id(),
                    new TemporalCandidate(candidate.item(), candidate.window(), true));
        }

        for (var historical : historyMatchesBySource.getOrDefault(source.item().id(), List.of())) {
            var window = classifier.resolveWindow(historical);
            if (window == null
                    || !Objects.equals(historical.type(), source.item().type())
                    || !Objects.equals(historical.category(), source.item().category())) {
                continue;
            }
            merged.putIfAbsent(historical.id(), new TemporalCandidate(historical, window, false));
        }

        return List.copyOf(merged.values());
    }

    private List<TemporalCandidate> selectTopCandidates(
            IncomingTemporalItem source, List<TemporalCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> rankCandidate(source, candidate))
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparingInt(ScoredTemporalCandidate::relationPriority)
                                .thenComparing(ScoredTemporalCandidate::anchorGap)
                                .thenComparing(scored -> scored.candidate().item().id()))
                .limit(options.maxTemporalLinksPerItem())
                .map(ScoredTemporalCandidate::candidate)
                .toList();
    }

    private ScoredTemporalCandidate rankCandidate(
            IncomingTemporalItem source, TemporalCandidate candidate) {
        String relationType = classifier.classify(source.window(), candidate.window());
        if (relationType == null) {
            return null;
        }
        return new ScoredTemporalCandidate(
                candidate,
                relationPriority(relationType),
                Duration.between(source.window().anchor(), candidate.window().anchor()).abs(),
                relationType);
    }

    private ItemLink canonicalize(
            MemoryId memoryId, IncomingTemporalItem source, TemporalCandidate candidate) {
        TemporalWindow left = source.window();
        TemporalWindow right = candidate.window();
        MemoryItem leftItem = source.item();
        MemoryItem rightItem = candidate.item();
        int compare = classifier.compare(left, right);
        MemoryItem earlier = compare <= 0 ? leftItem : rightItem;
        MemoryItem later = earlier == leftItem ? rightItem : leftItem;
        String relationType = classifier.classify(left, right);
        return new ItemLink(
                memoryId.toIdentifier(),
                earlier.id(),
                later.id(),
                ItemLinkType.TEMPORAL,
                1.0d,
                Map.of("relationType", relationType),
                resolveGraphTimestamp(later));
    }

    private int countSameBatchCandidates(List<IncomingTemporalItem> incoming) {
        int count = 0;
        for (int sourceIndex = 0; sourceIndex < incoming.size(); sourceIndex++) {
            for (int candidateIndex = 0; candidateIndex < incoming.size(); candidateIndex++) {
                if (sourceIndex == candidateIndex) {
                    continue;
                }
                var source = incoming.get(sourceIndex);
                var candidate = incoming.get(candidateIndex);
                if (!Objects.equals(source.item().type(), candidate.item().type())
                        || !Objects.equals(source.item().category(), candidate.item().category())) {
                    continue;
                }
                if (classifier.classify(source.window(), candidate.window()) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private int relationPriority(String relationType) {
        return switch (relationType) {
            case "overlap" -> 0;
            case "nearby" -> 1;
            case "before" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private static Instant resolveGraphTimestamp(MemoryItem item) {
        return firstNonNull(
                item.createdAt(),
                item.observedAt(),
                item.occurredAt(),
                item.occurredStart(),
                Instant.EPOCH);
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

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static <T> List<List<T>> partition(List<T> values, int batchSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var partitions = new ArrayList<List<T>>();
        for (int start = 0; start < values.size(); start += batchSize) {
            partitions.add(
                    List.copyOf(values.subList(start, Math.min(values.size(), start + batchSize))));
        }
        return List.copyOf(partitions);
    }

    private record IncomingTemporalItem(MemoryItem item, TemporalWindow window) {}

    private record TemporalCandidate(MemoryItem item, TemporalWindow window, boolean sameBatch) {}

    private record ScoredTemporalCandidate(
            TemporalCandidate candidate,
            int relationPriority,
            Duration anchorGap,
            String relationType) {}

    private record LinkIdentity(Long sourceItemId, Long targetItemId, ItemLinkType linkType) {

        private LinkIdentity(ItemLink link) {
            this(link.sourceItemId(), link.targetItemId(), link.linkType());
        }
    }

    public record TemporalLinkingStats(
            int sourceCount,
            int historyQueryBatchCount,
            int historyCandidateCount,
            int intraBatchCandidateCount,
            int selectedPairCount,
            int createdLinkCount,
            long queryDurationMs,
            long buildDurationMs,
            long upsertDurationMs,
            boolean degraded) {

        public static TemporalLinkingStats empty() {
            return new TemporalLinkingStats(0, 0, 0, 0, 0, 0, 0L, 0L, 0L, false);
        }

        public static TemporalLinkingStats success(
                int sourceCount,
                int historyQueryBatchCount,
                int historyCandidateCount,
                int intraBatchCandidateCount,
                int selectedPairCount,
                int createdLinkCount,
                long queryDurationMs,
                long buildDurationMs,
                long upsertDurationMs,
                boolean degraded) {
            return new TemporalLinkingStats(
                    sourceCount,
                    historyQueryBatchCount,
                    historyCandidateCount,
                    intraBatchCandidateCount,
                    selectedPairCount,
                    createdLinkCount,
                    queryDurationMs,
                    buildDurationMs,
                    upsertDurationMs,
                    degraded);
        }

        public static TemporalLinkingStats stageFailure() {
            return new TemporalLinkingStats(0, 0, 0, 0, 0, 0, 0L, 0L, 0L, true);
        }

        public static TemporalLinkingStats stageFailure(
                int sourceCount,
                int historyQueryBatchCount,
                int historyCandidateCount,
                int intraBatchCandidateCount,
                int selectedPairCount,
                int createdLinkCount,
                long queryDurationMs,
                long buildDurationMs,
                long upsertDurationMs) {
            return new TemporalLinkingStats(
                    sourceCount,
                    historyQueryBatchCount,
                    historyCandidateCount,
                    intraBatchCandidateCount,
                    selectedPairCount,
                    createdLinkCount,
                    queryDurationMs,
                    buildDurationMs,
                    upsertDurationMs,
                    true);
        }
    }
}
