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
package com.openmemind.ai.memory.core.store.graph;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted alias identity used for Stage 3 historical alias retrieval.
 */
public record GraphEntityAlias(
        String memoryId,
        String entityKey,
        GraphEntityType entityType,
        String normalizedAlias,
        int evidenceCount,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {

    public GraphEntityAlias {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(entityKey, "entityKey");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(normalizedAlias, "normalizedAlias");
        if (normalizedAlias.isBlank()) {
            throw new IllegalArgumentException("normalizedAlias must not be blank");
        }
        if (evidenceCount <= 0) {
            throw new IllegalArgumentException("evidenceCount must be positive");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
