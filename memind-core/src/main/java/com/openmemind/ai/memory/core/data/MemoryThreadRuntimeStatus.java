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

import java.time.Instant;

/**
 * Runtime-visible memory-thread derivation status.
 */
public record MemoryThreadRuntimeStatus(
        boolean memoryThreadEnabled,
        boolean derivationEnabled,
        boolean derivationAvailable,
        String forcedDisabledReason,
        int queueDepth,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        long failureCount) {

    public MemoryThreadRuntimeStatus {
        if (queueDepth < 0) {
            throw new IllegalArgumentException("queueDepth must be non-negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must be non-negative");
        }
    }

    public static MemoryThreadRuntimeStatus disabled(String reason) {
        return new MemoryThreadRuntimeStatus(false, false, false, reason, 0, null, null, 0L);
    }

    public MemoryThreadRuntimeStatus withSuccess(Instant at, int queueDepth) {
        return new MemoryThreadRuntimeStatus(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                queueDepth,
                at,
                lastFailureAt,
                failureCount);
    }

    public MemoryThreadRuntimeStatus withFailure(Instant at, int queueDepth) {
        return new MemoryThreadRuntimeStatus(
                memoryThreadEnabled,
                derivationEnabled,
                derivationAvailable,
                forcedDisabledReason,
                queueDepth,
                lastSuccessAt,
                at,
                failureCount + 1);
    }
}
