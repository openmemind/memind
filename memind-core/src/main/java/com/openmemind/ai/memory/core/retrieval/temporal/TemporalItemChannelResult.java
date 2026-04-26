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

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;

public record TemporalItemChannelResult(
        List<ScoredResult> items,
        boolean enabled,
        boolean constraintPresent,
        boolean degraded,
        int candidateCount) {

    public TemporalItemChannelResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static TemporalItemChannelResult empty(boolean enabled, boolean constraintPresent) {
        return new TemporalItemChannelResult(List.of(), enabled, constraintPresent, false, 0);
    }

    public static TemporalItemChannelResult degraded(boolean enabled, boolean constraintPresent) {
        return new TemporalItemChannelResult(List.of(), enabled, constraintPresent, true, 0);
    }
}
