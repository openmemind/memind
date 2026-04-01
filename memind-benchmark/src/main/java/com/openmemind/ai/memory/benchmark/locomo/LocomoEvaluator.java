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
package com.openmemind.ai.memory.benchmark.locomo;

import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.evaluator.Evaluator;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import com.openmemind.ai.memory.benchmark.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.benchmark.core.report.BenchmarkReport;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LocomoEvaluator {

    private static final String BenchmarkAgentId = "memind-benchmark";

    private final Memory memory;
    private final StructuredChatClient answerClient;
    private final Evaluator judgeEvaluator;
    private final String memoryModel;
    private final String evalModel;
    private final PromptTemplate answerPrompt;

    public LocomoEvaluator(
            Memory memory,
            StructuredChatClient answerClient,
            Evaluator judgeEvaluator,
            String memoryModel,
            String evalModel) {
        this(
                memory,
                answerClient,
                judgeEvaluator,
                memoryModel,
                evalModel,
                PromptTemplate.fromClasspath("prompts/answer-with-memory.txt"));
    }

    LocomoEvaluator(
            Memory memory,
            StructuredChatClient answerClient,
            Evaluator judgeEvaluator,
            String memoryModel,
            String evalModel,
            PromptTemplate answerPrompt) {
        this.memory = memory;
        this.answerClient = answerClient;
        this.judgeEvaluator = judgeEvaluator;
        this.memoryModel = Objects.requireNonNull(memoryModel, "memoryModel");
        this.evalModel = Objects.requireNonNull(evalModel, "evalModel");
        this.answerPrompt = Objects.requireNonNull(answerPrompt, "answerPrompt");
    }

    public QuestionResult evaluate(
            LocomoDataset.LocomoUser user, LocomoDataset.LocomoQuestion question) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(answerClient, "answerClient");
        Objects.requireNonNull(judgeEvaluator, "judgeEvaluator");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(question, "question");

        Instant retrieveStartedAt = Instant.now();
        RetrievalResult retrieval =
                memory.retrieve(
                                DefaultMemoryId.of(user.id(), BenchmarkAgentId),
                                question.question(),
                                RetrievalConfig.Strategy.SIMPLE)
                        .block();
        Duration retrieveTime = Duration.between(retrieveStartedAt, Instant.now());
        String retrievedMemories = retrieval == null ? "" : retrieval.formattedResult();

        String prompt =
                answerPrompt.render(
                        Map.of(
                                "question",
                                question.question(),
                                "retrieved_memories",
                                retrievedMemories,
                                "top_k",
                                3));

        Instant answerStartedAt = Instant.now();
        String generatedAnswer = answerClient.call(ChatMessages.systemUser("", prompt)).block();
        Duration answerTime = Duration.between(answerStartedAt, Instant.now());

        Judgment judgment =
                judgeEvaluator
                        .evaluate(
                                question.question(),
                                generatedAnswer,
                                question.answer(),
                                Map.of("category", categoryName(question.category())))
                        .block();

        return new QuestionResult(
                question.id(),
                question.question(),
                question.answer(),
                retrievedMemories,
                generatedAnswer,
                judgment,
                categoryName(question.category()),
                retrieveTime,
                answerTime);
    }

    public BenchmarkReport aggregate(List<QuestionResult> results) {
        Map<String, List<QuestionResult>> grouped = new LinkedHashMap<>();
        for (QuestionResult result : results) {
            grouped.computeIfAbsent(result.category(), ignored -> new java.util.ArrayList<>())
                    .add(result);
        }

        Map<String, BenchmarkReport.MetricSummary> metrics = new LinkedHashMap<>();
        long totalCorrect = 0;
        for (Map.Entry<String, List<QuestionResult>> entry : grouped.entrySet()) {
            long correctCount = entry.getValue().stream().filter(this::isCorrect).count();
            totalCorrect += correctCount;
            metrics.put(
                    entry.getKey(),
                    new BenchmarkReport.MetricSummary(
                            entry.getValue().isEmpty()
                                    ? 0.0
                                    : (double) correctCount / entry.getValue().size(),
                            entry.getValue().size()));
        }

        metrics.put(
                "overall",
                new BenchmarkReport.MetricSummary(
                        results.isEmpty() ? 0.0 : (double) totalCorrect / results.size(),
                        results.size()));

        return new BenchmarkReport(
                "locomo",
                Instant.now(),
                memoryModel,
                evalModel,
                results.size(),
                results.size(),
                metrics,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO,
                Map.of(),
                results);
    }

    public static String categoryName(int category) {
        return switch (category) {
            case 1 -> "single-hop";
            case 2 -> "multi-hop";
            case 3 -> "temporal";
            case 4 -> "open-domain";
            default -> "other";
        };
    }

    private boolean isCorrect(QuestionResult result) {
        if (result.judgment() == null) {
            return false;
        }
        return "CORRECT".equalsIgnoreCase(result.judgment().verdict())
                || result.judgment().score() >= 0.5;
    }
}
