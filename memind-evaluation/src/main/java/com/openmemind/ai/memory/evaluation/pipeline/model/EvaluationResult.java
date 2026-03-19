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
package com.openmemind.ai.memory.evaluation.pipeline.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Complete evaluation result, including run-level and category-level statistics
 *
 */
public record EvaluationResult(
        int totalQuestions,
        int correct,
        double accuracy,
        /** Overall accuracy for each run */
        List<Double> runScores,
        /** Mean of multiple runs */
        double meanAccuracy,
        /** Standard deviation of multiple runs */
        double stdAccuracy,
        /** Detailed statistics by category */
        Map<String, CategoryStats> categoryStats,
        List<QuestionJudgment> details) {

    /**
     * Statistical information by category
     */
    public record CategoryStats(
            double mean, double std, List<Double> individualRuns, int total, int correct) {}

    public static EvaluationResult from(List<QuestionJudgment> judgments) {
        int total = judgments.size();
        long correctCount = judgments.stream().filter(QuestionJudgment::correct).count();
        double acc = total == 0 ? 0.0 : (double) correctCount / total;

        // Determine the number of runs (take the maximum value)
        int numRuns =
                judgments.stream()
                        .mapToInt(j -> j.runScores() == null ? 1 : j.runScores().size())
                        .max()
                        .orElse(1);

        // Calculate overall accuracy for each run
        List<Double> runScores =
                IntStream.range(0, numRuns)
                        .mapToObj(
                                run ->
                                        judgments.stream()
                                                .mapToDouble(
                                                        j -> {
                                                            if (j.runScores() == null
                                                                    || j.runScores().size()
                                                                            <= run) {
                                                                return j.correct() ? 1.0 : 0.0;
                                                            }
                                                            return j.runScores().get(run);
                                                        })
                                                .average()
                                                .orElse(0.0))
                        .toList();

        double mean = runScores.stream().mapToDouble(d -> d).average().orElse(acc);
        double std = calcStd(runScores, mean);

        // Group statistics by category
        Map<String, CategoryStats> unsorted = new LinkedHashMap<>();
        judgments.stream()
                .collect(Collectors.groupingBy(QuestionJudgment::category))
                .forEach(
                        (cat, catJudgments) -> {
                            int catTotal = catJudgments.size();
                            int catCorrectCount =
                                    (int)
                                            catJudgments.stream()
                                                    .filter(QuestionJudgment::correct)
                                                    .count();
                            List<Double> catRunScores =
                                    IntStream.range(0, numRuns)
                                            .mapToObj(
                                                    run ->
                                                            catJudgments.stream()
                                                                    .mapToDouble(
                                                                            j -> {
                                                                                if (j.runScores()
                                                                                                == null
                                                                                        || j.runScores()
                                                                                                        .size()
                                                                                                <= run) {
                                                                                    return j
                                                                                                    .correct()
                                                                                            ? 1.0
                                                                                            : 0.0;
                                                                                }
                                                                                return j.runScores()
                                                                                        .get(run);
                                                                            })
                                                                    .average()
                                                                    .orElse(0.0))
                                            .toList();
                            double catMean =
                                    catRunScores.stream().mapToDouble(d -> d).average().orElse(0.0);
                            double catStd = calcStd(catRunScores, catMean);
                            unsorted.put(
                                    cat,
                                    new CategoryStats(
                                            catMean,
                                            catStd,
                                            catRunScores,
                                            catTotal,
                                            catCorrectCount));
                        });

        // Sort by category key in ascending order to ensure output determinism
        Map<String, CategoryStats> catStats =
                unsorted.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (a, b) -> a,
                                        LinkedHashMap::new));

        return new EvaluationResult(
                total, (int) correctCount, acc, runScores, mean, std, catStats, judgments);
    }

    // Use population standard deviation, consistent with Python numpy.std() default behavior
    private static double calcStd(List<Double> values, double mean) {
        if (values.size() <= 1) {
            return 0.0;
        }
        double variance =
                values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        return Math.sqrt(variance);
    }
}
