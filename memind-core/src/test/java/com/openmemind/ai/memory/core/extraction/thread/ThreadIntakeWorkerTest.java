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
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.thread.InMemoryThreadProjectionStore;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadIntakeWorkerTest {

    @Test
    void intakeSuccessPublishesProjectionAndOutboxCompletionAtomically() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore backingStore = new InMemoryMemoryStore();
        backingStore
                .itemOperations()
                .insertItems(memoryId, List.of(item(300L, "The user planned more travel.")));
        backingStore
                .graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 300L, "concept:travel")));
        InMemoryThreadProjectionStore projectionStore = spy(new InMemoryThreadProjectionStore());
        projectionStore.ensureRuntime(memoryId, ThreadMaterializationPolicy.v1().version());
        projectionStore.enqueue(memoryId, 300L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        projectionStore,
                        backingStore.itemOperations(),
                        backingStore.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        verify(projectionStore, atLeastOnce())
                .commitClaimedIntakeReplaySuccess(
                        eq(memoryId),
                        anyList(),
                        eq(300L),
                        anyList(),
                        anyList(),
                        anyList(),
                        any(),
                        any());
        verify(projectionStore, never()).finalizeOutboxSuccess(any(), eq(300L), eq(300L), any());
        verify(projectionStore, never())
                .replaceProjection(any(), anyList(), anyList(), anyList(), any(), any());
    }

    @Test
    void workerReplaysOnlyOnceForMultipleClaimedCutoffs() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryThreadProjectionStore store = new InMemoryThreadProjectionStore();
        store.ensureRuntime(memoryId, ThreadMaterializationPolicy.v1().version());
        store.enqueue(memoryId, 301L);
        store.enqueue(memoryId, 302L);

        ThreadProjectionMaterializer materializer = mock(ThreadProjectionMaterializer.class);
        when(materializer.materializeUpTo(memoryId, 302L))
                .thenReturn(
                        new ThreadProjectionMaterializer.MaterializedProjection(
                                List.of(), List.of(), List.of(), 302L));
        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store,
                        materializer,
                        ThreadMaterializationPolicy.v1(),
                        ThreadReplaySuccessListener.NOOP);

        worker.wake(memoryId);

        verify(materializer, times(1)).materializeUpTo(memoryId, 302L);
    }

    @Test
    void metricsRecordOneCoalescedReplayPublication() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryThreadProjectionStore store = new InMemoryThreadProjectionStore();
        ThreadMaterializationPolicy policy = ThreadMaterializationPolicy.v1();
        store.ensureRuntime(memoryId, policy.version());
        store.enqueue(memoryId, 401L);
        store.enqueue(memoryId, 402L);

        ThreadProjectionMaterializer materializer = mock(ThreadProjectionMaterializer.class);
        when(materializer.materializeUpTo(memoryId, 402L))
                .thenReturn(
                        new ThreadProjectionMaterializer.MaterializedProjection(
                                List.of(), List.of(), List.of(), 402L));
        RecordingThreadDerivationMetrics metrics = new RecordingThreadDerivationMetrics();
        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store, materializer, policy, ThreadReplaySuccessListener.NOOP, metrics);

        worker.wake(memoryId);

        assertThat(metrics.claimedBatchSizes()).containsExactly(2);
        assertThat(metrics.coalescedReplayCutoffCounts()).containsExactly(2);
        assertThat(metrics.replayOrigins()).containsExactly(ThreadReplayOrigin.INTAKE_BATCH);
    }

    @Test
    void wakeProcessesProjectionAfterTwoSupportingItemsExist() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(301L, "The user planned a flight."),
                                item(302L, "The user booked a flight.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 301L, "concept:travel"),
                                mention(memoryId, 302L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 301L);
        store.threadOperations().enqueue(memoryId, 302L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
        assertThat(store.threadOperations().listOutbox(memoryId))
                .hasSize(2)
                .allSatisfy(
                        entry ->
                                assertThat(entry.status())
                                        .isEqualTo(MemoryThreadIntakeStatus.COMPLETED));
        assertThat(store.threadOperations().getRuntime(memoryId))
                .get()
                .extracting(
                        runtime -> runtime.projectionState(),
                        runtime -> runtime.lastProcessedItemId())
                .containsExactly(MemoryThreadProjectionState.AVAILABLE, 302L);
    }

    @Test
    void wakeRecoversExpiredProcessingLeaseBeforeRetrying() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(memoryId, List.of(item(302L, "The user planned more travel.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 302L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 302L);
        store.threadOperations()
                .claimPending(
                        memoryId,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:01Z"),
                        1);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listOutbox(memoryId))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.status())
                                    .isEqualTo(MemoryThreadIntakeStatus.COMPLETED);
                            assertThat(entry.attemptCount()).isEqualTo(1);
                        });
    }

    @Test
    void firstTopicHitDoesNotCreateThreadButSecondHitRetroactivelyAdmitsFirstItem() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(memoryId, List.of(item(301L, "The user planned a trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 301L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 301L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId)).isEmpty();

        store.itemOperations()
                .insertItems(memoryId, List.of(item(302L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId, List.of(mention(memoryId, 302L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 302L);

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
        assertThat(store.threadOperations().listMemberships(memoryId, "topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(301L, 302L);
    }

    @Test
    void metadataBackedWorkThreadRequiresBoundMeaningfulMarkerAndTwoHits() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                metadataItem(
                                        311L,
                                        "Project Alpha moved into implementation.",
                                        "project",
                                        "alpha",
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "STATE_CHANGE",
                                                        "objectRef",
                                                        "project:alpha",
                                                        "fromState",
                                                        "planning",
                                                        "toState",
                                                        "implementation",
                                                        "summary",
                                                        "Project Alpha entered implementation"))),
                                metadataItem(
                                        312L,
                                        "Project Alpha shipped another implementation update.",
                                        "project",
                                        "alpha",
                                        List.of())));
        store.threadOperations().enqueue(memoryId, 311L);
        store.threadOperations().enqueue(memoryId, 312L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactly("work:project:project:alpha", 2L);
        assertThat(store.threadOperations().listMemberships(memoryId, "work:project:project:alpha"))
                .extracting(membership -> membership.itemId())
                .containsExactly(311L, 312L);
    }

    @Test
    void metadataBackedWorkRefWithoutBoundMeaningfulMarkerDoesNotCreateThread() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                metadataItem(
                                        313L,
                                        "Project Alpha was mentioned.",
                                        "project",
                                        "alpha",
                                        List.of()),
                                metadataItem(
                                        314L,
                                        "Project Alpha was mentioned again.",
                                        "project",
                                        "alpha",
                                        List.of())));
        store.threadOperations().enqueue(memoryId, 313L);
        store.threadOperations().enqueue(memoryId, 314L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId)).isEmpty();
    }

    @Test
    void explicitContinuityLinkAttachesToExistingThreadWithoutExactAnchor() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(321L, "The user planned a trip."),
                                item(322L, "The user booked the trip."),
                                continuityItem(
                                        323L,
                                        "The user started the visa paperwork.",
                                        "concept:passport",
                                        322L)));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 321L, "concept:travel"),
                                mention(memoryId, 322L, "concept:travel"),
                                mention(memoryId, 323L, "concept:passport")));
        store.threadOperations().enqueue(memoryId, 321L);
        store.threadOperations().enqueue(memoryId, 322L);
        store.threadOperations().enqueue(memoryId, 323L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactly("topic:topic:concept:travel", 3L);
        assertThat(store.threadOperations().listMemberships(memoryId, "topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(321L, 322L, 323L);
    }

    @Test
    void multipleExactTopicMatchesInSameItemAreIgnoredAsAmbiguous() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(331L, "The user planned a trip."),
                                item(332L, "The user booked the trip."),
                                item(333L, "The user planned a Japan itinerary."),
                                item(334L, "The user booked the Japan tickets."),
                                item(
                                        335L,
                                        "The user compared the Japan trip with the broader"
                                                + " itinerary.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 331L, "concept:travel"),
                                mention(memoryId, 332L, "concept:travel"),
                                mention(memoryId, 333L, "concept:japan"),
                                mention(memoryId, 334L, "concept:japan"),
                                mention(memoryId, 335L, "concept:travel"),
                                mention(memoryId, 335L, "concept:japan")));
        store.threadOperations().enqueue(memoryId, 331L);
        store.threadOperations().enqueue(memoryId, 332L);
        store.threadOperations().enqueue(memoryId, 333L);
        store.threadOperations().enqueue(memoryId, 334L);
        store.threadOperations().enqueue(memoryId, 335L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactlyInAnyOrder(
                        tuple("topic:topic:concept:travel", 2L),
                        tuple("topic:topic:concept:japan", 2L));
        assertThat(store.threadOperations().listMemberships(memoryId, "topic:topic:concept:travel"))
                .extracting(membership -> membership.itemId())
                .containsExactly(331L, 332L);
        assertThat(store.threadOperations().listMemberships(memoryId, "topic:topic:concept:japan"))
                .extracting(membership -> membership.itemId())
                .containsExactly(333L, 334L);
    }

    @Test
    void graphContinuitySignalsDoNotIntroduceNewThreadFromSingleWeakMention() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(341L, "The user planned a trip."),
                                item(342L, "The user booked the trip."),
                                item(343L, "The user reviewed the passport checklist.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 341L, "concept:travel"),
                                mention(memoryId, 342L, "concept:travel"),
                                mention(memoryId, 343L, "concept:passport")));
        store.graphOperations()
                .upsertItemLinks(
                        memoryId,
                        List.of(
                                new ItemLink(
                                        memoryId.toIdentifier(),
                                        343L,
                                        342L,
                                        ItemLinkType.SEMANTIC,
                                        1.0d,
                                        Map.of("source", "vector_search"),
                                        Instant.parse("2026-04-20T09:00:00Z"))));
        store.threadOperations().enqueue(memoryId, 341L);
        store.threadOperations().enqueue(memoryId, 342L);
        store.threadOperations().enqueue(memoryId, 343L);

        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        ThreadMaterializationPolicy.v1());

        worker.wake(memoryId);

        assertThat(store.threadOperations().listThreads(memoryId))
                .singleElement()
                .extracting(
                        projection -> projection.threadKey(),
                        projection -> projection.memberCount())
                .containsExactly("topic:topic:concept:travel", 2L);
    }

    @Test
    void listenerIsInvokedOncePerCommittedBatch() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.itemOperations()
                .insertItems(
                        memoryId,
                        List.of(
                                item(351L, "The user planned a trip."),
                                item(352L, "The user booked the trip.")));
        store.graphOperations()
                .upsertItemEntityMentions(
                        memoryId,
                        List.of(
                                mention(memoryId, 351L, "concept:travel"),
                                mention(memoryId, 352L, "concept:travel")));
        store.threadOperations().enqueue(memoryId, 351L);
        store.threadOperations().enqueue(memoryId, 352L);

        List<ThreadReplaySuccessContext> callbacks = new ArrayList<>();
        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store.threadOperations(),
                        store.itemOperations(),
                        store.graphOperations(),
                        store.threadEnrichmentInputStore(),
                        ThreadMaterializationPolicy.v1(),
                        callbacks::add);

        worker.wake(memoryId);

        assertThat(callbacks)
                .singleElement()
                .extracting(
                        ThreadReplaySuccessContext::replayOrigin,
                        ThreadReplaySuccessContext::replayCutoffItemId,
                        ThreadReplaySuccessContext::coveredTriggerItemIds)
                .containsExactly(ThreadReplayOrigin.INTAKE_BATCH, 352L, List.of(351L, 352L));
    }

    @Test
    void rebuildContentionReturnsClaimsToPendingWithoutPublishingIntakeResult() {
        MemoryId memoryId = TestMemoryIds.userAgent();
        ContendedCommitStore store = new ContendedCommitStore();
        store.ensureRuntime(memoryId, ThreadMaterializationPolicy.v1().version());
        store.enqueue(memoryId, 301L);
        store.enqueue(memoryId, 302L);

        ThreadProjectionMaterializer materializer = mock(ThreadProjectionMaterializer.class);
        when(materializer.materializeUpTo(memoryId, 302L))
                .thenReturn(
                        new ThreadProjectionMaterializer.MaterializedProjection(
                                List.of(), List.of(), List.of(), 302L));
        ThreadReplaySuccessListener listener = mock(ThreadReplaySuccessListener.class);
        ThreadIntakeWorker worker =
                new ThreadIntakeWorker(
                        store, materializer, ThreadMaterializationPolicy.v1(), listener);

        worker.wake(memoryId);

        assertThat(store.listOutbox(memoryId))
                .extracting(entry -> entry.status())
                .containsOnly(MemoryThreadIntakeStatus.PENDING);
        verify(listener, never()).afterSuccessfulReplay(any());
    }

    private static final class ContendedCommitStore extends InMemoryThreadProjectionStore {

        @Override
        public boolean commitClaimedIntakeReplaySuccess(
                MemoryId memoryId,
                List<MemoryThreadIntakeClaim> claimedEntries,
                long replayCutoffItemId,
                List<MemoryThreadProjection> threads,
                List<MemoryThreadEvent> events,
                List<MemoryThreadMembership> memberships,
                MemoryThreadRuntimeState runtimeState,
                Instant finalizedAt) {
            return false;
        }
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

    private static MemoryItem continuityItem(
            long itemId, String content, String conceptKey, long targetItemId) {
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
                Map.of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "canonicalRefs",
                                List.of(
                                        Map.of(
                                                "refType",
                                                "topic",
                                                "refKey",
                                                conceptKey.substring("concept:".length()))),
                                "continuityLinks",
                                List.of(
                                        Map.of(
                                                "linkType",
                                                "CONTINUES",
                                                "targetItemId",
                                                targetItemId)))),
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryItemType.FACT);
    }

    private static MemoryItem metadataItem(
            long itemId,
            String content,
            String refType,
            String refKey,
            List<Map<String, Object>> markers) {
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
                Map.of(
                        "threadSemantics",
                        Map.of(
                                "version",
                                1,
                                "markers",
                                markers,
                                "canonicalRefs",
                                List.of(Map.of("refType", refType, "refKey", refKey)))),
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
