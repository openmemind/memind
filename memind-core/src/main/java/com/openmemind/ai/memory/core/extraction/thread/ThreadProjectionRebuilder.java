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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full rebuild entrypoint for thread projection state.
 */
public class ThreadProjectionRebuilder {

    private static final Logger log = LoggerFactory.getLogger(ThreadProjectionRebuilder.class);

    private final ThreadProjectionStore store;
    private final ThreadMaterializationPolicy policy;
    private final ItemOperations itemOperations;
    private final ThreadProjectionMaterializer materializer;

    public ThreadProjectionRebuilder(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadMaterializationPolicy policy) {
        this.store = Objects.requireNonNull(store, "store");
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.materializer =
                new ThreadProjectionMaterializer(itemOperations, graphOperations, policy);
    }

    public void rebuild(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        long cutoff =
                itemOperations.listItems(memoryId).stream()
                        .map(item -> item.id())
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0L);
        rebuild(memoryId, cutoff);
    }

    public void rebuild(MemoryId memoryId, long rebuildCutoffItemId) {
        Objects.requireNonNull(memoryId, "memoryId");
        store.ensureRuntime(memoryId, policy.version());
        if (!store.beginRebuild(memoryId, policy.version(), rebuildCutoffItemId)) {
            return;
        }

        try {
            ThreadProjectionMaterializer.MaterializedProjection projection =
                    materializer.materializeUpTo(memoryId, rebuildCutoffItemId);
            Instant finalizedAt = Instant.now();
            store.finalizeOutboxSkippedPrefix(memoryId, rebuildCutoffItemId, finalizedAt);
            MemoryThreadRuntimeState current = store.getRuntime(memoryId).orElse(null);
            Long lastProcessedItemId =
                    projection.lastProcessedItemId() != null
                            ? projection.lastProcessedItemId()
                            : (rebuildCutoffItemId > 0L ? rebuildCutoffItemId : null);
            store.replaceProjection(
                    memoryId,
                    projection.threads(),
                    projection.events(),
                    projection.memberships(),
                    availableRuntime(memoryId, current, lastProcessedItemId, finalizedAt),
                    finalizedAt);
        } catch (RuntimeException e) {
            log.warn(
                    "Thread projection rebuild failed for memoryId={} cutoff={}",
                    memoryId,
                    rebuildCutoffItemId,
                    e);
            store.markRebuildRequired(memoryId, "rebuild failure");
            throw e;
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
                current != null ? current.lastEnqueuedItemId() : null,
                lastProcessedItemId,
                false,
                null,
                policy.version(),
                null,
                updatedAt);
    }
}
