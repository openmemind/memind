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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical persisted memory-thread membership row.
 */
public record MemoryThreadItem(
        Long id,
        String memoryId,
        Long threadId,
        Long itemId,
        double membershipWeight,
        MemoryThreadRole role,
        int sequenceHint,
        Instant joinedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted) {

    public MemoryThreadItem {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadId, "threadId");
        Objects.requireNonNull(itemId, "itemId");
        role = Objects.requireNonNull(role, "role");
        if (Double.isNaN(membershipWeight) || membershipWeight < 0.0d || membershipWeight > 1.0d) {
            throw new IllegalArgumentException("membershipWeight must be in [0,1]");
        }
        if (sequenceHint <= 0) {
            throw new IllegalArgumentException("sequenceHint must be positive");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public MemoryThreadItem withId(Long id) {
        return new MemoryThreadItem(
                id,
                memoryId,
                threadId,
                itemId,
                membershipWeight,
                role,
                sequenceHint,
                joinedAt,
                metadata,
                createdAt,
                updatedAt,
                deleted);
    }

    public MemoryThreadItem withThreadId(Long threadId) {
        return new MemoryThreadItem(
                id,
                memoryId,
                threadId,
                itemId,
                membershipWeight,
                role,
                sequenceHint,
                joinedAt,
                metadata,
                createdAt,
                updatedAt,
                deleted);
    }
}
