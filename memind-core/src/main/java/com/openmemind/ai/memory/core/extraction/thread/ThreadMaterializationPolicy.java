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
import java.time.Duration;
import java.util.Objects;

/**
 * Deterministic thresholds for thread materialization.
 */
public record ThreadMaterializationPolicy(
        String version,
        double matchThreshold,
        double workCreateThreshold,
        double caseCreateThreshold,
        double relationshipCreateThreshold,
        double topicCreateThreshold,
        Duration dormantAfter) {

    public ThreadMaterializationPolicy {
        Objects.requireNonNull(version, "version");
        dormantAfter = Objects.requireNonNull(dormantAfter, "dormantAfter");
        validateUnitInterval(matchThreshold, "matchThreshold");
        validateUnitInterval(workCreateThreshold, "workCreateThreshold");
        validateUnitInterval(caseCreateThreshold, "caseCreateThreshold");
        validateUnitInterval(relationshipCreateThreshold, "relationshipCreateThreshold");
        validateUnitInterval(topicCreateThreshold, "topicCreateThreshold");
        if (dormantAfter.isNegative() || dormantAfter.isZero()) {
            throw new IllegalArgumentException("dormantAfter must be positive");
        }
    }

    public static ThreadMaterializationPolicy v1() {
        return new ThreadMaterializationPolicy(
                "thread-core-v1", 0.78d, 0.72d, 0.74d, 0.75d, 0.70d, Duration.ofDays(14));
    }

    public double creationThreshold(MemoryThreadType threadType) {
        return switch (Objects.requireNonNull(threadType, "threadType")) {
            case WORK -> workCreateThreshold;
            case CASE -> caseCreateThreshold;
            case RELATIONSHIP -> relationshipCreateThreshold;
            case TOPIC -> topicCreateThreshold;
        };
    }

    public boolean isEligible(ThreadIntakeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        return signal.eligibility().scoreFor(signal.threadType())
                >= creationThreshold(signal.threadType());
    }

    private static void validateUnitInterval(double value, String field) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
