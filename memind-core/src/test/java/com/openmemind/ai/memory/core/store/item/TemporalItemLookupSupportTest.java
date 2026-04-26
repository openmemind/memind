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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalDirection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporalItemLookupSupportTest {

    @Test
    void futureLookupMatchesSemanticOccurrenceOnly() {
        var operations = new InMemoryItemOperations();
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        operations.insertItems(
                memoryId,
                List.of(
                        item(
                                1,
                                "明天去爬山",
                                null,
                                Instant.parse("2026-04-27T00:00:00Z"),
                                Instant.parse("2026-04-28T00:00:00Z"),
                                Instant.parse("2026-04-26T10:00:00Z"),
                                MemoryCategory.EVENT,
                                MemoryItemType.FACT),
                        item(
                                2,
                                "今天记录但不是明天计划",
                                null,
                                null,
                                null,
                                Instant.parse("2026-04-27T10:00:00Z"),
                                MemoryCategory.EVENT,
                                MemoryItemType.FACT)));

        var request =
                new TemporalItemLookupRequest(
                        Instant.parse("2026-04-27T00:00:00Z"),
                        Instant.parse("2026-04-28T00:00:00Z"),
                        TemporalDirection.FUTURE,
                        MemoryScope.USER,
                        Set.of(MemoryCategory.EVENT),
                        Set.of(MemoryItemType.FACT),
                        10,
                        Set.of());

        var matches = operations.listTemporalItemMatches(memoryId, request);

        assertEquals(List.of(1L), matches.stream().map(match -> match.item().id()).toList());
    }

    @Test
    void lookupAppliesFiltersAndCandidateLimit() {
        var operations = new InMemoryItemOperations();
        var memoryId = DefaultMemoryId.of("user-1", "agent-1");
        operations.insertItems(
                memoryId,
                List.of(
                        item(
                                1,
                                "closest",
                                Instant.parse("2026-04-27T12:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT,
                                MemoryItemType.FACT),
                        item(
                                2,
                                "same window but excluded",
                                Instant.parse("2026-04-27T08:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.EVENT,
                                MemoryItemType.FACT),
                        item(
                                3,
                                "wrong category",
                                Instant.parse("2026-04-27T12:00:00Z"),
                                null,
                                null,
                                null,
                                MemoryCategory.PROFILE,
                                MemoryItemType.FACT)));

        var request =
                new TemporalItemLookupRequest(
                        Instant.parse("2026-04-27T00:00:00Z"),
                        Instant.parse("2026-04-28T00:00:00Z"),
                        TemporalDirection.FUTURE,
                        MemoryScope.USER,
                        Set.of(MemoryCategory.EVENT),
                        Set.of(MemoryItemType.FACT),
                        1,
                        Set.of(2L));

        var matches = operations.listTemporalItemMatches(memoryId, request);

        assertEquals(List.of(1L), matches.stream().map(match -> match.item().id()).toList());
    }

    private static MemoryItem item(
            long id,
            String content,
            Instant occurredAt,
            Instant occurredStart,
            Instant occurredEnd,
            Instant observedAt,
            MemoryCategory category,
            MemoryItemType type) {
        return new MemoryItem(
                id,
                "user-1:agent-1",
                content,
                MemoryScope.USER,
                category,
                "conversation",
                String.valueOf(id),
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredStart,
                occurredEnd,
                "DAY",
                observedAt,
                Map.of(),
                Instant.parse("2026-04-20T00:00:00Z"),
                type);
    }
}
