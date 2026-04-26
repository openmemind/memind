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
package com.openmemind.ai.memory.core.data.thread;

import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-memory runtime state for thread materialization.
 */
public record MemoryThreadRuntimeState(
        String memoryId,
        MemoryThreadProjectionState projectionState,
        long pendingCount,
        long failedCount,
        Long lastEnqueuedItemId,
        Long lastProcessedItemId,
        boolean rebuildInProgress,
        Long rebuildCutoffItemId,
        long rebuildEpoch,
        String materializationPolicyVersion,
        String invalidationReason,
        Instant updatedAt) {

    public MemoryThreadRuntimeState {
        Objects.requireNonNull(memoryId, "memoryId");
        projectionState = Objects.requireNonNull(projectionState, "projectionState");
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (pendingCount < 0) {
            throw new IllegalArgumentException("pendingCount must be non-negative");
        }
        if (failedCount < 0) {
            throw new IllegalArgumentException("failedCount must be non-negative");
        }
        if (rebuildEpoch < 0) {
            throw new IllegalArgumentException("rebuildEpoch must be non-negative");
        }
    }
}
