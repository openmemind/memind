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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporalCandidateLookupSupportTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void defaultLookupShouldReturnOverlapBeforeAndAfterMatchesWithScopeFilters() {
        ItemOperations ops = new InMemoryItemOperations();
        ops.insertItems(
                MEMORY_ID,
                List.of(
                        temporalItem(
                                1L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T08:00:00Z"),
                        rangedItem(
                                2L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:00:00Z",
                                "2026-04-10T11:30:00Z"),
                        temporalItem(
                                3L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T12:00:00Z"),
                        temporalItem(
                                4L,
                                MemoryItemType.FACT,
                                MemoryCategory.PROFILE,
                                "2026-04-10T10:05:00Z")));

        var request =
                new TemporalCandidateRequest(
                        101L,
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        MemoryItemType.FACT,
                        MemoryCategory.EVENT,
                        4,
                        8,
                        8);

        assertThat(ops.listTemporalCandidateMatches(MEMORY_ID, List.of(request), Set.of(101L)))
                .extracting(match -> match.candidateItem().id())
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    void defaultLookupShouldUseOverlapAwareIntervalsNotAnchorOnly() {
        ItemOperations ops = new InMemoryItemOperations();
        ops.insertItems(
                MEMORY_ID,
                List.of(
                        rangedItem(
                                9L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T13:00:00Z",
                                "2026-04-10T15:30:00Z")));

        var request =
                new TemporalCandidateRequest(
                        201L,
                        Instant.parse("2026-04-10T15:00:00Z"),
                        Instant.parse("2026-04-10T15:00:00Z"),
                        Instant.parse("2026-04-10T15:00:00Z"),
                        MemoryItemType.FACT,
                        MemoryCategory.EVENT,
                        4,
                        8,
                        8);

        assertThat(ops.listTemporalCandidateMatches(MEMORY_ID, List.of(request), Set.of(201L)))
                .extracting(match -> match.candidateItem().id())
                .containsExactly(9L);
    }

    @Test
    void defaultLookupShouldMatchNullCategoryOnlyToNullCategory() {
        ItemOperations ops = new InMemoryItemOperations();
        ops.insertItems(
                MEMORY_ID,
                List.of(
                        temporalItem(10L, MemoryItemType.FACT, null, "2026-04-10T09:59:00Z"),
                        temporalItem(
                                11L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T10:01:00Z")));

        var request =
                new TemporalCandidateRequest(
                        302L,
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        MemoryItemType.FACT,
                        null,
                        1,
                        2,
                        2);

        assertThat(ops.listTemporalCandidateMatches(MEMORY_ID, List.of(request), Set.of(302L)))
                .extracting(match -> match.candidateItem().id())
                .containsExactly(10L);
    }

    @Test
    void defaultLookupShouldBeDeterministicAndDeduplicateWhenStoreOrderIsUnstable() {
        ItemOperations ops =
                new InMemoryItemOperations() {
                    @Override
                    public List<MemoryItem> listItems(MemoryId id) {
                        var unstable = new ArrayList<>(super.listItems(id));
                        Collections.rotate(unstable, 2);
                        unstable.add(unstable.getFirst());
                        return unstable;
                    }
                };
        ops.insertItems(
                MEMORY_ID,
                List.of(
                        temporalItem(
                                5L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:59:00Z"),
                        temporalItem(
                                8L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T09:59:00Z"),
                        temporalItem(
                                7L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T10:01:00Z"),
                        temporalItem(
                                6L,
                                MemoryItemType.FACT,
                                MemoryCategory.EVENT,
                                "2026-04-10T10:01:00Z")));

        var request =
                new TemporalCandidateRequest(
                        301L,
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        Instant.parse("2026-04-10T10:00:00Z"),
                        MemoryItemType.FACT,
                        MemoryCategory.EVENT,
                        1,
                        2,
                        2);

        assertThat(ops.listTemporalCandidateMatches(MEMORY_ID, List.of(request), Set.of(301L)))
                .extracting(match -> match.candidateItem().id())
                .containsExactly(5L, 8L, 6L, 7L);
    }

    private static MemoryItem temporalItem(
            Long id, MemoryItemType type, MemoryCategory category, String occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                category,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                Instant.parse(occurredAt),
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                type);
    }

    private static MemoryItem rangedItem(
            Long id, MemoryItemType type, MemoryCategory category, String start, String end) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                category,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                null,
                Instant.parse(start),
                Instant.parse(end),
                "unknown",
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                type);
    }
}
