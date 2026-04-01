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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.BenchmarkConfig;
import com.openmemind.ai.memory.benchmark.core.BenchmarkPipeline;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.benchmark.core.evaluator.LlmJudgeEvaluator;
import com.openmemind.ai.memory.benchmark.core.prompt.PromptTemplate;
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

public final class LocomoBenchmark extends BenchmarkPipeline<LocomoDataset> {

    private final LocomoDatasetLoader loader;
    private final LocomoMemoryWriter writer;
    private final LocomoEvaluator evaluator;

    private LocomoBenchmark(
            BenchmarkRuntime runtime,
            BenchmarkConfig config,
            CheckpointStore checkpoint,
            ReportWriter reportWriter,
            LocomoDatasetLoader loader,
            LocomoMemoryWriter writer,
            LocomoEvaluator evaluator) {
        super(runtime, config, checkpoint, reportWriter);
        this.loader = Objects.requireNonNull(loader, "loader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public static void main(String[] args) {
        BenchmarkSettings settings = BenchmarkSettings.load();
        Path runDir = createRunDirectory(settings.benchmark().outputDir(), "locomo");
        BenchmarkConfig config =
                new BenchmarkConfig(
                        settings.benchmark().dataDir().resolve("locomo10.json"),
                        Integer.MAX_VALUE,
                        3,
                        true,
                        1,
                        1,
                        runDir,
                        true);

        try (BenchmarkRuntime runtime =
                BenchmarkRuntimeFactory.create(
                        "locomo",
                        runDir.resolve("runtime"),
                        MemoryBuildOptions.defaults(),
                        settings)) {
            StructuredChatClient answerClient =
                    createChatClient(
                            settings.openAi().baseUrl(),
                            settings.openAi().apiKey(),
                            settings.openAi().chatModel());
            StructuredChatClient evalClient =
                    createChatClient(
                            settings.openAi().baseUrl(),
                            settings.openAi().apiKey(),
                            settings.benchmark().evalModel());

            LocomoBenchmark benchmark =
                    new LocomoBenchmark(
                            runtime,
                            config,
                            new CheckpointStore(
                                    new ObjectMapper(), runDir.resolve("checkpoint.json")),
                            new ReportWriter(new ObjectMapper()),
                            new LocomoDatasetLoader(),
                            new LocomoMemoryWriter(),
                            new LocomoEvaluator(
                                    runtime.memory(),
                                    answerClient,
                                    new LlmJudgeEvaluator(
                                            evalClient,
                                            PromptTemplate.fromClasspath(
                                                    "prompts/locomo/judge.txt")),
                                    settings.openAi().chatModel(),
                                    settings.benchmark().evalModel()));
            benchmark.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run LoCoMo benchmark", exception);
        }
    }

    @Override
    protected LocomoDataset loadDataset() {
        return loader.load(config.dataPath());
    }

    @Override
    protected List<String> addItemIds(LocomoDataset dataset) {
        return selectedUsers(dataset).stream().map(LocomoDataset.LocomoUser::id).toList();
    }

    @Override
    protected void addItem(String itemId, LocomoDataset dataset) {
        LocomoDataset.LocomoUser user =
                dataset.users().stream()
                        .filter(candidate -> candidate.id().equals(itemId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown LoCoMo user: " + itemId));
        writer.write(runtime.memory(), user, config.dualPerspective());
    }

    @Override
    protected List<String> evaluationItemIds(LocomoDataset dataset) {
        return selectedUsers(dataset).stream()
                .flatMap(user -> user.questions().stream())
                .filter(question -> question.category() != 5)
                .map(LocomoDataset.LocomoQuestion::id)
                .toList();
    }

    @Override
    protected QuestionResult evaluateItem(String itemId, LocomoDataset dataset) {
        for (LocomoDataset.LocomoUser user : selectedUsers(dataset)) {
            for (LocomoDataset.LocomoQuestion question : user.questions()) {
                if (question.id().equals(itemId)) {
                    return evaluator.evaluate(user, question);
                }
            }
        }
        throw new IllegalArgumentException("Unknown LoCoMo question: " + itemId);
    }

    @Override
    protected BenchmarkReport aggregateResults(List<QuestionResult> results) {
        return evaluator.aggregate(results);
    }

    private List<LocomoDataset.LocomoUser> selectedUsers(LocomoDataset dataset) {
        return dataset.users().stream().limit(config.maxSamples()).toList();
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
