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
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThreadDecisionEngineTest {

    @Test
    void doesNotExposeLegacyDecideEntryPoints() {
        assertThat(publicDeclaredMethodsNamed("decide")).isEmpty();
    }

    @Test
    void noSignalsIsReportedAsNoSignal() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(300L),
                        MemoryThreadType.TOPIC,
                        List.of(),
                        List.of(),
                        new CreationSupportState());

        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.IGNORE);
        assertThat(outcome.nonAdmissionDisposition())
                .isEqualTo(ThreadNonAdmissionDisposition.NO_SIGNAL);
        assertThat(outcome.primaryReason()).isEqualTo("no canonical signals");
        assertThat(outcome.ambiguityBlocked()).isFalse();
        assertThat(outcome.supportBelowGate()).isFalse();
        assertThat(outcome.canonicalSignalCount()).isZero();
        assertThat(outcome.candidateCount()).isZero();
        assertThat(outcome.topCandidateScore()).isNull();
    }

    @Test
    void metricsRecordStructuredNonAdmissionReason() {
        RecordingThreadDerivationMetrics metrics = new RecordingThreadDerivationMetrics();
        ThreadDecisionEngine engine =
                new ThreadDecisionEngine(ThreadMaterializationPolicy.v1(), metrics);

        engine.resolve(
                item(3001L),
                MemoryThreadType.TOPIC,
                List.of(),
                List.of(),
                new CreationSupportState());

        assertThat(metrics.nonAdmissions()).containsExactly("NO_SIGNAL:no canonical signals");
    }

    @Test
    void resolveCanonicalizesRelationshipParticipantsBeforeCreation() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());
        ThreadIntakeSignal signal =
                ThreadIntakeSignal.relationship(
                        "memory-user-agent",
                        301L,
                        "Alice asked whether I had finished the draft.",
                        List.of("special:self", "person:alice"),
                        Instant.parse("2026-04-20T09:00:00Z"));

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(301L),
                        MemoryThreadType.RELATIONSHIP,
                        List.of(canonicalSignal(signal)),
                        List.of(),
                        creationSupport(
                                "relationship:relationship:special:self|person:alice",
                                301L,
                                2,
                                1.0d));

        assertThat(outcome.decision().threadKey())
                .isEqualTo("relationship:relationship:special:self|person:alice");
        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.CREATE);
        assertThat(outcome.role()).isEqualTo(MemoryThreadMembershipRole.TRIGGER);
        assertThat(outcome.evidence().hasFamily(ThreadAdmissionEvidence.TWO_HIT_SUPPORT)).isTrue();
    }

    @Test
    void resolveAttachesToExistingCanonicalThreadAfterSpecialUserNormalization() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());
        ThreadIntakeSignal signal =
                ThreadIntakeSignal.relationship(
                        "memory-user-agent",
                        302L,
                        "Alice followed up with the user again.",
                        List.of("special:user", "person:alice"),
                        Instant.parse("2026-04-20T10:00:00Z"));

        MemoryThreadProjection existing =
                projection("relationship:relationship:special:self|person:alice");

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(302L),
                        MemoryThreadType.RELATIONSHIP,
                        List.of(canonicalSignal(signal)),
                        List.of(exactAnchorCandidate(existing, 302L)),
                        new CreationSupportState());

        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.ATTACH);
        assertThat(outcome.decision().threadKey()).isEqualTo(existing.threadKey());
        assertThat(outcome.signal()).isEqualTo(signal);
        assertThat(outcome.evidence().hasFamily(ThreadAdmissionEvidence.EXACT_ANCHOR)).isTrue();
    }

    @Test
    void resolveIgnoresSignalBelowCreationThresholdWhenNoCanonicalThreadExists() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());
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

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(303L),
                        MemoryThreadType.TOPIC,
                        List.of(canonicalSignal(signal)),
                        List.of(),
                        creationSupport("topic:topic:concept:travel", 303L, 2, 0.10d));

        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.IGNORE);
        assertThat(outcome.decision().reason()).contains("create score below gate");
        assertThat(outcome.evidence().isEmpty()).isTrue();
        assertThat(outcome.nonAdmissionDisposition())
                .isEqualTo(ThreadNonAdmissionDisposition.DEFER);
        assertThat(outcome.primaryReason()).isEqualTo("create score below gate");
        assertThat(outcome.supportBelowGate()).isTrue();
        assertThat(outcome.ambiguityBlocked()).isFalse();
        assertThat(outcome.canonicalSignalCount()).isEqualTo(1);
        assertThat(outcome.candidateCount()).isZero();
        assertThat(outcome.topCandidateScore()).isNull();
    }

    @Test
    void belowTwoHitGateIsReportedAsDefer() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());
        ThreadIntakeSignal signal = signal("concept:travel", 3031L);

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(3031L),
                        MemoryThreadType.TOPIC,
                        List.of(canonicalSignal(signal)),
                        List.of(),
                        creationSupport("topic:topic:concept:travel", 3031L, 1, 1.0d));

        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.IGNORE);
        assertThat(outcome.nonAdmissionDisposition())
                .isEqualTo(ThreadNonAdmissionDisposition.DEFER);
        assertThat(outcome.primaryReason()).isEqualTo("support below two-hit gate");
        assertThat(outcome.supportBelowGate()).isTrue();
        assertThat(outcome.ambiguityBlocked()).isFalse();
    }

    @Test
    void ambiguousRunnerUpInsideMarginIsIgnored() {
        ThreadDecisionEngine engine = new ThreadDecisionEngine(ThreadMaterializationPolicy.v1());
        ThreadIntakeSignal signal = signal("concept:travel", 304L);
        ThreadAnchorCanonicalizer canonicalizer = new ThreadAnchorCanonicalizer();
        CanonicalizedSignal canonicalSignal =
                new CanonicalizedSignal(signal, canonicalizer.canonicalize(signal).orElseThrow());
        ThreadCandidateScore topCandidate =
                new ThreadCandidateScore(
                        new ThreadCandidate(
                                projection("topic:topic:concept:travel"),
                                Set.of(304L),
                                false,
                                true),
                        0.0d,
                        0.95d,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.88d,
                        0,
                        0.95d);
        ThreadCandidateScore runnerUp =
                new ThreadCandidateScore(
                        new ThreadCandidate(
                                projection("topic:topic:concept:japan"), Set.of(305L), false, true),
                        0.0d,
                        0.90d,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.80d,
                        0,
                        0.90d);

        ThreadDecisionOutcome outcome =
                engine.resolve(
                        item(304L),
                        MemoryThreadType.TOPIC,
                        List.of(canonicalSignal),
                        List.of(topCandidate, runnerUp),
                        new CreationSupportState());

        assertThat(outcome.decision().action()).isEqualTo(ThreadDecision.Action.IGNORE);
        assertThat(outcome.decision().reason()).contains("ambiguous");
        assertThat(outcome.nonAdmissionDisposition())
                .isEqualTo(ThreadNonAdmissionDisposition.REJECT);
        assertThat(outcome.primaryReason()).isEqualTo("ambiguous candidate scores");
        assertThat(outcome.ambiguityBlocked()).isTrue();
        assertThat(outcome.supportBelowGate()).isFalse();
        assertThat(outcome.canonicalSignalCount()).isEqualTo(1);
        assertThat(outcome.candidateCount()).isEqualTo(2);
        assertThat(outcome.topCandidateScore()).isEqualTo(0.88d);
    }

    private static ThreadIntakeSignal signal(String anchorKey, long triggerItemId) {
        return new ThreadIntakeSignal(
                "memory-user-agent",
                triggerItemId,
                "The user briefly mentioned travel.",
                Instant.parse("2026-04-20T11:00:00Z"),
                MemoryThreadType.TOPIC,
                List.of(
                        new ThreadIntakeSignal.AnchorCandidate(
                                "topic", anchorKey, List.of(), 1.0d)),
                new ThreadIntakeSignal.ThreadEligibilityScore(1.0d, 0.95d, 0.20d),
                List.of(triggerItemId),
                List.of(),
                List.of(),
                0.95d);
    }

    private static CanonicalizedSignal canonicalSignal(ThreadIntakeSignal signal) {
        return new CanonicalizedSignal(
                signal, new ThreadAnchorCanonicalizer().canonicalize(signal).orElseThrow());
    }

    private static ThreadCandidateScore exactAnchorCandidate(
            MemoryThreadProjection projection, long itemId) {
        return new ThreadCandidateScore(
                new ThreadCandidate(projection, Set.of(itemId), true, true),
                1.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                1.0d,
                0,
                1.0d);
    }

    private static CreationSupportState creationSupport(
            String threadKey, long latestSupportingItemId, int supportCount, double createScore) {
        return new CreationSupportState(
                Map.of(threadKey, supportCount),
                Map.of(threadKey, createScore),
                Set.of(threadKey),
                Map.of(threadKey, latestSupportingItemId));
    }

    private static List<Method> publicDeclaredMethodsNamed(String methodName) {
        return List.of(ThreadDecisionEngine.class.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .toList();
    }

    private static com.openmemind.ai.memory.core.data.MemoryItem item(long itemId) {
        return new com.openmemind.ai.memory.core.data.MemoryItem(
                itemId,
                "memory-user-agent",
                "The user briefly mentioned travel.",
                com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                com.openmemind.ai.memory.core.data.enums.MemoryCategory.EVENT,
                "conversation",
                "vec-" + itemId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.parse("2026-04-20T11:00:00Z"),
                Instant.parse("2026-04-20T11:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-20T11:00:00Z"),
                com.openmemind.ai.memory.core.data.enums.MemoryItemType.FACT);
    }

    private static MemoryThreadProjection projection(String threadKey) {
        String[] parts = threadKey.split(":", 3);
        MemoryThreadType threadType = MemoryThreadType.valueOf(parts[0].toUpperCase());
        String anchorKind = parts[1];
        String anchorKey = parts[2];
        return new MemoryThreadProjection(
                "memory-user-agent",
                threadKey,
                threadType,
                anchorKind,
                anchorKey,
                anchorKey,
                MemoryThreadLifecycleStatus.ACTIVE,
                MemoryThreadObjectState.ONGOING,
                anchorKey,
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
