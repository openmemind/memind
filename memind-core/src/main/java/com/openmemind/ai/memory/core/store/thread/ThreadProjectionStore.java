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
 * V1 thread projection persistence contract.
 */
public interface ThreadProjectionStore {

    void ensureRuntime(MemoryId memoryId, String materializationPolicyVersion);

    Optional<MemoryThreadRuntimeState> getRuntime(MemoryId memoryId);

    List<MemoryThreadProjection> listThreads(MemoryId memoryId);

    Optional<MemoryThreadProjection> getThread(MemoryId memoryId, String threadKey);

    List<MemoryThreadEvent> listEvents(MemoryId memoryId, String threadKey);

    List<MemoryThreadMembership> listMemberships(MemoryId memoryId, String threadKey);

    List<MemoryThreadProjection> listThreadsByItemId(MemoryId memoryId, long itemId);

    void enqueue(MemoryId memoryId, long triggerItemId);

    void enqueueReplay(MemoryId memoryId, long replayCutoffItemId);

    List<MemoryThreadIntakeOutboxEntry> listOutbox(MemoryId memoryId);

    List<MemoryThreadIntakeClaim> claimPending(
            MemoryId memoryId, Instant claimedAt, Instant leaseExpiresAt, int batchSize);

    int recoverAbandoned(MemoryId memoryId, Instant now, int maxAttempts);

    void finalizeOutboxSuccess(
            MemoryId memoryId, long triggerItemId, long lastProcessedItemId, Instant finalizedAt);

    void finalizeClaimedIntakeFailure(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            String reason,
            int maxAttempts,
            Instant finalizedAt);

    void finalizeOutboxSkippedPrefix(
            MemoryId memoryId, long rebuildCutoffItemId, Instant finalizedAt);

    void markRebuildRequired(MemoryId memoryId, String reason);

    boolean beginRebuild(
            MemoryId memoryId, String materializationPolicyVersion, long rebuildCutoffItemId);

    boolean commitClaimedIntakeReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            long replayCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt);

    void releaseClaims(MemoryId memoryId, List<MemoryThreadIntakeClaim> claimedEntries);

    void commitRebuildReplaySuccess(
            MemoryId memoryId,
            long rebuildCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt);

    void replaceProjection(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt);
}
