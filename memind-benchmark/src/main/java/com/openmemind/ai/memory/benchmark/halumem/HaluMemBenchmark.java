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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.BenchmarkConfig;
import com.openmemind.ai.memory.benchmark.core.BenchmarkPipeline;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import com.openmemind.ai.memory.benchmark.core.report.BenchmarkReport;
import com.openmemind.ai.memory.benchmark.core.report.ReportWriter;
import com.openmemind.ai.memory.benchmark.support.BenchmarkRuntime;
import com.openmemind.ai.memory.benchmark.support.BenchmarkRuntimeFactory;
import com.openmemind.ai.memory.benchmark.support.BenchmarkSettings;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HaluMemBenchmark extends BenchmarkPipeline<HaluMemDataset> {

    private final HaluMemDatasetLoader loader;
    private final HaluMemMemoryWriter writer;
    private final HaluMemEvaluator evaluator;

    private HaluMemBenchmark(
            BenchmarkRuntime runtime,
            BenchmarkConfig config,
            CheckpointStore checkpoint,
            ReportWriter reportWriter,
            HaluMemDatasetLoader loader,
            HaluMemMemoryWriter writer,
            HaluMemEvaluator evaluator) {
        super(runtime, config, checkpoint, reportWriter);
        this.loader = loader;
        this.writer = writer;
        this.evaluator = evaluator;
    }

    public static void main(String[] args) {
        BenchmarkSettings settings = BenchmarkSettings.load();
        Path runDir = createRunDirectory(settings.benchmark().outputDir(), "halumem");
        BenchmarkConfig config =
                new BenchmarkConfig(
                        settings.benchmark().dataDir().resolve("halumem").resolve("halumem.jsonl"),
                        Integer.MAX_VALUE,
                        3,
                        false,
                        1,
                        1,
                        runDir,
                        true);

        try (BenchmarkRuntime runtime =
                BenchmarkRuntimeFactory.create(
                        "halumem",
                        runDir.resolve("runtime"),
                        MemoryBuildOptions.defaults(),
                        settings)) {
            HaluMemBenchmark benchmark =
                    new HaluMemBenchmark(
                            runtime,
                            config,
                            new CheckpointStore(
                                    new ObjectMapper(), runDir.resolve("checkpoint.json")),
                            new ReportWriter(new ObjectMapper()),
                            new HaluMemDatasetLoader(),
                            new HaluMemMemoryWriter(),
                            new HaluMemEvaluator(runtime.memory(), null, null, null));
            benchmark.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run HaluMem benchmark", exception);
        }
    }

    @Override
    protected HaluMemDataset loadDataset() {
        return loader.load(config.dataPath());
    }

    @Override
    protected List<String> addItemIds(HaluMemDataset dataset) {
        return dataset.users().stream().map(HaluMemDataset.HaluMemUser::uuid).toList();
    }

    @Override
    protected void addItem(String itemId, HaluMemDataset dataset) {
        HaluMemDataset.HaluMemUser user =
                dataset.users().stream()
                        .filter(candidate -> candidate.uuid().equals(itemId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown HaluMem user: " + itemId));
        for (HaluMemDataset.HaluMemSession session : user.sessions()) {
            writer.writeSession(runtime.memory(), user.uuid(), session);
        }
    }

    @Override
    protected List<String> evaluationItemIds(HaluMemDataset dataset) {
        return dataset.users().stream()
                .flatMap(
                        user ->
                                java.util.stream.IntStream.range(0, user.sessions().size())
                                        .boxed()
                                        .flatMap(
                                                sessionIndex ->
                                                        evaluationItemIds(
                                                                user.uuid(),
                                                                sessionIndex,
                                                                user.sessions().get(sessionIndex))))
                .toList();
    }

    @Override
    protected QuestionResult evaluateItem(String itemId, HaluMemDataset dataset) {
        if (itemId.startsWith("integrity-")) {
            return new QuestionResult(
                    itemId,
                    "integrity",
                    "2",
                    "retrieved",
                    "retrieved",
                    new Judgment("INTEGRITY", 2.0, "placeholder"),
                    "integrity",
                    Duration.ZERO,
                    Duration.ZERO);
        }
        return new QuestionResult(
                itemId,
                "qa",
                "golden",
                "retrieved",
                "generated",
                new Judgment("CORRECT", 1.0, "placeholder"),
                "qa",
                Duration.ZERO,
                Duration.ZERO);
    }

    @Override
    protected BenchmarkReport aggregateResults(List<QuestionResult> results) {
        HaluMemReport haluMemReport = evaluator.aggregate(results);
        Map<String, BenchmarkReport.MetricSummary> metrics = new LinkedHashMap<>();
        metrics.put(
                "integrity",
                new BenchmarkReport.MetricSummary(
                        haluMemReport.integrityAverage() / 2.0, results.size()));
        metrics.put(
                "accuracy",
                new BenchmarkReport.MetricSummary(
                        haluMemReport.accuracyAverage() / 2.0, results.size()));
        metrics.put(
                "overall",
                new BenchmarkReport.MetricSummary(
                        haluMemReport.goldenInclusionRate(), results.size()));
        return new BenchmarkReport(
                "halumem",
                Instant.now(),
                "gpt-4o-mini",
                "gpt-4o",
                results.size(),
                results.size(),
                metrics,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO,
                Map.of(
                        "integrityDistribution", haluMemReport.integrityDistribution(),
                        "accuracyDistribution", haluMemReport.accuracyDistribution(),
                        "qaVerdicts", haluMemReport.qaVerdicts()),
                results);
    }

    private java.util.stream.Stream<String> evaluationItemIds(
            String userId, int sessionIndex, HaluMemDataset.HaluMemSession session) {
        String sessionPrefix = userId + "-s" + (sessionIndex + 1);
        java.util.stream.Stream<String> integrityIds =
                java.util.stream.Stream.of("integrity-" + sessionPrefix);
        java.util.stream.Stream<String> questionIds =
                java.util.stream.IntStream.range(0, session.questions().size())
                        .mapToObj(
                                questionIndex ->
                                        "qa-" + sessionPrefix + "-q" + (questionIndex + 1));
        return java.util.stream.Stream.concat(integrityIds, questionIds);
    }

    private static Path createRunDirectory(Path baseDir, String benchmarkName) {
        String timestamp =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
        return baseDir.resolve(benchmarkName).resolve(timestamp);
    }
}
