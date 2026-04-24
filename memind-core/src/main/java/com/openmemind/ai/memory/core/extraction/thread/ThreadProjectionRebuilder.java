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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import java.time.Instant;
import java.util.List;
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
    private final ThreadReplaySuccessListener replaySuccessListener;
    private final ThreadDerivationMetrics metrics;

    public ThreadProjectionRebuilder(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadMaterializationPolicy policy) {
        this(
                store,
                itemOperations,
                graphOperations,
                NoOpThreadEnrichmentInputStore.INSTANCE,
                policy,
                ThreadReplaySuccessListener.NOOP,
                ThreadDerivationMetrics.NOOP);
    }

    public ThreadProjectionRebuilder(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadEnrichmentInputStore enrichmentInputStore,
            ThreadMaterializationPolicy policy,
            ThreadReplaySuccessListener replaySuccessListener) {
        this(
                store,
                itemOperations,
                graphOperations,
                enrichmentInputStore,
                policy,
                replaySuccessListener,
                ThreadDerivationMetrics.NOOP);
    }

    public ThreadProjectionRebuilder(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadEnrichmentInputStore enrichmentInputStore,
            ThreadMaterializationPolicy policy,
            ThreadReplaySuccessListener replaySuccessListener,
            ThreadDerivationMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.materializer =
                new ThreadProjectionMaterializer(
                        itemOperations,
                        Objects.requireNonNull(graphOperations, "graphOperations"),
                        Objects.requireNonNull(enrichmentInputStore, "enrichmentInputStore"),
                        policy,
                        metrics);
        this.replaySuccessListener =
                Objects.requireNonNull(replaySuccessListener, "replaySuccessListener");
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
        List<Long> coveredTriggerItemIds =
                store.listOutbox(memoryId).stream()
                        .map(entry -> entry.triggerItemId())
                        .filter(triggerItemId -> triggerItemId <= rebuildCutoffItemId)
                        .sorted()
                        .toList();

        try {
            ThreadProjectionMaterializer.MaterializedProjection projection =
                    materializer.materializeUpTo(memoryId, rebuildCutoffItemId);
            Instant finalizedAt = Instant.now();
            MemoryThreadRuntimeState current = store.getRuntime(memoryId).orElse(null);
            Long lastProcessedItemId =
                    projection.lastProcessedItemId() != null
                            ? projection.lastProcessedItemId()
                            : (rebuildCutoffItemId > 0L ? rebuildCutoffItemId : null);
            store.commitRebuildReplaySuccess(
                    memoryId,
                    rebuildCutoffItemId,
                    projection.threads(),
                    projection.events(),
                    projection.memberships(),
                    availableRuntime(memoryId, current, lastProcessedItemId, finalizedAt),
                    finalizedAt);
            metrics.onReplayPublished(ThreadReplayOrigin.REBUILD);
            if (containsGroupRelationshipThread(projection.threads())) {
                metrics.onGroupRelationshipPublished();
            }
            notifyReplaySuccess(
                    memoryId, coveredTriggerItemIds, projection, rebuildCutoffItemId, finalizedAt);
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
                current != null ? current.rebuildEpoch() : 0L,
                policy.version(),
                null,
                updatedAt);
    }

    private void notifyReplaySuccess(
            MemoryId memoryId,
            List<Long> coveredTriggerItemIds,
            ThreadProjectionMaterializer.MaterializedProjection projection,
            long cutoffItemId,
            Instant finalizedAt) {
        try {
            replaySuccessListener.afterSuccessfulReplay(
                    new ThreadReplaySuccessContext(
                            memoryId,
                            ThreadReplayOrigin.REBUILD,
                            cutoffItemId,
                            coveredTriggerItemIds,
                            projection.threads(),
                            projection.events(),
                            projection.memberships(),
                            policy.version(),
                            finalizedAt));
        } catch (RuntimeException error) {
            log.warn(
                    "Thread replay success listener failed open for memoryId={} cutoff={}",
                    memoryId,
                    cutoffItemId,
                    error);
        }
    }

    private static boolean containsGroupRelationshipThread(List<MemoryThreadProjection> threads) {
        return threads.stream()
                .anyMatch(thread -> "relationship_group".equals(thread.anchorKind()));
    }
}
