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
package com.openmemind.ai.memory.core.extraction.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadProjectionRebuilderTest {

    @Test
    void rebuildLeavesPostWatermarkItemsPending() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(301L, "The user planned a trip."),
                                item(302L, "The user booked the trip."),
                                item(303L, "The user extended the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 301L, "concept:travel"),
                                mention(memoryId, 302L, "concept:travel"),
                                mention(memoryId, 303L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 301L);
        store.threadOperations().enqueue(memoryId, 302L);
        store.threadOperations().enqueue(memoryId, 303L);

        ThreadProjectionRebuilder rebuilder =
                new ThreadProjectionRebuilder(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        rebuilder.rebuild(memoryId, 302L);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(thread -> thread.threadKey(), thread -> thread.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
        assertThat(store.threadOperations().listMemberships(memoryId, "topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(301L, 302L);
        assertThat(store.threadOperations().listOutbox(memoryId))
                .filteredOn(entry -> entry.triggerItemId() == 303L)
                .singleElement()
                .extracting(entry -> entry.status())
                .isEqualTo(MemoryThreadIntakeStatus.PENDING);
        assertThat(store.threadOperations().getRuntime(memoryId))
                .get()
                .extracting(
                        runtime -> runtime.projectionState(),
                        runtime -> runtime.lastProcessedItemId())
                .containsExactly(MemoryThreadProjectionState.AVAILABLE, 302L);
    }

    private static MemoryItem item(long itemId, String content) {
        return new MemoryItem(
                itemId,
                TestMemoryIds.userAgent().toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static ItemEntityMention mention(MemoryId memoryId, long itemId, String entityKey) {
        return new ItemEntityMention(
                memoryId.toIdentifier(),
                itemId,
                entityKey,
                1.0f,
                Map.of("source", "test"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }
}
