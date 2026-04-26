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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Materialized V1 thread projection.
 */
public record MemoryThreadProjection(
        String memoryId,
        String threadKey,
        MemoryThreadType threadType,
        String anchorKind,
        String anchorKey,
        String displayLabel,
        MemoryThreadLifecycleStatus lifecycleStatus,
        MemoryThreadObjectState objectState,
        String headline,
        Map<String, Object> snapshotJson,
        int snapshotVersion,
        Instant openedAt,
        Instant lastEventAt,
        Instant lastMeaningfulUpdateAt,
        Instant closedAt,
        long eventCount,
        long memberCount,
        Instant createdAt,
        Instant updatedAt) {

    public MemoryThreadProjection {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadKey, "threadKey");
        threadType = Objects.requireNonNull(threadType, "threadType");
        Objects.requireNonNull(anchorKind, "anchorKind");
        Objects.requireNonNull(anchorKey, "anchorKey");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        objectState = Objects.requireNonNull(objectState, "objectState");
        snapshotJson = snapshotJson == null ? Map.of() : Map.copyOf(snapshotJson);
        if (snapshotVersion < 0) {
            throw new IllegalArgumentException("snapshotVersion must be non-negative");
        }
        if (eventCount < 0) {
            throw new IllegalArgumentException("eventCount must be non-negative");
        }
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount must be non-negative");
        }
    }
}
