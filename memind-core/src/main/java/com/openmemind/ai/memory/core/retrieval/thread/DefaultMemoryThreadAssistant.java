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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.retrieval.ItemRetrievalGuard;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.thread.MemoryThreadOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpMemoryThreadOperations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Direct-seeded bounded memory-thread assist implementation.
 */
public final class DefaultMemoryThreadAssistant implements MemoryThreadAssistant {

    private final MemoryStore store;

    public DefaultMemoryThreadAssistant(MemoryStore store) {
        this.store = store;
    }

    @Override
    public Mono<MemoryThreadAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        boolean enabled = settings != null && settings.enabled();
        if (!enabled || directWindow == null || directWindow.isEmpty() || store == null) {
            return Mono.just(MemoryThreadAssistResult.directOnly(directWindow, enabled));
        }

        return Mono.fromCallable(() -> assistBlocking(context, settings, directWindow))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(settings.timeout())
                .onErrorResume(
                        TimeoutException.class,
                        error ->
                                Mono.just(
                                        MemoryThreadAssistResult.degraded(
                                                directWindow, true, true)))
                .onErrorResume(
                        error ->
                                Mono.just(
                                        MemoryThreadAssistResult.degraded(
                                                directWindow, true, false)));
    }

    private MemoryThreadAssistResult assistBlocking(
            QueryContext context,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        MemoryThreadOperations threadOperations = resolveThreadOperations();
        List<MemoryThreadItem> memberships = threadOperations.listThreadItems(context.memoryId());
        if (memberships.isEmpty()) {
            return MemoryThreadAssistResult.directOnly(directWindow, true);
        }

        Map<Long, DirectSeed> directSeeds = directSeeds(directWindow);
        if (directSeeds.isEmpty()) {
            return MemoryThreadAssistResult.directOnly(directWindow, true);
        }

        Map<Long, MemoryThread> threadsById = threadsById(context, threadOperations);
        if (threadsById.isEmpty()) {
            return MemoryThreadAssistResult.directOnly(directWindow, true);
        }

        List<SelectedThread> selectedThreads =
                selectSeedThreads(memberships, threadsById, directSeeds, settings.maxThreads());
        if (selectedThreads.isEmpty()) {
            return MemoryThreadAssistResult.directOnly(directWindow, true);
        }

        List<ThreadCandidate> candidates =
                materializeCandidates(
                        context,
                        directSeeds.keySet(),
                        memberships,
                        selectedThreads,
                        settings.maxMembersPerThread());

        return fuseTail(
                directWindow, settings.protectDirectTopK(), selectedThreads.size(), candidates);
    }

    private MemoryThreadOperations resolveThreadOperations() {
        MemoryThreadOperations operations = store.threadOperations();
        return operations != null ? operations : NoOpMemoryThreadOperations.INSTANCE;
    }

    private Map<Long, DirectSeed> directSeeds(List<ScoredResult> directWindow) {
        Map<Long, DirectSeed> directSeeds = new LinkedHashMap<>();
        for (int i = 0; i < directWindow.size(); i++) {
            int rank = i;
            ScoredResult result = directWindow.get(i);
            parseItemId(result.sourceId())
                    .ifPresent(
                            itemId ->
                                    directSeeds.putIfAbsent(
                                            itemId,
                                            new DirectSeed(
                                                    itemId, rank, result.finalScore(), result)));
        }
        return directSeeds;
    }

    private Map<Long, MemoryThread> threadsById(
            QueryContext context, MemoryThreadOperations threadOperations) {
        Map<Long, MemoryThread> threadsById = new LinkedHashMap<>();
        for (MemoryThread thread : threadOperations.listThreads(context.memoryId())) {
            if (thread.id() != null) {
                threadsById.put(thread.id(), thread);
            }
        }
        return threadsById;
    }

    private List<SelectedThread> selectSeedThreads(
            List<MemoryThreadItem> memberships,
            Map<Long, MemoryThread> threadsById,
            Map<Long, DirectSeed> directSeeds,
            int maxThreads) {
        Map<Long, SelectedThread> byThreadId = new LinkedHashMap<>();
        for (MemoryThreadItem membership : memberships) {
            DirectSeed directSeed = directSeeds.get(membership.itemId());
            MemoryThread thread = threadsById.get(membership.threadId());
            if (directSeed == null || thread == null) {
                continue;
            }

            byThreadId.compute(
                    membership.threadId(),
                    (threadId, existing) -> {
                        if (existing == null) {
                            return new SelectedThread(
                                    threadId,
                                    thread.threadKey(),
                                    directSeed.rank(),
                                    directSeed.score());
                        }
                        int minRank = Math.min(existing.seedRank(), directSeed.rank());
                        double maxScore = Math.max(existing.seedScore(), directSeed.score());
                        return new SelectedThread(
                                threadId, existing.threadKey(), minRank, maxScore);
                    });
        }

        return byThreadId.values().stream()
                .sorted(
                        Comparator.comparingInt(SelectedThread::seedRank)
                                .thenComparing(SelectedThread::threadKey)
                                .thenComparing(SelectedThread::threadId))
                .limit(maxThreads)
                .toList();
    }

    private List<ThreadCandidate> materializeCandidates(
            QueryContext context,
            Set<Long> directItemIds,
            List<MemoryThreadItem> memberships,
            List<SelectedThread> selectedThreads,
            int maxMembersPerThread) {
        Map<Long, CandidateDescriptor> descriptors = new LinkedHashMap<>();
        for (SelectedThread thread : selectedThreads) {
            int admittedForThread = 0;
            for (MemoryThreadItem membership : memberships) {
                if (!thread.threadId().equals(membership.threadId())
                        || directItemIds.contains(membership.itemId())) {
                    continue;
                }
                if (descriptors.containsKey(membership.itemId())) {
                    continue;
                }
                descriptors.put(
                        membership.itemId(),
                        new CandidateDescriptor(
                                membership.itemId(),
                                membership.sequenceHint(),
                                membership.membershipWeight(),
                                thread.seedRank(),
                                thread.seedScore()));
                admittedForThread++;
                if (admittedForThread >= maxMembersPerThread) {
                    break;
                }
            }
        }

        if (descriptors.isEmpty()) {
            return List.of();
        }

        Map<Long, MemoryItem> itemsById =
                store
                        .itemOperations()
                        .getItemsByIds(context.memoryId(), List.copyOf(descriptors.keySet()))
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        MemoryItem::id,
                                        item -> item,
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        List<ThreadCandidate> candidates = new ArrayList<>(descriptors.size());
        for (CandidateDescriptor descriptor : descriptors.values()) {
            MemoryItem item = itemsById.get(descriptor.itemId());
            if (!ItemRetrievalGuard.allows(item, context)) {
                continue;
            }
            double finalScore = descriptor.seedScore() * descriptor.membershipWeight();
            candidates.add(
                    new ThreadCandidate(
                            item.id(),
                            descriptor.seedRank(),
                            descriptor.sequenceHint(),
                            new ScoredResult(
                                    ScoredResult.SourceType.ITEM,
                                    String.valueOf(item.id()),
                                    item.content(),
                                    0.0f,
                                    finalScore,
                                    item.occurredAt())));
        }

        return candidates.stream()
                .sorted(
                        Comparator.comparingInt(ThreadCandidate::seedRank)
                                .thenComparing(
                                        (ThreadCandidate candidate) ->
                                                -candidate.scoredResult().finalScore())
                                .thenComparingInt(ThreadCandidate::sequenceHint)
                                .thenComparingLong(ThreadCandidate::itemId))
                .toList();
    }

    private MemoryThreadAssistResult fuseTail(
            List<ScoredResult> directWindow,
            int protectDirectTopK,
            int seedThreadCount,
            List<ThreadCandidate> candidates) {
        int pinned = Math.min(protectDirectTopK, directWindow.size());
        int tailCapacity = Math.max(0, directWindow.size() - pinned);
        boolean clamped = candidates.size() > tailCapacity;

        List<ScoredResult> finalItems = new ArrayList<>(directWindow.size());
        finalItems.addAll(directWindow.subList(0, pinned));

        Set<String> usedIds =
                new LinkedHashSet<>(finalItems.stream().map(ScoredResult::sourceId).toList());
        int admittedMemberCount = 0;
        for (ThreadCandidate candidate : candidates) {
            if (admittedMemberCount >= tailCapacity) {
                break;
            }
            ScoredResult scored = candidate.scoredResult();
            if (usedIds.add(scored.sourceId())) {
                finalItems.add(scored);
                admittedMemberCount++;
            }
        }

        if (finalItems.size() < directWindow.size()) {
            for (ScoredResult direct : directWindow.subList(pinned, directWindow.size())) {
                if (finalItems.size() >= directWindow.size()) {
                    break;
                }
                if (usedIds.add(direct.sourceId())) {
                    finalItems.add(direct);
                }
            }
        }

        return new MemoryThreadAssistResult(
                finalItems,
                MemoryThreadAssistResult.Stats.success(
                        seedThreadCount, candidates.size(), admittedMemberCount, clamped));
    }

    private static OptionalLong parseItemId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(sourceId));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private record DirectSeed(Long itemId, int rank, double score, ScoredResult scoredResult) {}

    private record SelectedThread(
            Long threadId, String threadKey, int seedRank, double seedScore) {}

    private record CandidateDescriptor(
            Long itemId,
            int sequenceHint,
            double membershipWeight,
            int seedRank,
            double seedScore) {}

    private record ThreadCandidate(
            Long itemId, int seedRank, int sequenceHint, ScoredResult scoredResult) {}
}
