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
package com.openmemind.ai.memory.benchmark.halumem;

import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.evaluator.Evaluator;
import com.openmemind.ai.memory.core.Memory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HaluMemEvaluator {

    private final Memory memory;
    private final Evaluator integrityJudge;
    private final Evaluator accuracyJudge;
    private final Evaluator qaJudge;

    public HaluMemEvaluator(
            Memory memory, Evaluator integrityJudge, Evaluator accuracyJudge, Evaluator qaJudge) {
        this.memory = memory;
        this.integrityJudge = integrityJudge;
        this.accuracyJudge = accuracyJudge;
        this.qaJudge = qaJudge;
    }

    public HaluMemReport aggregate(List<QuestionResult> results) {
        List<QuestionResult> integrity =
                results.stream().filter(result -> "integrity".equals(result.category())).toList();
        List<QuestionResult> accuracy =
                results.stream().filter(result -> "accuracy".equals(result.category())).toList();
        List<QuestionResult> qa =
                results.stream().filter(result -> "qa".equals(result.category())).toList();

        Map<String, Long> qaVerdicts = new LinkedHashMap<>();
        for (QuestionResult result : qa) {
            if (result.judgment() != null) {
                qaVerdicts.merge(result.judgment().verdict(), 1L, Long::sum);
            }
        }

        return new HaluMemReport(
                average(integrity),
                distribution(integrity),
                average(accuracy),
                distribution(accuracy),
                accuracy.isEmpty()
                        ? 0.0
                        : accuracy.stream()
                                        .filter(
                                                result ->
                                                        result.judgment() != null
                                                                && result.judgment().score() >= 1.0)
                                        .count()
                                / (double) accuracy.size(),
                qaVerdicts);
    }

    Memory memory() {
        return memory;
    }

    Evaluator integrityJudge() {
        return integrityJudge;
    }

    Evaluator accuracyJudge() {
        return accuracyJudge;
    }

    Evaluator qaJudge() {
        return qaJudge;
    }

    private Map<Integer, Long> distribution(List<QuestionResult> results) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (QuestionResult result : results) {
            if (result.judgment() != null) {
                distribution.merge((int) result.judgment().score(), 1L, Long::sum);
            }
        }
        return distribution;
    }

    private double average(List<QuestionResult> results) {
        return results.isEmpty()
                ? 0.0
                : results.stream()
                        .filter(result -> result.judgment() != null)
                        .mapToDouble(result -> result.judgment().score())
                        .average()
                        .orElse(0.0);
    }
}
