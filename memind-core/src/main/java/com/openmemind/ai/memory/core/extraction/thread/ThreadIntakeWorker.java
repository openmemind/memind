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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable incremental worker that consumes the thread intake outbox.
 */
public class ThreadIntakeWorker {

    private static final Logger log = LoggerFactory.getLogger(ThreadIntakeWorker.class);

    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);
    private static final int DEFAULT_BATCH_SIZE = 64;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final ThreadProjectionStore store;
    private final ThreadMaterializationPolicy policy;
    private final ThreadProjectionMaterializer materializer;

    public ThreadIntakeWorker(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadMaterializationPolicy policy) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.materializer =
                new ThreadProjectionMaterializer(
                        Objects.requireNonNull(itemOperations, "itemOperations"),
                        Objects.requireNonNull(graphOperations, "graphOperations"),
                        policy);
    }

    public void wake(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        store.ensureRuntime(memoryId, policy.version());

        Instant now = Instant.now();
        store.recoverAbandoned(memoryId, now, DEFAULT_MAX_ATTEMPTS);

        while (true) {
            Instant claimedAt = Instant.now();
            List<MemoryThreadIntakeOutboxEntry> claimed =
                    store.claimPending(
                            memoryId, claimedAt, claimedAt.plus(DEFAULT_LEASE), DEFAULT_BATCH_SIZE);
            if (claimed.isEmpty()) {
                return;
            }
            for (MemoryThreadIntakeOutboxEntry entry : claimed) {
                process(memoryId, entry);
            }
        }
    }

    private void process(MemoryId memoryId, MemoryThreadIntakeOutboxEntry entry) {
        try {
            ThreadProjectionMaterializer.MaterializedProjection projection =
                    materializer.materializeUpTo(memoryId, entry.triggerItemId());
            Instant finalizedAt = Instant.now();
            store.finalizeOutboxSuccess(
                    memoryId, entry.triggerItemId(), entry.triggerItemId(), finalizedAt);
            MemoryThreadRuntimeState current = store.getRuntime(memoryId).orElse(null);
            store.replaceProjection(
                    memoryId,
                    projection.threads(),
                    projection.events(),
                    projection.memberships(),
                    availableRuntime(
                            memoryId,
                            current,
                            projection.lastProcessedItemId() != null
                                    ? projection.lastProcessedItemId()
                                    : entry.triggerItemId(),
                            finalizedAt),
                    finalizedAt);
        } catch (RuntimeException e) {
            Instant failedAt = Instant.now();
            log.warn(
                    "Thread intake worker failed for memoryId={} triggerItemId={}",
                    memoryId,
                    entry.triggerItemId(),
                    e);
            store.finalizeOutboxFailure(
                    memoryId,
                    entry.triggerItemId(),
                    failureReason(e),
                    DEFAULT_MAX_ATTEMPTS,
                    failedAt);
            store.markRebuildRequired(memoryId, "intake failure");
        }
    }

    private MemoryThreadRuntimeState availableRuntime(
            MemoryId memoryId,
            MemoryThreadRuntimeState current,
            Long lastProcessedItemId,
            Instant updatedAt) {
        return new MemoryThreadRuntimeState(
                memoryId.toIdentifier(),
                MemoryThreadProjectionState.AVAILABLE,
                current != null ? current.pendingCount() : 0L,
                current != null ? current.failedCount() : 0L,
                current != null && current.lastEnqueuedItemId() != null
                        ? current.lastEnqueuedItemId()
                        : lastProcessedItemId,
                lastProcessedItemId,
                false,
                null,
                policy.version(),
                null,
                updatedAt);
    }

    private static String failureReason(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
