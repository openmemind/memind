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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import java.time.Instant;
import java.util.Objects;

/**
 * Thread membership row.
 */
public record MemoryThreadMembership(
        String memoryId,
        String threadKey,
        long itemId,
        MemoryThreadMembershipRole role,
        boolean primary,
        double relevanceWeight,
        Instant createdAt,
        Instant updatedAt) {

    public MemoryThreadMembership {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(threadKey, "threadKey");
        role = Objects.requireNonNull(role, "role");
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId must be positive");
        }
        if (Double.isNaN(relevanceWeight) || relevanceWeight < 0.0d || relevanceWeight > 1.0d) {
            throw new IllegalArgumentException("relevanceWeight must be in [0,1]");
        }
    }
}
