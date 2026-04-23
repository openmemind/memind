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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Disabled-mode V1 thread projection store.
 */
public final class NoOpThreadProjectionStore implements ThreadProjectionStore {

    public static final NoOpThreadProjectionStore INSTANCE = new NoOpThreadProjectionStore();

    private NoOpThreadProjectionStore() {}

    @Override
    public void ensureRuntime(MemoryId memoryId, String materializationPolicyVersion) {}

    @Override
    public Optional<MemoryThreadRuntimeState> getRuntime(MemoryId memoryId) {
        return Optional.empty();
    }

    @Override
    public List<MemoryThreadProjection> listThreads(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public Optional<MemoryThreadProjection> getThread(MemoryId memoryId, String threadKey) {
        return Optional.empty();
    }

    @Override
    public List<MemoryThreadEvent> listEvents(MemoryId memoryId, String threadKey) {
        return List.of();
    }

    @Override
    public List<MemoryThreadMembership> listMemberships(MemoryId memoryId, String threadKey) {
        return List.of();
    }

    @Override
    public List<MemoryThreadProjection> listThreadsByItemId(MemoryId memoryId, long itemId) {
        return List.of();
    }

    @Override
    public void enqueue(MemoryId memoryId, long triggerItemId) {}

    @Override
    public void enqueueReplay(MemoryId memoryId, long replayCutoffItemId) {}

    @Override
    public List<MemoryThreadIntakeOutboxEntry> listOutbox(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public List<MemoryThreadIntakeClaim> claimPending(
            MemoryId memoryId, Instant claimedAt, Instant leaseExpiresAt, int batchSize) {
        return List.of();
    }

    @Override
    public int recoverAbandoned(MemoryId memoryId, Instant now, int maxAttempts) {
        return 0;
    }

    @Override
    public void finalizeOutboxSuccess(
            MemoryId memoryId, long triggerItemId, long lastProcessedItemId, Instant finalizedAt) {}

    @Override
    public void finalizeClaimedIntakeFailure(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            String reason,
            int maxAttempts,
            Instant finalizedAt) {}

    @Override
    public void finalizeOutboxSkippedPrefix(
            MemoryId memoryId, long rebuildCutoffItemId, Instant finalizedAt) {}

    @Override
    public void markRebuildRequired(MemoryId memoryId, String reason) {}

    @Override
    public boolean beginRebuild(
            MemoryId memoryId, String materializationPolicyVersion, long rebuildCutoffItemId) {
        return false;
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
        return false;
    }

    @Override
    public void releaseClaims(MemoryId memoryId, List<MemoryThreadIntakeClaim> claimedEntries) {}

    @Override
    public void commitRebuildReplaySuccess(
            MemoryId memoryId,
            long rebuildCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {}

    @Override
    public void replaceProjection(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {}
}
