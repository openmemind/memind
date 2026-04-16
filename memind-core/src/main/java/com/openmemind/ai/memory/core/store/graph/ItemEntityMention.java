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
 * Item-to-entity mention relation.
 */
public record ItemEntityMention(
        String memoryId,
        Long itemId,
        String entityKey,
        Float confidence,
        Map<String, Object> metadata,
        Instant createdAt) {

    public ItemEntityMention {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(entityKey, "entityKey");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
