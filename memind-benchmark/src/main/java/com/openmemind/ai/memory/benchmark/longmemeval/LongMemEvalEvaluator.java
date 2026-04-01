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
package com.openmemind.ai.memory.benchmark.longmemeval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
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

public final class LongMemEvalEvaluator {

    private static final String BenchmarkAgentId = "memind-benchmark";

    private final Memory memory;
    private final StructuredChatClient answerClient;
    private final StructuredChatClient judgeClient;
    private final LongMemEvalJudgeRules judgeRules;
    private final String memoryModel;
    private final String evalModel;
    private final PromptTemplate answerPrompt;
    private final ObjectMapper objectMapper;

    public LongMemEvalEvaluator(
            Memory memory, StructuredChatClient answerClient, StructuredChatClient judgeClient) {
        this(
                memory,
                answerClient,
                judgeClient,
                new LongMemEvalJudgeRules(),
                "gpt-4o-mini",
                "gpt-4o",
                PromptTemplate.fromClasspath("prompts/answer-with-memory.txt"),
                new ObjectMapper());
    }

    LongMemEvalEvaluator(
            Memory memory,
            StructuredChatClient answerClient,
            StructuredChatClient judgeClient,
            LongMemEvalJudgeRules judgeRules,
            String memoryModel,
            String evalModel,
            PromptTemplate answerPrompt,
            ObjectMapper objectMapper) {
        this.memory = memory;
        this.answerClient = answerClient;
        this.judgeClient = judgeClient;
        this.judgeRules = Objects.requireNonNull(judgeRules, "judgeRules");
        this.memoryModel = Objects.requireNonNull(memoryModel, "memoryModel");
        this.evalModel = Objects.requireNonNull(evalModel, "evalModel");
        this.answerPrompt = Objects.requireNonNull(answerPrompt, "answerPrompt");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public QuestionResult evaluate(LongMemEvalDataset.LongMemEvalQuestion question) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(answerClient, "answerClient");
        Objects.requireNonNull(question, "question");

        Instant retrieveStartedAt = Instant.now();
        RetrievalResult retrieval =
                memory.retrieve(
                                DefaultMemoryId.of(question.questionId(), BenchmarkAgentId),
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

        Judgment judgment = judge(question, generatedAnswer);
        return new QuestionResult(
                question.questionId(),
                question.question(),
                question.answer(),
                retrievedMemories,
                generatedAnswer,
                judgment,
                question.questionType(),
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
                "longmemeval",
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

    private Judgment judge(
            LongMemEvalDataset.LongMemEvalQuestion question, String generatedAnswer) {
        if (judgeRules.accepts(question.questionType(), generatedAnswer, question.answer())) {
            return new Judgment("CORRECT", 1.0, "Accepted by LongMemEval rule");
        }
        if (judgeClient == null) {
            return new Judgment("WRONG", 0.0, "Rejected by LongMemEval rule");
        }

        PromptTemplate judgePrompt =
                PromptTemplate.fromClasspath(judgeRules.promptPath(question.questionType()));
        String prompt =
                judgePrompt.render(
                        Map.of(
                                "question",
                                question.question(),
                                "generated",
                                String.valueOf(generatedAnswer),
                                "golden",
                                question.answer(),
                                "question_type",
                                question.questionType(),
                                "question_date",
                                question.questionDate()));
        String rawResponse = judgeClient.call(ChatMessages.systemUser("", prompt)).block();
        return parseJudgment(rawResponse);
    }

    private Judgment parseJudgment(String rawResponse) {
        try {
            return objectMapper.readValue(rawResponse, Judgment.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to parse LongMemEval judge response: " + rawResponse, exception);
        }
    }

    private boolean isCorrect(QuestionResult result) {
        if (result.judgment() == null) {
            return false;
        }
        return "CORRECT".equalsIgnoreCase(result.judgment().verdict())
                || result.judgment().score() >= 1.0;
    }
}
