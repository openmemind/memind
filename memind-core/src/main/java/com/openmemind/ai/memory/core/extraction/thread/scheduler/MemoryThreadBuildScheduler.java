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
package com.openmemind.ai.memory.core.extraction.thread.scheduler;

import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.MemoryThreadRuntimeStatus;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.thread.derivation.MemoryThreadDerivationOutcome;
import com.openmemind.ai.memory.core.extraction.thread.derivation.RuleBasedMemoryThreadDeriver;
import com.openmemind.ai.memory.core.extraction.thread.text.RuleBasedMemoryThreadTextGenerator;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.thread.InMemoryMemoryThreadOperations;
import com.openmemind.ai.memory.core.store.thread.MemoryThreadOperations;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial-per-memory scheduler for deterministic memory-thread derive/flush/rebuild work.
 */
public class MemoryThreadBuildScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryThreadBuildScheduler.class);

    private final MemoryThreadOperations threadOperations;
    private final MemoryStore store;
    private final RuleBasedMemoryThreadDeriver deriver;
    private final RuleBasedMemoryThreadTextGenerator textGenerator;
    private final MemoryThreadOptions options;
    private final AtomicReference<MemoryThreadRuntimeStatus> status;
    private final Map<String, Deque<Runnable>> queuedTasks = new ConcurrentHashMap<>();
    private final AtomicInteger queuedTaskCount = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MemoryThreadBuildScheduler(
            MemoryThreadOperations threadOperations,
            MemoryStore store,
            RuleBasedMemoryThreadDeriver deriver,
            RuleBasedMemoryThreadTextGenerator textGenerator,
            MemoryThreadOptions options,
            MemoryObserver memoryObserver,
            Optional<String> forcedDisableReason) {
        this.threadOperations = Objects.requireNonNull(threadOperations, "threadOperations");
        this.store = Objects.requireNonNull(store, "store");
        this.deriver = Objects.requireNonNull(deriver, "deriver");
        this.textGenerator = Objects.requireNonNull(textGenerator, "textGenerator");
        this.options = Objects.requireNonNull(options, "options");
        Objects.requireNonNull(memoryObserver, "memoryObserver");
        Optional<String> reason =
                forcedDisableReason != null ? forcedDisableReason : Optional.empty();
        boolean derivationEnabled = options.enabled() && options.derivation().enabled();
        boolean derivationAvailable = derivationEnabled && reason.isEmpty();
        this.status =
                new AtomicReference<>(
                        new MemoryThreadRuntimeStatus(
                                options.enabled(),
                                derivationEnabled,
                                derivationAvailable,
                                reason.orElse(null),
                                0,
                                null,
                                null,
                                0L));
    }

    public void submitDerivation(
            MemoryId memoryId, List<MemoryItem> items, ItemExtractionConfig config) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(config, "config");
        if (closed.get()
                || !status.get().derivationAvailable()
                || items == null
                || items.isEmpty()) {
            return;
        }
        List<MemoryItem> submittedItems = items.stream().filter(Objects::nonNull).toList();
        if (submittedItems.isEmpty()) {
            return;
        }
        enqueue(memoryId, () -> deriveAndRefresh(memoryId, submittedItems));
        if (!options.derivation().async()) {
            flush(memoryId);
        }
    }

    public void flush(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (closed.get()) {
            return;
        }
        Deque<Runnable> tasks = queuedTasks.get(memoryId.toIdentifier());
        if (tasks == null) {
            return;
        }
        Runnable task;
        while ((task = tasks.pollFirst()) != null) {
            try {
                task.run();
                markSuccess();
            } catch (RuntimeException e) {
                log.warn(
                        "Memory-thread derivation task failed for memoryId={}",
                        memoryId.toIdentifier(),
                        e);
                markFailure();
            }
        }
        if (tasks.isEmpty()) {
            queuedTasks.remove(memoryId.toIdentifier(), tasks);
        }
    }

    public void rebuild(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (closed.get() || !status.get().derivationAvailable()) {
            return;
        }
        flush(memoryId);
        enqueue(memoryId, () -> rebuildFromPersistedState(memoryId));
        flush(memoryId);
    }

    public MemoryThreadRuntimeStatus status() {
        return status.get();
    }

    public MemoryThreadOperations threadOperations() {
        return threadOperations;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        queuedTasks.clear();
        queuedTaskCount.set(0);
        updateQueueDepth(0);
    }

    private void enqueue(MemoryId memoryId, Runnable task) {
        queuedTasks
                .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ArrayDeque<>())
                .addLast(task);
        updateQueueDepth(queuedTaskCount.incrementAndGet());
    }

    private void deriveAndRefresh(MemoryId memoryId, List<MemoryItem> items) {
        MemoryThreadDerivationOutcome outcome =
                deriver.derive(memoryId, items, threadOperations, store);
        if (outcome.isEmpty()) {
            return;
        }
        if (!outcome.threads().isEmpty()) {
            threadOperations.upsertThreads(memoryId, outcome.threads());
        }
        if (!outcome.memberships().isEmpty()) {
            threadOperations.upsertThreadItems(memoryId, outcome.memberships());
        }
        refreshThreads(memoryId, outcome.touchedThreadIds(), threadOperations);
    }

    private void rebuildFromPersistedState(MemoryId memoryId) {
        List<MemoryItem> items =
                store.itemOperations().listItems(memoryId).stream()
                        .sorted(
                                Comparator.comparing(
                                                MemoryThreadBuildScheduler::activityAt,
                                                Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(
                                                MemoryItem::id,
                                                Comparator.nullsLast(Long::compareTo)))
                        .toList();
        if (items.isEmpty()) {
            return;
        }

        InMemoryMemoryThreadOperations scratch = new InMemoryMemoryThreadOperations();
        for (MemoryItem item : items) {
            MemoryThreadDerivationOutcome outcome =
                    deriver.derive(memoryId, List.of(item), scratch, store);
            if (!outcome.threads().isEmpty()) {
                scratch.upsertThreads(memoryId, outcome.threads());
            }
            if (!outcome.memberships().isEmpty()) {
                scratch.upsertThreadItems(memoryId, outcome.memberships());
            }
        }

        refreshThreads(
                memoryId,
                scratch.listThreads(memoryId).stream()
                        .map(MemoryThread::id)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()),
                scratch);

        List<Long> itemIds = items.stream().map(MemoryItem::id).filter(Objects::nonNull).toList();
        threadOperations.deleteMembershipsByItemIds(memoryId, itemIds);
        threadOperations.upsertThreads(memoryId, scratch.listThreads(memoryId));
        threadOperations.upsertThreadItems(memoryId, scratch.listThreadItems(memoryId));
    }

    private void refreshThreads(
            MemoryId memoryId, Set<Long> touchedThreadIds, MemoryThreadOperations operations) {
        if (touchedThreadIds == null || touchedThreadIds.isEmpty()) {
            return;
        }
        Map<Long, MemoryThread> threadsById =
                operations.listThreads(memoryId).stream()
                        .filter(thread -> thread.id() != null)
                        .collect(Collectors.toMap(MemoryThread::id, thread -> thread, (l, r) -> r));
        Map<Long, List<MemoryThreadItem>> membershipsByThreadId = new LinkedHashMap<>();
        for (MemoryThreadItem membership : operations.listThreadItems(memoryId)) {
            membershipsByThreadId
                    .computeIfAbsent(membership.threadId(), ignored -> new ArrayList<>())
                    .add(membership);
        }

        List<MemoryThread> refreshedThreads = new ArrayList<>();
        for (Long threadId : touchedThreadIds) {
            MemoryThread thread = threadsById.get(threadId);
            if (thread == null) {
                continue;
            }
            List<MemoryThreadItem> memberships =
                    membershipsByThreadId.getOrDefault(threadId, List.of()).stream()
                            .sorted(
                                    Comparator.comparingInt(MemoryThreadItem::sequenceHint)
                                            .thenComparing(MemoryThreadItem::itemId))
                            .limit(options.rule().maxMembersPerThread())
                            .toList();
            List<Long> itemIds =
                    memberships.stream()
                            .map(MemoryThreadItem::itemId)
                            .filter(Objects::nonNull)
                            .toList();
            List<MemoryItem> members =
                    orderItems(store.itemOperations().getItemsByIds(memoryId, itemIds), itemIds);
            if (members.isEmpty()) {
                continue;
            }
            refreshedThreads.add(textGenerator.refreshCanonicalText(thread, members));
        }
        if (!refreshedThreads.isEmpty()) {
            operations.upsertThreads(memoryId, refreshedThreads);
        }
    }

    private void markSuccess() {
        int depth = Math.max(queuedTaskCount.decrementAndGet(), 0);
        status.updateAndGet(current -> current.withSuccess(Instant.now(), depth));
    }

    private void markFailure() {
        int depth = Math.max(queuedTaskCount.decrementAndGet(), 0);
        status.updateAndGet(current -> current.withFailure(Instant.now(), depth));
    }

    private void updateQueueDepth(int queueDepth) {
        status.updateAndGet(
                current ->
                        new MemoryThreadRuntimeStatus(
                                current.memoryThreadEnabled(),
                                current.derivationEnabled(),
                                current.derivationAvailable(),
                                current.forcedDisabledReason(),
                                queueDepth,
                                current.lastSuccessAt(),
                                current.lastFailureAt(),
                                current.failureCount()));
    }

    private static Instant activityAt(MemoryItem item) {
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        return item.createdAt();
    }

    private static List<MemoryItem> orderItems(List<MemoryItem> items, List<Long> itemIds) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < itemIds.size(); i++) {
            order.put(itemIds.get(i), i);
        }
        return items.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.<MemoryItem>comparingInt(
                                        item -> order.getOrDefault(item.id(), Integer.MAX_VALUE))
                                .thenComparing(
                                        MemoryThreadBuildScheduler::activityAt,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        MemoryItem::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }
}
