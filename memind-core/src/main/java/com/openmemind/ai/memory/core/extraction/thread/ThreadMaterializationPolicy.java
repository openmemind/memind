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

import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import java.time.Duration;
import java.util.Objects;

/**
 * Deterministic thresholds for thread materialization.
 */
public record ThreadMaterializationPolicy(
        String version,
        double matchThreshold,
        double minimumCreateScoreAfterTwoHit,
        int maxCandidateThreads,
        Duration dormantAfter,
        Duration closeAfter) {

    public ThreadMaterializationPolicy {
        Objects.requireNonNull(version, "version");
        dormantAfter = Objects.requireNonNull(dormantAfter, "dormantAfter");
        closeAfter = Objects.requireNonNull(closeAfter, "closeAfter");
        validateUnitInterval(matchThreshold, "matchThreshold");
        validateUnitInterval(minimumCreateScoreAfterTwoHit, "minimumCreateScoreAfterTwoHit");
        if (maxCandidateThreads <= 0) {
            throw new IllegalArgumentException("maxCandidateThreads must be positive");
        }
        if (dormantAfter.isNegative() || dormantAfter.isZero()) {
            throw new IllegalArgumentException("dormantAfter must be positive");
        }
        if (closeAfter.isNegative() || closeAfter.isZero()) {
            throw new IllegalArgumentException("closeAfter must be positive");
        }
        if (closeAfter.compareTo(dormantAfter) < 0) {
            throw new IllegalArgumentException("closeAfter must be >= dormantAfter");
        }
    }

    public static ThreadMaterializationPolicy v1() {
        return ThreadMaterializationPolicyFactory.from(MemoryThreadOptions.defaults());
    }

    public double createScore(
            MemoryThreadType threadType, ThreadIntakeSignal.ThreadEligibilityScore eligibility) {
        Objects.requireNonNull(threadType, "threadType");
        Objects.requireNonNull(eligibility, "eligibility");
        return eligibility.scoreFor(threadType);
    }

    public boolean isEligible(ThreadIntakeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        return createScore(signal.threadType(), signal.eligibility())
                >= minimumCreateScoreAfterTwoHit;
    }

    private static void validateUnitInterval(double value, String field) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
