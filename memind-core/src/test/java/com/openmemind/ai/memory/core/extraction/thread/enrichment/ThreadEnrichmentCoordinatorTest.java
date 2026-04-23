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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.openmemind.ai.memory.core.builder.MemoryThreadEnrichmentOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.extraction.thread.ThreadMaterializationPolicy;
import com.openmemind.ai.memory.core.extraction.thread.ThreadWakeScheduler;
import com.openmemind.ai.memory.core.store.thread.InMemoryThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.InMemoryThreadProjectionStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ThreadEnrichmentCoordinatorTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final String POLICY_VERSION = ThreadMaterializationPolicy.v1().version();
    private static final Instant BASE_TIME = Instant.parse("2026-04-22T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);

    @Test
    void insertedRunCommitsAuthoritativeInputsAndReplayRequestAtomically() {
        InMemoryThreadProjectionStore projectionStore = new InMemoryThreadProjectionStore();
        projectionStore.ensureRuntime(MEMORY_ID, POLICY_VERSION);
        projectionStore.enqueue(MEMORY_ID, 401L);
        projectionStore.finalizeOutboxSuccess(MEMORY_ID, 401L, 401L, BASE_TIME.minusSeconds(1));

        InMemoryThreadEnrichmentInputStore inputStore =
                new InMemoryThreadEnrichmentInputStore(projectionStore);
        RecordingReplayScheduler replayScheduler = new RecordingReplayScheduler();
        ThreadEnrichmentCoordinator coordinator =
                new ThreadEnrichmentCoordinator(
                        inputStore,
                        replayScheduler,
                        assistantReturning(
                                new ThreadEnrichmentResult(
                                        "UPDATE",
                                        false,
                                        "topic:travel:update:401",
                                        Map.of(
                                                "summary", "Travel planning remains active.",
                                                "summaryRole", "HEADLINE_REFRESH"))),
                        new MemoryThreadEnrichmentOptions(
                                true, 2, 3, Duration.ofMinutes(15), Duration.ofSeconds(5)),
                        Schedulers.immediate(),
                        FIXED_CLOCK);

        coordinator.afterSuccessfulReplay(
                new com.openmemind.ai.memory.core.extraction.thread.ThreadReplaySuccessContext(
                        MEMORY_ID,
                        com.openmemind.ai.memory.core.extraction.thread.ThreadReplayOrigin.INTAKE_BATCH,
                        401L,
                        List.of(401L),
                        List.of(thread("topic:travel")),
                        List.of(
                                itemBackedEvent("topic:travel", "topic:travel:update:301", 1L, true),
                                itemBackedEvent("topic:travel", "topic:travel:update:401", 2L, true)),
                        List.of(),
                        POLICY_VERSION,
                        BASE_TIME));

        assertThat(inputStore.listReplayable(MEMORY_ID, 401L, POLICY_VERSION))
                .singleElement()
                .satisfies(
                        input -> {
                            assertThat(input.threadKey()).isEqualTo("topic:travel");
                            assertThat(input.basisCutoffItemId()).isEqualTo(401L);
                            assertThat(input.basisMeaningfulEventCount()).isEqualTo(2L);
                        });
        assertThat(projectionStore.listOutbox(MEMORY_ID))
                .singleElement()
                .extracting(entry -> entry.triggerItemId(), entry -> entry.status())
                .containsExactly(401L, com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus.PENDING);
        assertThat(replayScheduler.scheduledCutoffs()).containsExactly(401L);
    }

    @Test
    void duplicateEquivalentRunDoesNotEnqueueSecondReplay() {
        InMemoryThreadProjectionStore projectionStore = new InMemoryThreadProjectionStore();
        projectionStore.ensureRuntime(MEMORY_ID, POLICY_VERSION);
        projectionStore.enqueue(MEMORY_ID, 401L);
        projectionStore.finalizeOutboxSuccess(MEMORY_ID, 401L, 401L, BASE_TIME);

        InMemoryThreadEnrichmentInputStore inputStore =
                new InMemoryThreadEnrichmentInputStore(projectionStore);
        List<MemoryThreadEnrichmentInput> runInputs = List.of(enrichmentInput("topic:travel", 0));

        assertThat(inputStore.appendRunAndEnqueueReplay(MEMORY_ID, 401L, runInputs))
                .isEqualTo(ThreadEnrichmentAppendResult.INSERTED);
        projectionStore.finalizeOutboxSuccess(
                MEMORY_ID, 401L, 401L, BASE_TIME.plusSeconds(1));

        assertThat(inputStore.appendRunAndEnqueueReplay(MEMORY_ID, 401L, runInputs))
                .isEqualTo(ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT);
        assertThat(inputStore.listReplayable(MEMORY_ID, 401L, POLICY_VERSION)).hasSize(1);
        assertThat(projectionStore.listOutbox(MEMORY_ID))
                .singleElement()
                .extracting(entry -> entry.status())
                .isEqualTo(com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus.COMPLETED);
    }

    @Test
    void zeroTimeoutDisablesTimeoutFailOpen() {
        InMemoryThreadProjectionStore projectionStore = new InMemoryThreadProjectionStore();
        projectionStore.ensureRuntime(MEMORY_ID, POLICY_VERSION);
        projectionStore.enqueue(MEMORY_ID, 401L);
        projectionStore.finalizeOutboxSuccess(MEMORY_ID, 401L, 401L, BASE_TIME.minusSeconds(1));

        InMemoryThreadEnrichmentInputStore inputStore =
                new InMemoryThreadEnrichmentInputStore(projectionStore);
        RecordingReplayScheduler replayScheduler = new RecordingReplayScheduler();
        ThreadEnrichmentCoordinator coordinator =
                new ThreadEnrichmentCoordinator(
                        inputStore,
                        replayScheduler,
                        delayedAssistantReturning(
                                new ThreadEnrichmentResult(
                                        "UPDATE",
                                        false,
                                        "topic:travel:update:401",
                                        Map.of(
                                                "summary", "Travel planning remains active.",
                                                "summaryRole", "HEADLINE_REFRESH")),
                                Duration.ofMillis(25)),
                        new MemoryThreadEnrichmentOptions(
                                true, 2, 3, Duration.ofMinutes(15), Duration.ZERO),
                        Schedulers.immediate(),
                        FIXED_CLOCK);

        coordinator.afterSuccessfulReplay(
                new com.openmemind.ai.memory.core.extraction.thread.ThreadReplaySuccessContext(
                        MEMORY_ID,
                        com.openmemind.ai.memory.core.extraction.thread.ThreadReplayOrigin.INTAKE_BATCH,
                        401L,
                        List.of(401L),
                        List.of(thread("topic:travel")),
                        List.of(
                                itemBackedEvent("topic:travel", "topic:travel:update:301", 1L, true),
                                itemBackedEvent("topic:travel", "topic:travel:update:401", 2L, true)),
                        List.of(),
                        POLICY_VERSION,
                        BASE_TIME));

        assertThat(inputStore.listReplayable(MEMORY_ID, 401L, POLICY_VERSION)).hasSize(1);
        assertThat(replayScheduler.scheduledCutoffs()).containsExactly(401L);
    }

    @Test
    void conflictingDuplicateRunIsRejected() {
        InMemoryThreadProjectionStore projectionStore = new InMemoryThreadProjectionStore();
        projectionStore.ensureRuntime(MEMORY_ID, POLICY_VERSION);
        projectionStore.enqueue(MEMORY_ID, 401L);
        projectionStore.finalizeOutboxSuccess(MEMORY_ID, 401L, 401L, BASE_TIME);

        InMemoryThreadEnrichmentInputStore inputStore =
                new InMemoryThreadEnrichmentInputStore(projectionStore);
        assertThat(inputStore.appendRunAndEnqueueReplay(MEMORY_ID, 401L, List.of(enrichmentInput("topic:travel", 0))))
                .isEqualTo(ThreadEnrichmentAppendResult.INSERTED);

        MemoryThreadEnrichmentInput conflicting =
                new MemoryThreadEnrichmentInput(
                        MEMORY_ID.toIdentifier(),
                        "topic:travel",
                        "topic:travel|401|2|" + POLICY_VERSION,
                        0,
                        401L,
                        2L,
                        POLICY_VERSION,
                        Map.of(
                                "eventType",
                                "UPDATE",
                                "meaningful",
                                false,
                                "basisEventKey",
                                "topic:travel:update:401",
                                "summary",
                                "Conflicting rewrite"),
                        Map.of("sourceType", "THREAD_LLM"),
                        BASE_TIME.plusSeconds(5));

        assertThatThrownBy(
                        () ->
                                inputStore.appendRunAndEnqueueReplay(
                                        MEMORY_ID, 401L, List.of(conflicting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting duplicate");
    }

    @Test
    void replayWakeupDelegatesToTheSharedThreadWakeScheduler() {
        InMemoryThreadProjectionStore projectionStore = new InMemoryThreadProjectionStore();
        projectionStore.ensureRuntime(MEMORY_ID, POLICY_VERSION);
        projectionStore.enqueueReplay(MEMORY_ID, 401L);

        ThreadWakeScheduler wakeScheduler = mock(ThreadWakeScheduler.class);
        StoreBackedThreadReplayScheduler scheduler =
                new StoreBackedThreadReplayScheduler(projectionStore, wakeScheduler);

        scheduler.scheduleReplay(MEMORY_ID, 401L);

        verify(wakeScheduler).schedule(MEMORY_ID);
    }

    private static ThreadEnrichmentAssistant assistantReturning(ThreadEnrichmentResult result) {
        return (thread, itemBackedEvents) -> Mono.just(List.of(result));
    }

    private static ThreadEnrichmentAssistant delayedAssistantReturning(
            ThreadEnrichmentResult result, Duration delay) {
        return (thread, itemBackedEvents) -> Mono.delay(delay).thenReturn(List.of(result));
    }

    private static MemoryThreadProjection thread(String threadKey) {
        return new MemoryThreadProjection(
                MEMORY_ID.toIdentifier(),
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                threadKey,
                "Travel",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "Travel planning is active",
                Map.of(),
                1,
                BASE_TIME.minusSeconds(300),
                BASE_TIME.minusSeconds(60),
                BASE_TIME.minusSeconds(60),
                null,
                2,
                2,
                BASE_TIME.minusSeconds(300),
                BASE_TIME.minusSeconds(60));
    }

    private static MemoryThreadEvent itemBackedEvent(
            String threadKey, String eventKey, long eventSeq, boolean meaningful) {
        return new MemoryThreadEvent(
                MEMORY_ID.toIdentifier(),
                threadKey,
                eventKey,
                eventSeq,
                MemoryThreadEventType.UPDATE,
                BASE_TIME.minusSeconds(120 - eventSeq),
                Map.of(
                        "summary",
                        "Travel update " + eventSeq,
                        "sources",
                        List.of(Map.of("sourceType", "ITEM", "itemId", 300L + eventSeq))),
                1,
                meaningful,
                0.95d,
                BASE_TIME.minusSeconds(120 - eventSeq));
    }

    private static MemoryThreadEnrichmentInput enrichmentInput(String threadKey, int entrySeq) {
        return new MemoryThreadEnrichmentInput(
                MEMORY_ID.toIdentifier(),
                threadKey,
                threadKey + "|401|2|" + POLICY_VERSION,
                entrySeq,
                401L,
                2L,
                POLICY_VERSION,
                Map.of(
                        "eventType",
                        "UPDATE",
                        "meaningful",
                        false,
                        "basisEventKey",
                        "topic:travel:update:401",
                        "summary",
                        "Travel planning remains active.",
                        "summaryRole",
                        "HEADLINE_REFRESH"),
                Map.of("sourceType", "THREAD_LLM"),
                BASE_TIME);
    }

    private static final class RecordingReplayScheduler implements ThreadReplayScheduler {

        private final Queue<Long> scheduledCutoffs = new ArrayDeque<>();

        @Override
        public void scheduleReplay(MemoryId memoryId, long replayCutoffItemId) {
            scheduledCutoffs.add(replayCutoffItemId);
        }

        private List<Long> scheduledCutoffs() {
            return List.copyOf(scheduledCutoffs);
        }
    }

}
