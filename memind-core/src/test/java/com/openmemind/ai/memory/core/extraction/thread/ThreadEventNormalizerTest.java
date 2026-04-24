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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadEventNormalizerTest {

    @Test
    void observationWithoutMarkersNormalizesToObservationEvent() {
        List<MemoryThreadEvent> events =
                new ThreadEventNormalizer()
                        .normalize(
                                decision("topic:topic:concept:travel"),
                                signalWithoutMarkers(301L, "The user booked a flight."));

        assertThat(events)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.eventType())
                                    .isEqualTo(MemoryThreadEventType.OBSERVATION);
                            assertThat(event.eventSeq()).isZero();
                            assertThat(event.eventPayloadJson())
                                    .containsEntry("summary", "The user booked a flight.");
                        });
    }

    @Test
    void blockerMarkerProducesMeaningfulDeterministicEvent() {
        ThreadIntakeSignal signal =
                new ThreadIntakeSignal(
                        "memory-user-agent",
                        302L,
                        "The project is blocked by legacy auth compatibility.",
                        Instant.parse("2026-04-20T10:00:00Z"),
                        MemoryThreadType.WORK,
                        List.of(
                                new ThreadIntakeSignal.AnchorCandidate(
                                        "project",
                                        "project:alpha-auth-refactor",
                                        List.of(),
                                        0.95d)),
                        new ThreadIntakeSignal.ThreadEligibilityScore(0.90d, 0.60d, 0.90d),
                        List.of(302L),
                        List.of(
                                ThreadIntakeSignal.SemanticMarker.of(
                                        MemoryThreadEventType.BLOCKER_ADDED,
                                        "Legacy auth compatibility is blocking rollout.",
                                        Map.of("blockerKey", "legacy-auth-compat"))),
                        List.of(),
                        0.93d);

        List<MemoryThreadEvent> events =
                new ThreadEventNormalizer()
                        .normalize(
                                new ThreadDecision(
                                        ThreadDecision.Action.CREATE,
                                        "work:project:project:alpha-auth-refactor",
                                        MemoryThreadType.WORK,
                                        "project",
                                        "project:alpha-auth-refactor",
                                        302L,
                                        null),
                                signal);

        assertThat(events)
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.eventType())
                                    .isEqualTo(MemoryThreadEventType.BLOCKER_ADDED);
                            assertThat(event.meaningful()).isTrue();
                            assertThat(event.eventKey())
                                    .isEqualTo(
                                            "work:project:project:alpha-auth-refactor:blocker_added:302:legacy-auth-compat");
                            assertThat(event.eventPayloadJson())
                                    .containsEntry("blockerKey", "legacy-auth-compat");
                        });
    }

    @Test
    void structuralReducerDerivesBlockedThenResolvedSnapshot() {
        ThreadStructuralReducer reducer =
                new ThreadStructuralReducer(ThreadMaterializationPolicy.v1());
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:00Z"));

        MemoryThreadEvent blockerAdded =
                event(
                        current.threadKey(),
                        "evt-1",
                        1,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        MemoryThreadEventType.BLOCKER_ADDED,
                        Map.of(
                                "summary", "Legacy auth is blocking rollout.",
                                "blockerKey", "legacy-auth"));
        MemoryThreadEvent resolved =
                event(
                        current.threadKey(),
                        "evt-2",
                        2,
                        Instant.parse("2026-04-20T12:00:00Z"),
                        MemoryThreadEventType.RESOLUTION_DECLARED,
                        Map.of(
                                "summary", "The migration shipped successfully.",
                                "resolutionKey", "migration-shipped"));

        MemoryThreadProjection projection =
                reducer.reduce(current, List.of(blockerAdded, resolved), List.of(membership));

        assertThat(projection.objectState()).isEqualTo(MemoryThreadObjectState.RESOLVED);
        assertThat(projection.lifecycleStatus()).isEqualTo(MemoryThreadLifecycleStatus.CLOSED);
        assertThat(projection.closedAt()).isEqualTo(Instant.parse("2026-04-20T12:00:00Z"));
        assertThat(projection.snapshotJson())
                .containsEntry("currentState", "RESOLVED")
                .containsEntry("latestUpdate", "The migration shipped successfully.");
    }

    @Test
    void reducerPreservesReplayOrderInsteadOfResortingByEventTime() {
        ThreadStructuralReducer reducer =
                new ThreadStructuralReducer(ThreadMaterializationPolicy.v1());
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:00Z"));

        MemoryThreadEvent resolved =
                event(
                        current.threadKey(),
                        "evt-1",
                        1,
                        Instant.parse("2026-04-20T12:00:00Z"),
                        MemoryThreadEventType.RESOLUTION_DECLARED,
                        Map.of(
                                "summary", "The migration shipped successfully.",
                                "resolutionKey", "migration-shipped"));
        MemoryThreadEvent blockerAdded =
                event(
                        current.threadKey(),
                        "evt-2",
                        2,
                        Instant.parse("2026-04-20T11:00:00Z"),
                        MemoryThreadEventType.BLOCKER_ADDED,
                        Map.of(
                                "summary",
                                "Legacy auth is blocking rollout again after the" + " resolution.",
                                "blockerKey",
                                "legacy-auth"));

        MemoryThreadProjection projection =
                reducer.reduce(
                        current,
                        List.of(resolved, blockerAdded),
                        List.of(membership),
                        Instant.parse("2026-04-20T13:00:00Z"));

        assertThat(projection.objectState()).isEqualTo(MemoryThreadObjectState.BLOCKED);
        assertThat(projection.lifecycleStatus()).isEqualTo(MemoryThreadLifecycleStatus.ACTIVE);
        assertThat(projection.closedAt()).isNull();
        assertThat(projection.snapshotJson())
                .containsEntry("currentState", "BLOCKED")
                .containsEntry(
                        "latestUpdate",
                        "Legacy auth is blocking rollout again after the resolution.");
    }

    @Test
    void recentNonMeaningfulObservationKeepsLifecycleActiveWhileObjectStateStable() {
        ThreadMaterializationPolicy policy =
                new ThreadMaterializationPolicy(
                        "test-policy", 0.78d, 0.70d, 4, Duration.ofDays(7), Duration.ofDays(21));
        ThreadStructuralReducer reducer = new ThreadStructuralReducer(policy);
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:00Z"));
        MemoryThreadEvent observation =
                new MemoryThreadEvent(
                        "memory-user-agent",
                        current.threadKey(),
                        "evt-3",
                        3,
                        MemoryThreadEventType.OBSERVATION,
                        Instant.parse("2026-04-20T10:00:00Z"),
                        Map.of("summary", "Still monitoring rollout health."),
                        1,
                        false,
                        0.90d,
                        Instant.parse("2026-04-20T10:00:00Z"));

        MemoryThreadProjection projection =
                reducer.reduce(
                        current,
                        List.of(observation),
                        List.of(membership),
                        Instant.parse("2026-04-21T00:00:00Z"));

        assertThat(projection.objectState()).isEqualTo(MemoryThreadObjectState.STABLE);
        assertThat(projection.lifecycleStatus()).isEqualTo(MemoryThreadLifecycleStatus.ACTIVE);
        assertThat(projection.closedAt()).isNull();
        assertThat(projection.snapshotJson())
                .containsEntry("currentState", "STABLE")
                .containsEntry("latestUpdate", "Alpha Auth Refactor")
                .containsEntry("salientFacts", List.of("Still monitoring rollout health."));
    }

    @Test
    void snapshotContainsEvidenceFacetWithSupportCountAndDominantFamilies() {
        ThreadStructuralReducer reducer =
                new ThreadStructuralReducer(ThreadMaterializationPolicy.v1());
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:00Z"));
        MemoryThreadEvent observation =
                new MemoryThreadEvent(
                        "memory-user-agent",
                        current.threadKey(),
                        "evt-4",
                        4,
                        MemoryThreadEventType.OBSERVATION,
                        Instant.parse("2026-04-20T10:00:00Z"),
                        Map.of(
                                "summary",
                                "The user continued the rollout with linked supporting evidence.",
                                "evidence",
                                Map.of(
                                        "supportCount",
                                        2,
                                        "dominantFamilies",
                                        List.of("explicit_continuity", "entity_support"))),
                        1,
                        false,
                        0.90d,
                        Instant.parse("2026-04-20T10:00:00Z"));

        MemoryThreadProjection projection =
                reducer.reduce(current, List.of(observation), List.of(membership));

        @SuppressWarnings("unchecked")
        Map<String, Object> facets = (Map<String, Object>) projection.snapshotJson().get("facets");
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) facets.get("evidence");
        assertThat(evidence)
                .containsEntry("supportCount", 2)
                .containsEntry(
                        "dominantFamilies", List.of("explicit_continuity", "entity_support"));
    }

    @Test
    void contradictionAfterResolutionReopensObjectState() {
        ThreadStructuralReducer reducer =
                new ThreadStructuralReducer(ThreadMaterializationPolicy.v1());
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-20T09:00:00Z"),
                        Instant.parse("2026-04-20T09:00:00Z"));

        MemoryThreadEvent resolved =
                event(
                        current.threadKey(),
                        "evt-1",
                        1,
                        Instant.parse("2026-04-20T10:00:00Z"),
                        MemoryThreadEventType.RESOLUTION_DECLARED,
                        Map.of(
                                "summary", "The migration shipped successfully.",
                                "resolutionKey", "migration-shipped"));
        MemoryThreadEvent setback =
                event(
                        current.threadKey(),
                        "evt-2",
                        2,
                        Instant.parse("2026-04-20T11:00:00Z"),
                        MemoryThreadEventType.SETBACK,
                        Map.of("summary", "The rollout regressed after release."));

        MemoryThreadProjection projection =
                reducer.reduce(
                        current,
                        List.of(resolved, setback),
                        List.of(membership),
                        Instant.parse("2026-04-20T12:00:00Z"));

        assertThat(projection.objectState()).isEqualTo(MemoryThreadObjectState.ONGOING);
        assertThat(projection.lifecycleStatus()).isEqualTo(MemoryThreadLifecycleStatus.ACTIVE);
        assertThat(projection.closedAt()).isNull();
        assertThat(projection.snapshotJson())
                .containsEntry("currentState", "ONGOING")
                .containsEntry("latestUpdate", "The rollout regressed after release.");
    }

    @Test
    void reducerMarksUnresolvedThreadDormantAndClosedByInactivityThresholds() {
        ThreadMaterializationPolicy policy =
                new ThreadMaterializationPolicy(
                        "test-policy", 0.78d, 0.70d, 4, Duration.ofDays(7), Duration.ofDays(21));
        ThreadStructuralReducer reducer = new ThreadStructuralReducer(policy);
        MemoryThreadProjection current = currentProjection();
        MemoryThreadMembership membership =
                new MemoryThreadMembership(
                        "memory-user-agent",
                        current.threadKey(),
                        301L,
                        MemoryThreadMembershipRole.TRIGGER,
                        true,
                        1.0d,
                        Instant.parse("2026-04-01T09:00:00Z"),
                        Instant.parse("2026-04-01T09:00:00Z"));
        MemoryThreadEvent observation =
                new MemoryThreadEvent(
                        "memory-user-agent",
                        current.threadKey(),
                        "evt-3",
                        1,
                        MemoryThreadEventType.OBSERVATION,
                        Instant.parse("2026-04-01T09:00:00Z"),
                        Map.of("summary", "Project is still in flight."),
                        1,
                        false,
                        0.90d,
                        Instant.parse("2026-04-01T09:00:00Z"));

        MemoryThreadProjection dormantProjection =
                reducer.reduce(
                        current,
                        List.of(observation),
                        List.of(membership),
                        Instant.parse("2026-04-12T00:00:00Z"));
        MemoryThreadProjection closedProjection =
                reducer.reduce(
                        current,
                        List.of(observation),
                        List.of(membership),
                        Instant.parse("2026-04-25T00:00:00Z"));

        assertThat(dormantProjection.objectState()).isEqualTo(MemoryThreadObjectState.STABLE);
        assertThat(dormantProjection.lifecycleStatus())
                .isEqualTo(MemoryThreadLifecycleStatus.DORMANT);
        assertThat(closedProjection.objectState()).isEqualTo(MemoryThreadObjectState.STABLE);
        assertThat(closedProjection.lifecycleStatus())
                .isEqualTo(MemoryThreadLifecycleStatus.CLOSED);
    }

    private static ThreadDecision decision(String threadKey) {
        return new ThreadDecision(
                ThreadDecision.Action.CREATE,
                threadKey,
                MemoryThreadType.TOPIC,
                "topic",
                "concept:travel",
                301L,
                null);
    }

    private static ThreadIntakeSignal signalWithoutMarkers(long itemId, String content) {
        return new ThreadIntakeSignal(
                "memory-user-agent",
                itemId,
                content,
                Instant.parse("2026-04-20T09:00:00Z"),
                MemoryThreadType.TOPIC,
                List.of(
                        new ThreadIntakeSignal.AnchorCandidate(
                                "topic", "concept:travel", List.of(), 0.80d)),
                new ThreadIntakeSignal.ThreadEligibilityScore(0.70d, 0.75d, 0.20d),
                List.of(itemId),
                List.of(),
                List.of(),
                0.88d);
    }

    private static MemoryThreadProjection currentProjection() {
        return new MemoryThreadProjection(
                "memory-user-agent",
                "work:project:project:alpha-auth-refactor",
                MemoryThreadType.WORK,
                "project",
                "project:alpha-auth-refactor",
                "Alpha Auth Refactor",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "Auth migration is underway",
                Map.of(),
                1,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"),
                null,
                1,
                1,
                Instant.parse("2026-04-20T09:00:00Z"),
                Instant.parse("2026-04-20T09:00:00Z"));
    }

    private static MemoryThreadEvent event(
            String threadKey,
            String eventKey,
            long eventSeq,
            Instant eventTime,
            MemoryThreadEventType eventType,
            Map<String, Object> payload) {
        return new MemoryThreadEvent(
                "memory-user-agent",
                threadKey,
                eventKey,
                eventSeq,
                eventType,
                eventTime,
                payload,
                1,
                true,
                0.95d,
                eventTime);
    }
}
