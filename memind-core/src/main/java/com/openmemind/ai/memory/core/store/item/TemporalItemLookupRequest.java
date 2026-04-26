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
package com.openmemind.ai.memory.core.store.item;

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record TemporalItemLookupRequest(
        Instant startInclusive,
        Instant endExclusive,
        TemporalDirection direction,
        MemoryScope scope,
        Set<MemoryCategory> categories,
        Set<MemoryItemType> itemTypes,
        int maxCandidates,
        Set<Long> excludeItemIds) {

    public TemporalItemLookupRequest {
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endExclusive, "endExclusive");
        Objects.requireNonNull(direction, "direction");
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        itemTypes = itemTypes == null ? Set.of() : Set.copyOf(itemTypes);
        excludeItemIds = excludeItemIds == null ? Set.of() : Set.copyOf(excludeItemIds);
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("startInclusive must be before endExclusive");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }
    }
}
