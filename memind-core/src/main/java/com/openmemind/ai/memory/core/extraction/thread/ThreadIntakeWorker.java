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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
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
    private final ThreadReplaySuccessListener replaySuccessListener;
    private final ThreadDerivationMetrics metrics;

    public ThreadIntakeWorker(
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

    public ThreadIntakeWorker(
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

    public ThreadIntakeWorker(
            ThreadProjectionStore store,
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            ThreadEnrichmentInputStore enrichmentInputStore,
            ThreadMaterializationPolicy policy,
            ThreadReplaySuccessListener replaySuccessListener,
            ThreadDerivationMetrics metrics) {
        this(
                store,
                new ThreadProjectionMaterializer(
                        Objects.requireNonNull(itemOperations, "itemOperations"),
                        Objects.requireNonNull(graphOperations, "graphOperations"),
                        Objects.requireNonNull(enrichmentInputStore, "enrichmentInputStore"),
                        policy,
                        metrics),
                policy,
                replaySuccessListener,
                metrics);
    }

    ThreadIntakeWorker(
            ThreadProjectionStore store,
            ThreadProjectionMaterializer materializer,
            ThreadMaterializationPolicy policy,
            ThreadReplaySuccessListener replaySuccessListener) {
        this(
                store,
                materializer,
                policy,
                replaySuccessListener,
                ThreadDerivationMetrics.NOOP);
    }

    ThreadIntakeWorker(
            ThreadProjectionStore store,
            ThreadProjectionMaterializer materializer,
            ThreadMaterializationPolicy policy,
            ThreadReplaySuccessListener replaySuccessListener,
            ThreadDerivationMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.replaySuccessListener =
                Objects.requireNonNull(replaySuccessListener, "replaySuccessListener");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public void wake(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        store.ensureRuntime(memoryId, policy.version());

        Instant now = Instant.now();
        store.recoverAbandoned(memoryId, now, DEFAULT_MAX_ATTEMPTS);

        while (true) {
            MemoryThreadRuntimeState observedRuntime = store.getRuntime(memoryId).orElse(null);
            long observedRebuildEpoch = observedRuntime != null ? observedRuntime.rebuildEpoch() : 0L;
            Instant claimedAt = Instant.now();
            List<MemoryThreadIntakeClaim> claimed =
                    store.claimPending(
                            memoryId, claimedAt, claimedAt.plus(DEFAULT_LEASE), DEFAULT_BATCH_SIZE);
            if (claimed.isEmpty()) {
                return;
            }
            metrics.onClaimedBatch(claimed.size());
            if (!processBatch(memoryId, claimed, observedRebuildEpoch)) {
                return;
            }
        }
    }

    private boolean processBatch(
            MemoryId memoryId, List<MemoryThreadIntakeClaim> claimed, long observedRebuildEpoch) {
        long replayCutoffItemId =
                claimed.stream().mapToLong(MemoryThreadIntakeClaim::triggerItemId).max().orElseThrow();
        try {
            ThreadProjectionMaterializer.MaterializedProjection projection =
                    materializer.materializeUpTo(memoryId, replayCutoffItemId);
            Instant finalizedAt = Instant.now();
            MemoryThreadRuntimeState current = store.getRuntime(memoryId).orElse(null);
            boolean committed =
                    store.commitClaimedIntakeReplaySuccess(
                            memoryId,
                            claimed,
                            replayCutoffItemId,
                            projection.threads(),
                            projection.events(),
                            projection.memberships(),
                            availableRuntime(
                                    memoryId,
                                    current,
                                    projection.lastProcessedItemId() != null
                                            ? projection.lastProcessedItemId()
                                            : replayCutoffItemId,
                                    finalizedAt,
                                    observedRebuildEpoch),
                            finalizedAt);
            if (!committed) {
                store.releaseClaims(memoryId, claimed);
                return false;
            }
            notifyReplaySuccess(memoryId, claimed, projection, replayCutoffItemId, finalizedAt);
            return true;
        } catch (RuntimeException e) {
            Instant failedAt = Instant.now();
            log.warn(
                    "Thread intake worker failed for memoryId={} replayCutoffItemId={}",
                    memoryId,
                    replayCutoffItemId,
                    e);
            store.finalizeClaimedIntakeFailure(
                    memoryId,
                    claimed,
                    failureReason(e),
                    DEFAULT_MAX_ATTEMPTS,
                    failedAt);
            store.markRebuildRequired(memoryId, "intake failure");
            return false;
        }
    }

    private MemoryThreadRuntimeState availableRuntime(
            MemoryId memoryId,
            MemoryThreadRuntimeState current,
            Long lastProcessedItemId,
            Instant updatedAt,
            long observedRebuildEpoch) {
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
                observedRebuildEpoch,
                policy.version(),
                null,
                updatedAt);
    }

    private static String failureReason(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void notifyReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimed,
            ThreadProjectionMaterializer.MaterializedProjection projection,
            long cutoffItemId,
            Instant finalizedAt) {
        metrics.onCoalescedReplayCutoffs(claimed.size());
        metrics.onReplayPublished(ThreadReplayOrigin.INTAKE_BATCH);
        if (containsGroupRelationshipThread(projection.threads())) {
            metrics.onGroupRelationshipPublished();
        }
        try {
            replaySuccessListener.afterSuccessfulReplay(
                    new ThreadReplaySuccessContext(
                            memoryId,
                            ThreadReplayOrigin.INTAKE_BATCH,
                            cutoffItemId,
                            claimed.stream()
                                    .map(MemoryThreadIntakeClaim::triggerItemId)
                                    .sorted()
                                    .toList(),
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
        return threads.stream().anyMatch(thread -> "relationship_group".equals(thread.anchorKind()));
    }
}
