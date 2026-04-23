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

import static com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState.AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryThreadProjectionStoreTest {

    @Test
    void replayCommitFinalizesOnlyTheClaimedGeneration() {
        var store = new InMemoryThreadProjectionStore();
        var memoryId = DefaultMemoryId.of("u1", "a1");
        store.ensureRuntime(memoryId, "thread-core-v2");

        store.enqueue(memoryId, 301L);
        MemoryThreadIntakeClaim firstClaim =
                store.claimPending(
                                memoryId,
                                Instant.parse("2026-04-23T01:00:00Z"),
                                Instant.parse("2026-04-23T01:00:30Z"),
                                1)
                        .getFirst();
        store.enqueueReplay(memoryId, 301L);

        store.commitClaimedIntakeReplaySuccess(
                memoryId,
                List.of(firstClaim),
                301L,
                List.of(),
                List.of(),
                List.of(),
                runtime(memoryId, AVAILABLE, 301L, 1, 0, false, 0L),
                Instant.parse("2026-04-23T01:01:00Z"));

        assertThat(store.listOutbox(memoryId))
                .extracting(
                        MemoryThreadIntakeOutboxEntry::triggerItemId,
                        MemoryThreadIntakeOutboxEntry::enqueueGeneration,
                        MemoryThreadIntakeOutboxEntry::status)
                .containsExactly(tuple(301L, 2L, MemoryThreadIntakeStatus.PENDING));
    }

    @Test
    void intakeCommitFailsOpenWhenRebuildEpochAdvancedAfterClaim() {
        var store = new InMemoryThreadProjectionStore();
        var memoryId = DefaultMemoryId.of("u1", "a1");
        store.ensureRuntime(memoryId, "thread-core-v2");
        store.enqueue(memoryId, 401L);

        MemoryThreadIntakeClaim claim =
                store.claimPending(
                                memoryId,
                                Instant.parse("2026-04-23T02:00:00Z"),
                                Instant.parse("2026-04-23T02:00:30Z"),
                                1)
                        .getFirst();
        long observedRebuildEpoch = store.getRuntime(memoryId).orElseThrow().rebuildEpoch();
        assertThat(store.beginRebuild(memoryId, "thread-core-v2", 401L)).isTrue();

        boolean committed =
                store.commitClaimedIntakeReplaySuccess(
                        memoryId,
                        List.of(claim),
                        401L,
                        List.of(),
                        List.of(),
                        List.of(),
                        runtime(memoryId, AVAILABLE, 401L, 0, 0, false, observedRebuildEpoch),
                        Instant.parse("2026-04-23T02:01:00Z"));

        assertThat(committed).isFalse();
        assertThat(store.getRuntime(memoryId))
                .get()
                .extracting(MemoryThreadRuntimeState::rebuildInProgress)
                .isEqualTo(true);
    }

    @Test
    void commitIntakeReplaySuccessKeepsClaimedStateWhenAtomicCommitFailsBeforeSwap() {
        var store = new FailingAtomicReplayStore();
        var memoryId = DefaultMemoryId.of("u1", "a1");

        store.ensureRuntime(memoryId, "v1");
        store.replaceProjection(
                memoryId,
                List.of(projection(memoryId, "topic:topic:concept:travel")),
                List.of(),
                List.of(membership(memoryId, "topic:topic:concept:travel", 301L)),
                runtime(memoryId, AVAILABLE, 301L, 0, 0, false, 0L),
                Instant.parse("2026-04-20T00:00:00Z"));
        store.enqueue(memoryId, 302L);
        var claim =
                store.claimPending(
                                memoryId,
                                Instant.parse("2026-04-20T00:59:00Z"),
                                Instant.parse("2026-04-20T00:59:30Z"),
                                1)
                        .getFirst();

        assertThatThrownBy(
                        () ->
                                store.commitClaimedIntakeReplaySuccess(
                                        memoryId,
                                        List.of(claim),
                                        302L,
                                        List.of(projection(memoryId, "topic:topic:concept:japan")),
                                        List.of(),
                                        List.of(
                                                membership(
                                                        memoryId,
                                                        "topic:topic:concept:japan",
                                                        302L)),
                                        runtime(memoryId, AVAILABLE, 302L, 0, 0, false, 0L),
                                        Instant.parse("2026-04-20T01:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(store.listThreads(memoryId))
                .extracting(MemoryThreadProjection::threadKey)
                .containsExactly("topic:topic:concept:travel");
        assertThat(store.listOutbox(memoryId))
                .filteredOn(entry -> entry.triggerItemId() == 302L)
                .singleElement()
                .extracting(MemoryThreadIntakeOutboxEntry::status)
                .isEqualTo(MemoryThreadIntakeStatus.PROCESSING);
        assertThat(store.getRuntime(memoryId))
                .get()
                .extracting(MemoryThreadRuntimeState::lastProcessedItemId)
                .isEqualTo(301L);
    }

    @Test
    void supportsManyToManyMembershipAndRuntimeState() {
        var store = new InMemoryThreadProjectionStore();
        var memoryId = DefaultMemoryId.of("u1", "a1");

        store.replaceProjection(
                memoryId,
                List.of(
                        projection(memoryId, "relationship:relationship:person:alice|person:bob"),
                        projection(memoryId, "topic:topic:concept:travel")),
                List.of(),
                List.of(
                        membership(
                                memoryId,
                                "relationship:relationship:person:alice|person:bob",
                                301L),
                        membership(memoryId, "topic:topic:concept:travel", 301L)),
                runtime(memoryId, AVAILABLE, 301L, 0, 0, false, 0L),
                Instant.parse("2026-04-20T00:00:00Z"));

        assertThat(store.listThreadsByItemId(memoryId, 301L))
                .extracting(MemoryThreadProjection::threadKey)
                .containsExactlyInAnyOrder(
                        "relationship:relationship:person:alice|person:bob",
                        "topic:topic:concept:travel");
        assertThat(store.getRuntime(memoryId))
                .get()
                .extracting(MemoryThreadRuntimeState::projectionState)
                .isEqualTo(AVAILABLE);
    }

    @Test
    void enqueueIsIdempotentPerMemoryAndTriggerItem() {
        var store = new InMemoryThreadProjectionStore();
        var memoryId = DefaultMemoryId.of("u1", "a1");

        store.enqueue(memoryId, 301L);
        store.enqueue(memoryId, 301L);

        assertThat(store.listOutbox(memoryId))
                .extracting(MemoryThreadIntakeOutboxEntry::triggerItemId)
                .containsExactly(301L);
    }

    private static MemoryThreadProjection projection(MemoryId memoryId, String threadKey) {
        return new MemoryThreadProjection(
                memoryId.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                threadKey.substring(threadKey.lastIndexOf(':') + 1),
                "label-" + threadKey,
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "headline",
                Map.of("source", "test"),
                1,
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"),
                null,
                1,
                1,
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"));
    }

    private static MemoryThreadMembership membership(
            MemoryId memoryId, String threadKey, long itemId) {
        return new MemoryThreadMembership(
                memoryId.toIdentifier(),
                threadKey,
                itemId,
                MemoryThreadMembershipRole.CORE,
                true,
                1.0d,
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"));
    }

    private static MemoryThreadRuntimeState runtime(
            MemoryId memoryId,
            com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState projectionState,
            long lastProcessedItemId,
            long pendingCount,
            long failedCount,
            boolean rebuildInProgress,
            long rebuildEpoch) {
        return new MemoryThreadRuntimeState(
                memoryId.toIdentifier(),
                projectionState,
                pendingCount,
                failedCount,
                lastProcessedItemId,
                lastProcessedItemId,
                rebuildInProgress,
                null,
                rebuildEpoch,
                "v1",
                null,
                Instant.parse("2026-04-20T00:00:00Z"));
    }

    private static final class FailingAtomicReplayStore extends InMemoryThreadProjectionStore {

        @Override
        protected void beforeAtomicReplayCommit() {
            throw new IllegalStateException("boom");
        }
    }
}
