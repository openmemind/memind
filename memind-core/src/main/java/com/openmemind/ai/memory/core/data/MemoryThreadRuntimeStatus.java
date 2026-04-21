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
package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import java.time.Instant;
import java.util.Objects;

/**
 * Runtime-visible memory-thread status.
 */
public final class MemoryThreadRuntimeStatus {

    private final boolean memoryThreadEnabled;
    private final boolean derivationEnabled;
    private final boolean derivationAvailable;
    private final String forcedDisabledReason;
    private final MemoryThreadProjectionState projectionState;
    private final int queueDepth;
    private final long pendingCount;
    private final Instant lastSuccessAt;
    private final Instant lastFailureAt;
    private final long failureCount;
    private final long failedCount;
    private final boolean rebuildInProgress;
    private final Long lastProcessedItemId;
    private final String materializationPolicyVersion;
    private final Instant updatedAt;
    private final String invalidationReason;

    public MemoryThreadRuntimeStatus(
            boolean memoryThreadEnabled,
            boolean derivationEnabled,
            boolean derivationAvailable,
            String forcedDisabledReason,
            int queueDepth,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            long failureCount) {
        this(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                derivationAvailable
                        ? MemoryThreadProjectionState.AVAILABLE
                        : MemoryThreadProjectionState.REBUILD_REQUIRED,
                queueDepth,
                queueDepth,
                lastSuccessAt,
                lastFailureAt,
                failureCount,
                failureCount,
                false,
                null,
                null,
                lastSuccessAt != null ? lastSuccessAt : lastFailureAt,
                forcedDisabledReason);
    }

    public MemoryThreadRuntimeStatus(
            boolean memoryThreadEnabled,
            boolean derivationEnabled,
            boolean derivationAvailable,
            String forcedDisabledReason,
            MemoryThreadProjectionState projectionState,
            long pendingCount,
            long failedCount,
            boolean rebuildInProgress,
            Long lastProcessedItemId,
            String materializationPolicyVersion,
            Instant updatedAt,
            String invalidationReason) {
        this(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                projectionState,
                safeQueueDepth(pendingCount),
                pendingCount,
                projectionState == MemoryThreadProjectionState.AVAILABLE ? updatedAt : null,
                projectionState == MemoryThreadProjectionState.REBUILD_REQUIRED && failedCount > 0
                        ? updatedAt
                        : null,
                failedCount,
                failedCount,
                rebuildInProgress,
                lastProcessedItemId,
                materializationPolicyVersion,
                updatedAt,
                invalidationReason);
    }

    private MemoryThreadRuntimeStatus(
            boolean memoryThreadEnabled,
            boolean derivationEnabled,
            boolean derivationAvailable,
            String forcedDisabledReason,
            MemoryThreadProjectionState projectionState,
            int queueDepth,
            long pendingCount,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            long failureCount,
            long failedCount,
            boolean rebuildInProgress,
            Long lastProcessedItemId,
            String materializationPolicyVersion,
            Instant updatedAt,
            String invalidationReason) {
        if (queueDepth < 0) {
            throw new IllegalArgumentException("queueDepth must be non-negative");
        }
        if (pendingCount < 0) {
            throw new IllegalArgumentException("pendingCount must be non-negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must be non-negative");
        }
        if (failedCount < 0) {
            throw new IllegalArgumentException("failedCount must be non-negative");
        }
        this.memoryThreadEnabled = memoryThreadEnabled;
        this.derivationEnabled = derivationEnabled;
        this.derivationAvailable = derivationAvailable;
        this.forcedDisabledReason = forcedDisabledReason;
        this.projectionState = Objects.requireNonNull(projectionState, "projectionState");
        this.queueDepth = queueDepth;
        this.pendingCount = pendingCount;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.failureCount = failureCount;
        this.failedCount = failedCount;
        this.rebuildInProgress = rebuildInProgress;
        this.lastProcessedItemId = lastProcessedItemId;
        this.materializationPolicyVersion = materializationPolicyVersion;
        this.updatedAt = updatedAt;
        this.invalidationReason = invalidationReason;
    }

    public static MemoryThreadRuntimeStatus disabled(String reason) {
        return new MemoryThreadRuntimeStatus(false, false, false, reason, 0, null, null, 0L);
    }

    public static MemoryThreadRuntimeStatus fromRuntimeState(
            MemoryThreadRuntimeState state,
            boolean memoryThreadEnabled,
            boolean derivationEnabled,
            String forcedDisabledReason) {
        if (!memoryThreadEnabled) {
            return disabled(
                    forcedDisabledReason != null ? forcedDisabledReason : "memoryThread disabled");
        }
        if (!derivationEnabled) {
            return disabled(
                    forcedDisabledReason != null
                            ? forcedDisabledReason
                            : "memoryThread derivation disabled");
        }
        if (state == null) {
            return new MemoryThreadRuntimeStatus(
                    true,
                    true,
                    false,
                    forcedDisabledReason,
                    MemoryThreadProjectionState.REBUILD_REQUIRED,
                    0L,
                    0L,
                    false,
                    null,
                    null,
                    null,
                    forcedDisabledReason);
        }
        return new MemoryThreadRuntimeStatus(
                memoryThreadEnabled,
                derivationEnabled,
                state.projectionState() == MemoryThreadProjectionState.AVAILABLE,
                forcedDisabledReason,
                state.projectionState(),
                state.pendingCount(),
                state.failedCount(),
                state.rebuildInProgress(),
                state.lastProcessedItemId(),
                state.materializationPolicyVersion(),
                state.updatedAt(),
                state.invalidationReason());
    }

