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
package com.openmemind.ai.memory.core.retrieval.temporal;

import java.time.Instant;
import java.util.Objects;

public record TemporalConstraint(
        Instant startInclusive,
        Instant endExclusive,
        Instant referenceTime,
        TemporalGranularity granularity,
        TemporalDirection direction,
        TemporalConstraintSource source,
        double confidence) {

    public TemporalConstraint {
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endExclusive, "endExclusive");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(granularity, "granularity");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("startInclusive must be before endExclusive");
        }
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }
}
