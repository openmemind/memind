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
package com.openmemind.ai.memory.core.store.thread;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link ThreadProjectionStore}.
 */
public class InMemoryThreadProjectionStore implements ThreadProjectionStore {

    private final Map<String, MemoryState> stateByMemoryId = new ConcurrentHashMap<>();

    @Override
    public void ensureRuntime(MemoryId memoryId, String materializationPolicyVersion) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        MemoryState state = state(memoryId);
        MemoryThreadRuntimeState current = state.runtime;
        if (current == null) {
            state.runtime =
                    new MemoryThreadRuntimeState(
                            memoryId.toIdentifier(),
                            MemoryThreadProjectionState.REBUILD_REQUIRED,
                            0,
                            0,
                            null,
                            null,
                            false,
                            null,
                            0L,
                            materializationPolicyVersion,
                            "runtime bootstrap",
                            Instant.now());
            return;
        }
        if (Objects.equals(current.materializationPolicyVersion(), materializationPolicyVersion)) {
            return;
        }
        state.runtime =
                new MemoryThreadRuntimeState(
                        memoryId.toIdentifier(),
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        pendingCount(state),
                        failedCount(state),
                        current.lastEnqueuedItemId(),
                        current.lastProcessedItemId(),
                        false,
                        null,
                        current.rebuildEpoch(),
                        materializationPolicyVersion,
                        "policy version changed",
                        Instant.now());
    }

    @Override
    public Optional<MemoryThreadRuntimeState> getRuntime(MemoryId memoryId) {
        return Optional.ofNullable(state(memoryId).runtime);
    }

    @Override
    public List<MemoryThreadProjection> listThreads(MemoryId memoryId) {
        return state(memoryId).threadsByKey.values().stream()
                .sorted(Comparator.comparing(MemoryThreadProjection::threadKey))
                .toList();
    }

    @Override
    public Optional<MemoryThreadProjection> getThread(MemoryId memoryId, String threadKey) {
        return Optional.ofNullable(state(memoryId).threadsByKey.get(threadKey));
    }

    @Override
    public List<MemoryThreadEvent> listEvents(MemoryId memoryId, String threadKey) {
        return state(memoryId).eventsByThreadKey.getOrDefault(threadKey, List.of()).stream()
                .sorted(
                        Comparator.comparingLong(MemoryThreadEvent::eventSeq)
                                .thenComparing(MemoryThreadEvent::eventKey))
                .toList();
    }

    @Override
    public List<MemoryThreadMembership> listMemberships(MemoryId memoryId, String threadKey) {
        return state(memoryId).membershipsByThreadKey.getOrDefault(threadKey, List.of()).stream()
                .sorted(
                        Comparator.comparingLong(MemoryThreadMembership::itemId)
                                .thenComparing(MemoryThreadMembership::role))
                .toList();
    }

    @Override
    public List<MemoryThreadProjection> listThreadsByItemId(MemoryId memoryId, long itemId) {
        MemoryState state = state(memoryId);
        return state.threadKeysByItemId.getOrDefault(itemId, List.of()).stream()
                .map(state.threadsByKey::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MemoryThreadProjection::threadKey))
                .toList();
    }

    @Override
    public void enqueue(MemoryId memoryId, long triggerItemId) {
        enqueueInternal(memoryId, triggerItemId, false);
    }

    @Override
    public void enqueueReplay(MemoryId memoryId, long replayCutoffItemId) {
        enqueueInternal(memoryId, replayCutoffItemId, true);
    }

    private void enqueueInternal(
            MemoryId memoryId, long triggerItemId, boolean replayableExisting) {
        MemoryState state = state(memoryId);
        MemoryThreadIntakeOutboxEntry existing = state.outboxByTriggerItemId.get(triggerItemId);
        Instant now = Instant.now();
        if (existing == null) {
            state.outboxByTriggerItemId.put(
                    triggerItemId,
                    new MemoryThreadIntakeOutboxEntry(
                            memoryId.toIdentifier(),
                            triggerItemId,
                            1L,
                            MemoryThreadIntakeStatus.PENDING,
                            0,
                            null,
                            null,
                            null,
                            null,
                            now,
                            null));
        } else if (replayableExisting && existing.status() != MemoryThreadIntakeStatus.PENDING) {
            state.outboxByTriggerItemId.put(
                    triggerItemId,
                    new MemoryThreadIntakeOutboxEntry(
                            existing.memoryId(),
                            existing.triggerItemId(),
                            existing.enqueueGeneration() + 1,
                            MemoryThreadIntakeStatus.PENDING,
                            existing.attemptCount(),
                            null,
                            null,
                            null,
                            existing.lastProcessedItemId(),
                            now,
                            null));
        }
        if (state.runtime != null) {
            Long currentLastEnqueued = state.runtime.lastEnqueuedItemId();
            long nextLastEnqueued =
                    currentLastEnqueued == null
                            ? triggerItemId
                            : Math.max(currentLastEnqueued, triggerItemId);
            state.runtime =
                    new MemoryThreadRuntimeState(
                            state.runtime.memoryId(),
                            state.runtime.projectionState(),
                            state.runtime.pendingCount(),
                            state.runtime.failedCount(),
                            nextLastEnqueued,
                            state.runtime.lastProcessedItemId(),
                            state.runtime.rebuildInProgress(),
                            state.runtime.rebuildCutoffItemId(),
                            state.runtime.rebuildEpoch(),
                            state.runtime.materializationPolicyVersion(),
                            state.runtime.invalidationReason(),
                            now);
        }
        refreshRuntimeCounters(memoryId, state);
    }

    @Override
    public List<MemoryThreadIntakeOutboxEntry> listOutbox(MemoryId memoryId) {
        return state(memoryId).outboxByTriggerItemId.values().stream()
                .sorted(Comparator.comparingLong(MemoryThreadIntakeOutboxEntry::triggerItemId))
                .toList();
    }

    @Override
    public List<MemoryThreadIntakeClaim> claimPending(
            MemoryId memoryId, Instant claimedAt, Instant leaseExpiresAt, int batchSize) {
        MemoryState state = state(memoryId);
        List<MemoryThreadIntakeClaim> claimed = new ArrayList<>();
        for (MemoryThreadIntakeOutboxEntry entry : listOutbox(memoryId)) {
            if (claimed.size() >= batchSize) {
                break;
            }
            if (entry.status() != MemoryThreadIntakeStatus.PENDING) {
                continue;
            }
            MemoryThreadIntakeOutboxEntry processing =
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.PROCESSING,
                            entry.attemptCount(),
                            claimedAt,
                            leaseExpiresAt,
                            null,
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            null);
            state.outboxByTriggerItemId.put(entry.triggerItemId(), processing);
            claimed.add(toClaim(processing));
        }
        refreshRuntimeCounters(memoryId, state);
        return claimed;
    }

    @Override
    public int recoverAbandoned(MemoryId memoryId, Instant now, int maxAttempts) {
        MemoryState state = state(memoryId);
        int recovered = 0;
        for (MemoryThreadIntakeOutboxEntry entry : listOutbox(memoryId)) {
            if (entry.status() != MemoryThreadIntakeStatus.PROCESSING
                    || entry.leaseExpiresAt() == null
                    || !entry.leaseExpiresAt().isBefore(now)) {
                continue;
            }
            int nextAttempt = entry.attemptCount() + 1;
            MemoryThreadIntakeStatus status =
                    nextAttempt >= maxAttempts
                            ? MemoryThreadIntakeStatus.FAILED
                            : MemoryThreadIntakeStatus.PENDING;
            MemoryThreadIntakeOutboxEntry recoveredEntry =
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            status,
                            nextAttempt,
                            null,
                            null,
                            status == MemoryThreadIntakeStatus.FAILED ? "lease expired" : null,
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            status == MemoryThreadIntakeStatus.FAILED ? now : null);
            state.outboxByTriggerItemId.put(entry.triggerItemId(), recoveredEntry);
            recovered++;
        }
        refreshRuntimeCounters(memoryId, state);
        return recovered;
    }

    @Override
    public void finalizeOutboxSuccess(
            MemoryId memoryId, long triggerItemId, long lastProcessedItemId, Instant finalizedAt) {
        MemoryState state = state(memoryId);
        MemoryThreadIntakeOutboxEntry entry = state.outboxByTriggerItemId.get(triggerItemId);
        if (entry == null) {
            return;
        }
        state.outboxByTriggerItemId.put(
                triggerItemId,
                new MemoryThreadIntakeOutboxEntry(
                        entry.memoryId(),
                        entry.triggerItemId(),
                        entry.enqueueGeneration(),
                        MemoryThreadIntakeStatus.COMPLETED,
                        entry.attemptCount(),
                        entry.claimedAt(),
                        entry.leaseExpiresAt(),
                        null,
                        lastProcessedItemId,
                        entry.enqueuedAt(),
                        finalizedAt));
        refreshRuntimeCounters(memoryId, state);
    }

    @Override
    public void finalizeOutboxSkippedPrefix(
            MemoryId memoryId, long rebuildCutoffItemId, Instant finalizedAt) {
        MemoryState state = state(memoryId);
        for (MemoryThreadIntakeOutboxEntry entry : listOutbox(memoryId)) {
            if (entry.triggerItemId() > rebuildCutoffItemId) {
                continue;
            }
            state.outboxByTriggerItemId.put(
                    entry.triggerItemId(),
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.SKIPPED,
                            entry.attemptCount(),
                            entry.claimedAt(),
                            entry.leaseExpiresAt(),
                            entry.failureReason(),
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            finalizedAt));
        }
        refreshRuntimeCounters(memoryId, state);
    }

    @Override
    public void markRebuildRequired(MemoryId memoryId, String reason) {
        MemoryState state = state(memoryId);
        MemoryThreadRuntimeState current = state.runtime;
        state.runtime =
                new MemoryThreadRuntimeState(
                        memoryId.toIdentifier(),
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        pendingCount(state),
                        failedCount(state),
                        current != null ? current.lastEnqueuedItemId() : null,
                        current != null ? current.lastProcessedItemId() : null,
                        current != null && current.rebuildInProgress(),
                        current != null ? current.rebuildCutoffItemId() : null,
                        current != null ? current.rebuildEpoch() : 0L,
                        current != null ? current.materializationPolicyVersion() : "v1",
                        reason,
                        Instant.now());
    }

    @Override
    public boolean beginRebuild(
            MemoryId memoryId, String materializationPolicyVersion, long rebuildCutoffItemId) {
        MemoryState state = state(memoryId);
        if (state.runtime != null && state.runtime.rebuildInProgress()) {
            return false;
        }
        MemoryThreadRuntimeState current = state.runtime;
        long nextEpoch = current != null ? current.rebuildEpoch() + 1L : 1L;
        state.runtime =
                new MemoryThreadRuntimeState(
                        memoryId.toIdentifier(),
                        current != null
                                ? current.projectionState()
                                : MemoryThreadProjectionState.REBUILD_REQUIRED,
                        pendingCount(state),
                        failedCount(state),
                        current != null ? current.lastEnqueuedItemId() : null,
                        current != null ? current.lastProcessedItemId() : null,
                        true,
                        rebuildCutoffItemId,
                        nextEpoch,
                        materializationPolicyVersion,
                        current != null ? current.invalidationReason() : "rebuild bootstrap",
                        Instant.now());
        return true;
    }

    @Override
    public boolean commitClaimedIntakeReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            long replayCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        MemoryState state = state(memoryId);
        if (!claimsStillMatch(state, claimedEntries) || !rebuildEpochMatches(state, runtimeState)) {
            return false;
        }

        Map<Long, MemoryThreadIntakeOutboxEntry> updatedOutbox =
                new ConcurrentHashMap<>(state.outboxByTriggerItemId);
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxEntry entry = updatedOutbox.get(claim.triggerItemId());
            updatedOutbox.put(
                    claim.triggerItemId(),
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.COMPLETED,
                            entry.attemptCount(),
                            entry.claimedAt(),
                            entry.leaseExpiresAt(),
                            null,
                            replayCutoffItemId,
                            entry.enqueuedAt(),
                            finalizedAt));
        }

        commitReplaySuccess(
                state, threads, events, memberships, runtimeState, finalizedAt, updatedOutbox);
        return true;
    }

    @Override
    public void finalizeClaimedIntakeFailure(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            String reason,
            int maxAttempts,
            Instant finalizedAt) {
        MemoryState state = state(memoryId);
        boolean changed = false;
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxEntry entry =
                    state.outboxByTriggerItemId.get(claim.triggerItemId());
            if (!matchesClaim(entry, claim)) {
                continue;
            }
            int attempts = Math.max(entry.attemptCount() + 1, maxAttempts);
            state.outboxByTriggerItemId.put(
                    claim.triggerItemId(),
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.FAILED,
                            attempts,
                            entry.claimedAt(),
                            entry.leaseExpiresAt(),
                            reason,
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            finalizedAt));
            changed = true;
        }
        if (changed) {
            refreshRuntimeCounters(memoryId, state);
        }
    }

    @Override
    public void releaseClaims(MemoryId memoryId, List<MemoryThreadIntakeClaim> claimedEntries) {
        MemoryState state = state(memoryId);
        boolean changed = false;
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxEntry entry =
                    state.outboxByTriggerItemId.get(claim.triggerItemId());
            if (!matchesClaim(entry, claim)) {
                continue;
            }
            state.outboxByTriggerItemId.put(
                    claim.triggerItemId(),
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.PENDING,
                            entry.attemptCount(),
                            null,
                            null,
                            null,
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            null));
            changed = true;
        }
        if (changed) {
            refreshRuntimeCounters(memoryId, state);
        }
    }

    @Override
    public void commitRebuildReplaySuccess(
            MemoryId memoryId,
            long rebuildCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        MemoryState state = state(memoryId);
        Map<Long, MemoryThreadIntakeOutboxEntry> updatedOutbox =
                new ConcurrentHashMap<>(state.outboxByTriggerItemId);
        for (MemoryThreadIntakeOutboxEntry entry : listOutbox(memoryId)) {
            if (entry.triggerItemId() > rebuildCutoffItemId) {
                continue;
            }
            updatedOutbox.put(
                    entry.triggerItemId(),
                    new MemoryThreadIntakeOutboxEntry(
                            entry.memoryId(),
                            entry.triggerItemId(),
                            entry.enqueueGeneration(),
                            MemoryThreadIntakeStatus.SKIPPED,
                            entry.attemptCount(),
                            entry.claimedAt(),
                            entry.leaseExpiresAt(),
                            entry.failureReason(),
                            entry.lastProcessedItemId(),
                            entry.enqueuedAt(),
                            finalizedAt));
        }

        commitReplaySuccess(
                state, threads, events, memberships, runtimeState, finalizedAt, updatedOutbox);
    }

    @Override
    public void replaceProjection(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        MemoryState state = state(memoryId);
        state.threadsByKey =
                threads == null
                        ? new LinkedHashMap<>()
                        : threads.stream()
                                .collect(
                                        Collectors.toMap(
                                                MemoryThreadProjection::threadKey,
                                                projection -> projection,
                                                (left, right) -> right,
                                                LinkedHashMap::new));
        state.eventsByThreadKey = groupedEvents(events);
        state.membershipsByThreadKey = groupedMemberships(memberships);
        state.threadKeysByItemId = membershipsByItemId(state.membershipsByThreadKey);
        state.runtime = runtimeState;
        refreshRuntimeCounters(memoryId, state);
    }

    protected void beforeAtomicReplayCommit() {}

    private void commitReplaySuccess(
            MemoryState state,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt,
            Map<Long, MemoryThreadIntakeOutboxEntry> updatedOutbox) {
        Map<String, MemoryThreadProjection> updatedThreads =
                threads == null
                        ? new LinkedHashMap<>()
                        : threads.stream()
                                .collect(
                                        Collectors.toMap(
                                                MemoryThreadProjection::threadKey,
                                                projection -> projection,
                                                (left, right) -> right,
                                                LinkedHashMap::new));
        Map<String, List<MemoryThreadEvent>> updatedEvents = groupedEvents(events);
        Map<String, List<MemoryThreadMembership>> updatedMemberships =
                groupedMemberships(memberships);
        Map<Long, List<String>> updatedThreadKeysByItemId = membershipsByItemId(updatedMemberships);
        MemoryThreadRuntimeState adjustedRuntime =
                adjustedRuntime(runtimeState, finalizedAt, updatedOutbox);

        beforeAtomicReplayCommit();

        state.threadsByKey = updatedThreads;
        state.eventsByThreadKey = updatedEvents;
        state.membershipsByThreadKey = updatedMemberships;
        state.threadKeysByItemId = updatedThreadKeysByItemId;
        state.outboxByTriggerItemId = updatedOutbox;
        state.runtime = adjustedRuntime;
    }

    private static MemoryThreadRuntimeState adjustedRuntime(
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt,
            Map<Long, MemoryThreadIntakeOutboxEntry> updatedOutbox) {
        if (runtimeState == null) {
            return null;
        }
        return new MemoryThreadRuntimeState(
                runtimeState.memoryId(),
                runtimeState.projectionState(),
                pendingCount(updatedOutbox),
                failedCount(updatedOutbox),
                runtimeState.lastEnqueuedItemId(),
                runtimeState.lastProcessedItemId(),
                runtimeState.rebuildInProgress(),
                runtimeState.rebuildCutoffItemId(),
                runtimeState.rebuildEpoch(),
                runtimeState.materializationPolicyVersion(),
                runtimeState.invalidationReason(),
                finalizedAt != null ? finalizedAt : runtimeState.updatedAt());
    }

    private static Map<String, List<MemoryThreadEvent>> groupedEvents(
            List<MemoryThreadEvent> events) {
        Map<String, List<MemoryThreadEvent>> grouped = new LinkedHashMap<>();
        if (events == null) {
            return grouped;
        }
        for (MemoryThreadEvent event : events) {
            grouped.computeIfAbsent(event.threadKey(), ignored -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    private static Map<String, List<MemoryThreadMembership>> groupedMemberships(
            List<MemoryThreadMembership> memberships) {
        Map<String, List<MemoryThreadMembership>> grouped = new LinkedHashMap<>();
        if (memberships == null) {
            return grouped;
        }
        for (MemoryThreadMembership membership : memberships) {
            grouped.computeIfAbsent(membership.threadKey(), ignored -> new ArrayList<>())
                    .add(membership);
        }
        return grouped;
    }

    private static Map<Long, List<String>> membershipsByItemId(
            Map<String, List<MemoryThreadMembership>> membershipsByThreadKey) {
        Map<Long, List<String>> threadKeysByItemId = new LinkedHashMap<>();
        for (Map.Entry<String, List<MemoryThreadMembership>> entry :
                membershipsByThreadKey.entrySet()) {
            for (MemoryThreadMembership membership : entry.getValue()) {
                threadKeysByItemId
                        .computeIfAbsent(membership.itemId(), ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        return threadKeysByItemId;
    }

    private void refreshRuntimeCounters(MemoryId memoryId, MemoryState state) {
        if (state.runtime == null) {
            return;
        }
        state.runtime =
                new MemoryThreadRuntimeState(
                        state.runtime.memoryId(),
                        state.runtime.projectionState(),
                        pendingCount(state),
                        failedCount(state),
                        state.runtime.lastEnqueuedItemId(),
                        state.runtime.lastProcessedItemId(),
                        state.runtime.rebuildInProgress(),
                        state.runtime.rebuildCutoffItemId(),
                        state.runtime.rebuildEpoch(),
                        state.runtime.materializationPolicyVersion(),
                        state.runtime.invalidationReason(),
                        state.runtime.updatedAt());
    }

    private static MemoryThreadIntakeClaim toClaim(MemoryThreadIntakeOutboxEntry entry) {
        return new MemoryThreadIntakeClaim(
                entry.triggerItemId(),
                entry.enqueueGeneration(),
                entry.claimedAt(),
                entry.leaseExpiresAt());
    }

    private static boolean claimsStillMatch(
            MemoryState state, List<MemoryThreadIntakeClaim> claimedEntries) {
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            if (!matchesClaim(state.outboxByTriggerItemId.get(claim.triggerItemId()), claim)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesClaim(
            MemoryThreadIntakeOutboxEntry entry, MemoryThreadIntakeClaim claim) {
        return entry != null
                && entry.enqueueGeneration() == claim.enqueueGeneration()
                && entry.status() == MemoryThreadIntakeStatus.PROCESSING;
    }

    private static boolean rebuildEpochMatches(
            MemoryState state, MemoryThreadRuntimeState runtimeState) {
        if (runtimeState == null) {
            return state.runtime == null;
        }
        return state.runtime != null && state.runtime.rebuildEpoch() == runtimeState.rebuildEpoch();
    }

    private static long pendingCount(MemoryState state) {
        return pendingCount(state.outboxByTriggerItemId);
    }

    private static long failedCount(MemoryState state) {
        return failedCount(state.outboxByTriggerItemId);
    }

    private static long pendingCount(
            Map<Long, MemoryThreadIntakeOutboxEntry> outboxByTriggerItemId) {
        return outboxByTriggerItemId.values().stream()
                .filter(entry -> entry.status() == MemoryThreadIntakeStatus.PENDING)
                .count();
    }

    private static long failedCount(
            Map<Long, MemoryThreadIntakeOutboxEntry> outboxByTriggerItemId) {
        return outboxByTriggerItemId.values().stream()
                .filter(entry -> entry.status() == MemoryThreadIntakeStatus.FAILED)
                .count();
    }

    private MemoryState state(MemoryId memoryId) {
        return stateByMemoryId.computeIfAbsent(
                memoryId.toIdentifier(), ignored -> new MemoryState());
    }

    private static final class MemoryState {
        private Map<String, MemoryThreadProjection> threadsByKey = new LinkedHashMap<>();
        private Map<String, List<MemoryThreadEvent>> eventsByThreadKey = new LinkedHashMap<>();
        private Map<String, List<MemoryThreadMembership>> membershipsByThreadKey =
                new LinkedHashMap<>();
        private Map<Long, List<String>> threadKeysByItemId = new LinkedHashMap<>();
        private Map<Long, MemoryThreadIntakeOutboxEntry> outboxByTriggerItemId =
                new ConcurrentHashMap<>();
        private MemoryThreadRuntimeState runtime;
    }
}
