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
package com.openmemind.ai.memory.core.store.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadStatus;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryMemoryThreadOperationsTest {

    private static final Instant NOW = Instant.parse("2026-04-17T10:00:00Z");

    @Test
    void noOpMemoryThreadOperationsSwallowWritesAndExposeEmptyViews() {
        var ops = NoOpMemoryThreadOperations.INSTANCE;
        var memoryId = TestMemoryIds.userAgent();

        ops.upsertThreads(memoryId, List.of(thread(101L)));
        ops.upsertThreadItems(memoryId, List.of(membership(201L, 101L, 101L)));
        ops.deleteMembershipsByItemIds(memoryId, List.of(101L));

        assertThat(ops.listThreads(memoryId)).isEmpty();
        assertThat(ops.listThreadItems(memoryId)).isEmpty();
    }

    @Test
    void upsertThreadAndMembershipsKeepsSingleThreadPerItemInvariant() {
        var store = new InMemoryMemoryThreadOperations();
        var memoryId = TestMemoryIds.userAgent();
        var membership = membership(201L, 101L, 101L);

        store.upsertThreads(memoryId, List.of(thread(101L)));
        store.upsertThreadItems(memoryId, List.of(membership));

        assertThat(store.listThreads(memoryId))
                .extracting(MemoryThread::threadKey)
                .containsExactly("ep:101");
        assertThat(store.listThreadItems(memoryId))
                .extracting(MemoryThreadItem::itemId)
                .containsExactly(101L);

        assertThatThrownBy(
                        () ->
                                store.upsertThreadItems(
                                        memoryId,
                                        List.of(membership.withThreadId(102L).withId(202L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single-thread-per-item");
    }

    @Test
    void deleteMembershipsByItemIdsRemovesMembershipAndReleasesItemForReassignment() {
        var store = new InMemoryMemoryThreadOperations();
        var memoryId = TestMemoryIds.userAgent();

        store.upsertThreads(memoryId, List.of(thread(101L), thread(102L)));
        store.upsertThreadItems(memoryId, List.of(membership(201L, 101L, 101L)));

        store.deleteMembershipsByItemIds(memoryId, List.of(101L));
        store.upsertThreadItems(memoryId, List.of(membership(202L, 102L, 101L)));

        assertThat(store.listThreadItems(memoryId))
                .extracting(MemoryThreadItem::threadId, MemoryThreadItem::itemId)
                .containsExactly(tuple(102L, 101L));
    }

    private static MemoryThread thread(Long id) {
        var memoryId = TestMemoryIds.userAgent();
        return new MemoryThread(
                id,
                memoryId.toIdentifier(),
                "ep:" + id,
                "project",
                "Phase 4 Delivery Line",
                "From implementation start toward bounded delivery",
                MemoryThreadStatus.OPEN,
                0.92d,
                NOW,
                null,
                NOW,
                id,
                id,
                1,
                Map.of(),
                NOW,
                NOW,
                false);
    }

    private static MemoryThreadItem membership(Long id, Long threadId, Long itemId) {
        var memoryId = TestMemoryIds.userAgent();
        return new MemoryThreadItem(
                id,
                memoryId.toIdentifier(),
                threadId,
                itemId,
                0.95d,
                MemoryThreadRole.CORE,
                1,
                NOW,
                Map.of(),
                NOW,
                NOW,
                false);
    }
}
