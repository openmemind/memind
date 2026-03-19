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
package com.openmemind.ai.memory.core.retrieval.truncation;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;

/**
 * Adaptive truncation result
 *
 * @param results      List of results after truncation
 * @param originalSize Number of results before truncation
 * @param cutoffScore  Cutoff score (results below this score are removed, 0.0 when no truncation)
 * @param triggeredBy  Rule that triggered the truncation ("elbow" | "drop_ratio" | "none")
 */
public record TruncationResult(
        List<ScoredResult> results, int originalSize, double cutoffScore, String triggeredBy) {

    public static final String TRIGGER_ELBOW = "elbow";
    public static final String TRIGGER_DROP_RATIO = "drop_ratio";
    public static final String TRIGGER_NONE = "none";

    /** Unchanged (returned as is) */
    public static TruncationResult unchanged(List<ScoredResult> results) {
        return new TruncationResult(results, results.size(), 0.0, TRIGGER_NONE);
    }
}
