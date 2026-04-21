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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadDecisionEngineTest {

    @Test
    void canonicalizesRelationshipParticipantsBeforeIdentityLookup() {
        ThreadIntakeSignal signal =
                ThreadIntakeSignal.relationship(
                        "memory-user-agent",
                        301L,
                        "Alice asked whether I had finished the draft.",
                        List.of("special:self", "person:alice"),
                        Instant.parse("2026-04-20T09:00:00Z"));

        ThreadDecision decision =
                new ThreadDecisionEngine(ThreadMaterializationPolicy.v1())
                        .decide(signal, List.of());

        assertThat(decision.threadKey())
                .isEqualTo("relationship:relationship:special:self|person:alice");
        assertThat(decision.action()).isEqualTo(ThreadDecision.Action.CREATE);
    }

    @Test
    void attachesToExistingCanonicalThreadAfterSpecialUserNormalization() {
        ThreadIntakeSignal signal =
                ThreadIntakeSignal.relationship(
                        "memory-user-agent",
                        302L,
                        "Alice followed up with the user again.",
                        List.of("special:user", "person:alice"),
                        Instant.parse("2026-04-20T10:00:00Z"));

        MemoryThreadProjection existing =
                projection("relationship:relationship:special:self|person:alice");

        ThreadDecision decision =
                new ThreadDecisionEngine(ThreadMaterializationPolicy.v1())
                        .decide(signal, List.of(existing));

        assertThat(decision.action()).isEqualTo(ThreadDecision.Action.ATTACH);
        assertThat(decision.threadKey()).isEqualTo(existing.threadKey());
    }

    @Test
    void ignoresSignalBelowCreationThresholdWhenNoCanonicalThreadExists() {
        ThreadIntakeSignal signal =
                new ThreadIntakeSignal(
                        "memory-user-agent",
                        303L,
                        "The user briefly mentioned travel.",
                        Instant.parse("2026-04-20T11:00:00Z"),
                        MemoryThreadType.TOPIC,
                        List.of(
                                new ThreadIntakeSignal.AnchorCandidate(
                                        "topic", "concept:travel", List.of(), 0.40d)),
                        new ThreadIntakeSignal.ThreadEligibilityScore(0.30d, 0.35d, 0.10d),
                        List.of(303L),
                        List.of(),
                        List.of(),
                        0.40d);

        ThreadDecision decision =
                new ThreadDecisionEngine(ThreadMaterializationPolicy.v1())
                        .decide(signal, List.of());

        assertThat(decision.action()).isEqualTo(ThreadDecision.Action.IGNORE);
        assertThat(decision.reason()).contains("below creation threshold");
    }

    private static MemoryThreadProjection projection(String threadKey) {
        return new MemoryThreadProjection(
                "memory-user-agent",
                threadKey,
                MemoryThreadType.RELATIONSHIP,
                "relationship",
                "special:self|person:alice",
                "Alice Relationship",
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                "Alice followed up again",
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
}