    public MemoryThreadRuntimeStatus withSuccess(Instant at, int queueDepth) {
        return new MemoryThreadRuntimeStatus(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                projectionState,
                queueDepth,
                queueDepth,
                at,
                lastFailureAt,
                failureCount,
                failedCount,
                rebuildInProgress,
                lastProcessedItemId,
                materializationPolicyVersion,
                updatedAt,
                invalidationReason);
    }

    public MemoryThreadRuntimeStatus withFailure(Instant at, int queueDepth) {
        return new MemoryThreadRuntimeStatus(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                projectionState,
                queueDepth,
                queueDepth,
                lastSuccessAt,
                at,
                failureCount + 1,
                failedCount + 1,
                rebuildInProgress,
                lastProcessedItemId,
                materializationPolicyVersion,
                updatedAt,
                invalidationReason);
    }

    public boolean memoryThreadEnabled() {
        return memoryThreadEnabled;
    }

    public boolean derivationEnabled() {
        return derivationEnabled;
    }

    public boolean derivationAvailable() {
        return derivationAvailable;
    }

    public String forcedDisabledReason() {
        return forcedDisabledReason;
    }

    public MemoryThreadProjectionState projectionState() {
        return projectionState;
    }

    public int queueDepth() {
        return queueDepth;
    }

    public long pendingCount() {
        return pendingCount;
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant lastFailureAt() {
        return lastFailureAt;
    }

    public long failureCount() {
        return failureCount;
    }

    public long failedCount() {
        return failedCount;
    }

    public boolean rebuildInProgress() {
        return rebuildInProgress;
    }

    public Long lastProcessedItemId() {
        return lastProcessedItemId;
    }

    public String materializationPolicyVersion() {
        return materializationPolicyVersion;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String invalidationReason() {
        return invalidationReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryThreadRuntimeStatus that)) {
            return false;
        }
        return memoryThreadEnabled == that.memoryThreadEnabled
                && derivationEnabled == that.derivationEnabled
                && derivationAvailable == that.derivationAvailable
                && queueDepth == that.queueDepth
                && pendingCount == that.pendingCount
                && failureCount == that.failureCount
                && failedCount == that.failedCount
                && rebuildInProgress == that.rebuildInProgress
                && Objects.equals(forcedDisabledReason, that.forcedDisabledReason)
                && projectionState == that.projectionState
                && Objects.equals(lastSuccessAt, that.lastSuccessAt)
                && Objects.equals(lastFailureAt, that.lastFailureAt)
                && Objects.equals(lastProcessedItemId, that.lastProcessedItemId)
                && Objects.equals(materializationPolicyVersion, that.materializationPolicyVersion)
                && Objects.equals(updatedAt, that.updatedAt)
                && Objects.equals(invalidationReason, that.invalidationReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                projectionState,
                queueDepth,
                pendingCount,
                lastSuccessAt,
                lastFailureAt,
                failureCount,
                failedCount,
                rebuildInProgress,
                lastProcessedItemId,
                materializationPolicyVersion,
                updatedAt,
                invalidationReason);
    }

    @Override
    public String toString() {
        return "MemoryThreadRuntimeStatus{"
                + "memoryThreadEnabled="
                + memoryThreadEnabled
                + ", derivationEnabled="
                + derivationEnabled
                + ", derivationAvailable="
                + derivationAvailable
                + ", forcedDisabledReason='"
                + forcedDisabledReason
                + '\''
                + ", projectionState="
                + projectionState
                + ", queueDepth="
                + queueDepth
                + ", pendingCount="
                + pendingCount
                + ", lastSuccessAt="
                + lastSuccessAt
                + ", lastFailureAt="
                + lastFailureAt
                + ", failureCount="
                + failureCount
                + ", failedCount="
                + failedCount
                + ", rebuildInProgress="
                + rebuildInProgress
                + ", lastProcessedItemId="
                + lastProcessedItemId
                + ", materializationPolicyVersion='"
                + materializationPolicyVersion
                + '\''
                + ", updatedAt="
                + updatedAt
                + ", invalidationReason='"
                + invalidationReason
                + '\''
                + '}';
    }

    private static int safeQueueDepth(long pendingCount) {
        if (pendingCount < 0L) {
            throw new IllegalArgumentException("pendingCount must be non-negative");
        }
        return pendingCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pendingCount;
    }
}
