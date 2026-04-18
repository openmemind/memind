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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical persisted memory-thread snapshot.
 */
public record MemoryThread(
        Long id,
        String memoryId,
        String threadKey,
        String episodeType,
        String title,
        String summarySnapshot,
        MemoryThreadStatus status,
        double confidence,
        Instant startAt,
        Instant endAt,
        Instant lastActivityAt,
        Long originItemId,
        Long anchorItemId,
        int displayOrderHint,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted) {

    public MemoryThread {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadKey, "threadKey");
        status = Objects.requireNonNull(status, "status");
        if (Double.isNaN(confidence) || confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
        if (displayOrderHint < 0) {
            throw new IllegalArgumentException("displayOrderHint must be non-negative");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
