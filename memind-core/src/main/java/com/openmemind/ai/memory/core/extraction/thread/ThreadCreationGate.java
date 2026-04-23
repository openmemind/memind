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

import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.util.Objects;

/**
 * Two-hit creation gate for canonical anchors.
 */
final class ThreadCreationGate {

    private final ThreadMaterializationPolicy policy;

    ThreadCreationGate(ThreadMaterializationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    GateEvaluation evaluate(
            ThreadAnchorCanonicalizer.CanonicalThreadAnchor anchor,
            ThreadIntakeSignal signal,
            CreationSupportState creationSupport) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(creationSupport, "creationSupport");

        String threadKey = anchor.threadKey();
        if (creationSupport.supportCountByThreadKey().getOrDefault(threadKey, 0) < 2) {
            return GateEvaluation.rejected("support below two-hit gate");
        }
        if (creationSupport.createScoreByThreadKey().getOrDefault(threadKey, 0.0d)
                < policy.minimumCreateScoreAfterTwoHit()) {
            return GateEvaluation.rejected("create score below gate");
        }
        if ((anchor.threadType() == MemoryThreadType.WORK
                        || anchor.threadType() == MemoryThreadType.CASE)
                && !creationSupport.markerEligibleThreadKeys().contains(threadKey)) {
            return GateEvaluation.rejected("marker eligibility required");
        }
        return GateEvaluation.admitted(
                ThreadDecision.create(
                        anchor.threadKey(),
                        anchor.threadType(),
                        anchor.anchorKind(),
                        anchor.anchorKey(),
                        signal.triggerItemId()));
    }

    record GateEvaluation(ThreadDecision decision, String failureReason, boolean supportBelowGate) {

        GateEvaluation {
            if ((decision == null) == (failureReason == null)) {
                throw new IllegalArgumentException(
                        "exactly one of decision or failureReason must be present");
            }
        }

        static GateEvaluation admitted(ThreadDecision decision) {
            return new GateEvaluation(Objects.requireNonNull(decision, "decision"), null, false);
        }

        static GateEvaluation rejected(String failureReason) {
            return new GateEvaluation(null, Objects.requireNonNull(failureReason, "failureReason"), true);
        }

        boolean admitted() {
            return decision != null;
        }
    }
}
