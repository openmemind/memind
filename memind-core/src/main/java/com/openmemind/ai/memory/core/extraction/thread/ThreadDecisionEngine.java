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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic thread attach/create/ignore engine.
 */
public final class ThreadDecisionEngine {

    private static final double AMBIGUITY_MARGIN = 0.10d;

    private final ThreadMaterializationPolicy policy;
    private final ThreadAnchorCanonicalizer canonicalizer = new ThreadAnchorCanonicalizer();
    private final ThreadCreationGate creationGate;
    private final ThreadDerivationMetrics metrics;

    public ThreadDecisionEngine(ThreadMaterializationPolicy policy) {
        this(policy, ThreadDerivationMetrics.NOOP);
    }

    ThreadDecisionEngine(ThreadMaterializationPolicy policy, ThreadDerivationMetrics metrics) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.creationGate = new ThreadCreationGate(policy);
    }

    public ThreadDecisionOutcome resolve(
            MemoryItem item,
            com.openmemind.ai.memory.core.data.enums.MemoryThreadType threadType,
            List<CanonicalizedSignal> canonicalSignals,
            List<ThreadCandidateScore> scoredCandidates,
            CreationSupportState creationSupport) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(threadType, "threadType");
        List<CanonicalizedSignal> signals =
                canonicalSignals == null ? List.of() : List.copyOf(canonicalSignals);
        List<ThreadCandidateScore> candidates =
                scoredCandidates == null ? List.of() : List.copyOf(scoredCandidates);
        CreationSupportState support =
                creationSupport == null ? new CreationSupportState() : creationSupport;
        Double topCandidateScore = candidates.isEmpty() ? null : candidates.getFirst().finalScore();

        if (signals.isEmpty()) {
            return ignoreOutcome(
                    item.id(),
                    "no canonical signals",
                    ThreadNonAdmissionDisposition.NO_SIGNAL,
                    false,
                    false,
                    signals.size(),
                    candidates.size(),
                    topCandidateScore);
        }

        List<ThreadCandidateScore> exactAnchorMatches =
                candidates.stream().filter(ThreadCandidateScore::isExactAnchorMatch).toList();
        if (exactAnchorMatches.size() > 1) {
            return ignoreOutcome(
                    item.id(),
                    "ambiguous exact anchor",
                    ThreadNonAdmissionDisposition.REJECT,
                    true,
                    false,
                    signals.size(),
                    candidates.size(),
                    topCandidateScore);
        }
        if (exactAnchorMatches.size() == 1) {
            ThreadCandidateScore exactAnchorMatch = exactAnchorMatches.getFirst();
            return attachOutcome(
                    item.id(),
                    representativeSignal(
                            signals, exactAnchorMatch.candidate().thread().threadKey()),
                    exactAnchorMatch.candidate().thread(),
                    1.0d,
                    ThreadAdmissionEvidence.exactAnchor(),
                    signals.size(),
                    candidates.size(),
                    topCandidateScore);
        }

        if (!candidates.isEmpty()) {
            ThreadCandidateScore topCandidate = candidates.getFirst();
            ThreadCandidateScore runnerUp = candidates.size() > 1 ? candidates.get(1) : null;
            boolean unambiguous =
                    runnerUp == null
                            || topCandidate.dominantFamilyRank() < runnerUp.dominantFamilyRank()
                            || (topCandidate.finalScore() - runnerUp.finalScore())
                                    > AMBIGUITY_MARGIN;
            if (topCandidate.finalScore() >= policy.matchThreshold()
                    && topCandidate.hasNonEntitySupport()
                    && unambiguous) {
                return attachOutcome(
                        item.id(),
                        representativeSignal(
                                signals, topCandidate.candidate().thread().threadKey()),
                        topCandidate.candidate().thread(),
                        topCandidate.finalScore(),
                        ThreadAdmissionEvidence.fromCandidate(topCandidate),
                        signals.size(),
                        candidates.size(),
                        topCandidateScore);
            }
            if (topCandidate.finalScore() >= policy.matchThreshold()
                    && topCandidate.hasNonEntitySupport()
                    && !unambiguous) {
                return ignoreOutcome(
                        item.id(),
                        "ambiguous candidate scores",
                        ThreadNonAdmissionDisposition.REJECT,
                        true,
                        false,
                        signals.size(),
                        candidates.size(),
                        topCandidateScore);
            }
        }

        if (signals.size() != 1) {
            return ignoreOutcome(
                    item.id(),
                    "ambiguous canonical signals",
                    ThreadNonAdmissionDisposition.REJECT,
                    true,
                    false,
                    signals.size(),
                    candidates.size(),
                    topCandidateScore);
        }

        CanonicalizedSignal creationSignal = signals.getFirst();
        ThreadCreationGate.GateEvaluation gateEvaluation =
                creationGate.evaluate(creationSignal.anchor(), creationSignal.signal(), support);
        if (gateEvaluation.admitted()) {
            return new ThreadDecisionOutcome(
                    gateEvaluation.decision(),
                    creationSignal.signal(),
                    Objects.equals(
                                    support.latestSupportingItemIdByThreadKey()
                                            .get(creationSignal.anchor().threadKey()),
                                    item.id())
                            ? MemoryThreadMembershipRole.TRIGGER
                            : MemoryThreadMembershipRole.CORE,
                    true,
                    1.0d,
                    ThreadAdmissionEvidence.twoHitSupport(
                            support.supportCountByThreadKey()
                                    .getOrDefault(creationSignal.anchor().threadKey(), 0)),
                    null,
                    null,
                    false,
                    false,
                    signals.size(),
                    candidates.size(),
                    topCandidateScore);
        }
        return ignoreOutcome(
                item.id(),
                gateEvaluation.failureReason(),
                ThreadNonAdmissionDisposition.DEFER,
                false,
                gateEvaluation.supportBelowGate(),
                signals.size(),
                candidates.size(),
                topCandidateScore);
    }

    private static ThreadDecisionOutcome attachOutcome(
            long triggerItemId,
            ThreadIntakeSignal signal,
            MemoryThreadProjection thread,
            double relevanceWeight,
            ThreadAdmissionEvidence evidence,
            int canonicalSignalCount,
            int candidateCount,
            Double topCandidateScore) {
        return new ThreadDecisionOutcome(
                ThreadDecision.attach(
                        thread.threadKey(),
                        thread.threadType(),
                        thread.anchorKind(),
                        thread.anchorKey(),
                        triggerItemId),
                signal,
                MemoryThreadMembershipRole.TRIGGER,
                true,
                relevanceWeight,
                evidence,
                null,
                null,
                false,
                false,
                canonicalSignalCount,
                candidateCount,
                topCandidateScore);
    }

    private ThreadDecisionOutcome ignoreOutcome(
            long triggerItemId,
            String reason,
            ThreadNonAdmissionDisposition nonAdmissionDisposition,
            boolean ambiguityBlocked,
            boolean supportBelowGate,
            int canonicalSignalCount,
            int candidateCount,
            Double topCandidateScore) {
        metrics.onNonAdmission(nonAdmissionDisposition, reason);
        return new ThreadDecisionOutcome(
                ThreadDecision.ignore(triggerItemId, reason),
                null,
                null,
                false,
                0.0d,
                ThreadAdmissionEvidence.none(),
                nonAdmissionDisposition,
                reason,
                ambiguityBlocked,
                supportBelowGate,
                canonicalSignalCount,
                candidateCount,
                topCandidateScore);
    }

    private static ThreadIntakeSignal representativeSignal(
            List<CanonicalizedSignal> canonicalSignals, String threadKey) {
        return canonicalSignals.stream()
                .filter(signal -> signal.anchor().threadKey().equals(threadKey))
                .map(CanonicalizedSignal::signal)
                .findFirst()
                .orElseGet(() -> canonicalSignals.getFirst().signal());
    }
}
