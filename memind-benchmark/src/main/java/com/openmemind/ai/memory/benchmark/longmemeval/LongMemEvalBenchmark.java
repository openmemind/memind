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
import com.openmemind.ai.memory.benchmark.core.BenchmarkConfig;
import com.openmemind.ai.memory.benchmark.core.BenchmarkPipeline;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.benchmark.core.report.BenchmarkReport;
import com.openmemind.ai.memory.benchmark.core.report.ReportWriter;
import com.openmemind.ai.memory.benchmark.support.BenchmarkRuntime;
import com.openmemind.ai.memory.benchmark.support.BenchmarkRuntimeFactory;
import com.openmemind.ai.memory.benchmark.support.BenchmarkSettings;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public final class LongMemEvalBenchmark extends BenchmarkPipeline<LongMemEvalDataset> {

    private final LongMemEvalDatasetLoader loader;
    private final LongMemEvalMemoryWriter writer;
    private final LongMemEvalEvaluator evaluator;

    private LongMemEvalBenchmark(
            BenchmarkRuntime runtime,
            BenchmarkConfig config,
            CheckpointStore checkpoint,
            ReportWriter reportWriter,
            LongMemEvalDatasetLoader loader,
            LongMemEvalMemoryWriter writer,
            LongMemEvalEvaluator evaluator) {
        super(runtime, config, checkpoint, reportWriter);
        this.loader = Objects.requireNonNull(loader, "loader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public static void main(String[] args) {
        BenchmarkSettings settings = BenchmarkSettings.load();
        Path runDir = createRunDirectory(settings.benchmark().outputDir(), "longmemeval");
        BenchmarkConfig config =
                new BenchmarkConfig(
                        settings.benchmark()
                                .dataDir()
                                .resolve("longmemeval")
                                .resolve("questions.json"),
                        Integer.MAX_VALUE,
                        3,
                        false,
                        1,
                        1,
                        runDir,
                        true);

        try (BenchmarkRuntime runtime =
                BenchmarkRuntimeFactory.create(
                        "longmemeval",
                        runDir.resolve("runtime"),
                        MemoryBuildOptions.defaults(),
                        settings)) {
            StructuredChatClient answerClient =
                    createChatClient(
                            settings.openAi().baseUrl(),
                            settings.openAi().apiKey(),
                            settings.openAi().chatModel());
            StructuredChatClient judgeClient =
                    createChatClient(
                            settings.openAi().baseUrl(),
                            settings.openAi().apiKey(),
                            settings.benchmark().evalModel());

            LongMemEvalBenchmark benchmark =
                    new LongMemEvalBenchmark(
                            runtime,
                            config,
                            new CheckpointStore(
                                    new ObjectMapper(), runDir.resolve("checkpoint.json")),
                            new ReportWriter(new ObjectMapper()),
                            new LongMemEvalDatasetLoader(),
                            new LongMemEvalMemoryWriter(),
                            new LongMemEvalEvaluator(
                                    runtime.memory(),
                                    answerClient,
                                    judgeClient,
                                    new LongMemEvalJudgeRules(),
                                    settings.openAi().chatModel(),
                                    settings.benchmark().evalModel(),
                                    com.openmemind.ai.memory.benchmark.core.prompt.PromptTemplate
                                            .fromClasspath("prompts/answer-with-memory.txt"),
                                    new ObjectMapper()));
            benchmark.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run LongMemEval benchmark", exception);
        }
    }

    @Override
    protected LongMemEvalDataset loadDataset() {
        return loader.load(config.dataPath());
    }

    @Override
    protected List<String> addItemIds(LongMemEvalDataset dataset) {
        return selectedQuestions(dataset).stream()
                .map(LongMemEvalDataset.LongMemEvalQuestion::questionId)
                .toList();
    }

    @Override
    protected void addItem(String itemId, LongMemEvalDataset dataset) {
        writer.writeQuestionHaystack(runtime.memory(), findQuestion(itemId, dataset));
    }

    @Override
    protected List<String> evaluationItemIds(LongMemEvalDataset dataset) {
        return addItemIds(dataset);
    }

    @Override
    protected QuestionResult evaluateItem(String itemId, LongMemEvalDataset dataset) {
        return evaluator.evaluate(findQuestion(itemId, dataset));
    }

    @Override
    protected BenchmarkReport aggregateResults(List<QuestionResult> results) {
        return evaluator.aggregate(results);
    }

    private List<LongMemEvalDataset.LongMemEvalQuestion> selectedQuestions(
            LongMemEvalDataset dataset) {
        return dataset.questions().stream().limit(config.maxSamples()).toList();
    }

    private LongMemEvalDataset.LongMemEvalQuestion findQuestion(
            String questionId, LongMemEvalDataset dataset) {
        return dataset.questions().stream()
                .filter(candidate -> candidate.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown LongMemEval question: " + questionId));
    }

    private static Path createRunDirectory(Path baseDir, String benchmarkName) {
        String timestamp =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
        return baseDir.resolve(benchmarkName).resolve(timestamp);
    }

    private static StructuredChatClient createChatClient(
            String baseUrl, String apiKey, String modelName) {
        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build())
                        .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build();
        return new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());
    }
}
