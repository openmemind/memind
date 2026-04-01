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
package com.openmemind.ai.memory.benchmark.core;

import com.openmemind.ai.memory.benchmark.core.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.benchmark.core.dataset.Dataset;
import com.openmemind.ai.memory.benchmark.core.report.BenchmarkReport;
import com.openmemind.ai.memory.benchmark.core.report.ReportWriter;
import com.openmemind.ai.memory.benchmark.support.BenchmarkRuntime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class BenchmarkPipeline<DatasetT extends Dataset> {

    private static final String ADD_STAGE = "ADD";
    private static final String EVALUATE_STAGE = "EVALUATE";

    protected final BenchmarkRuntime runtime;
    protected final BenchmarkConfig config;
    protected final CheckpointStore checkpoint;
    protected final ReportWriter reportWriter;

    protected BenchmarkPipeline(
            BenchmarkRuntime runtime,
            BenchmarkConfig config,
            CheckpointStore checkpoint,
            ReportWriter reportWriter) {
        this.runtime = runtime;
        this.config = Objects.requireNonNull(config, "config");
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.reportWriter = Objects.requireNonNull(reportWriter, "reportWriter");
    }

    protected abstract DatasetT loadDataset();

    protected abstract List<String> addItemIds(DatasetT dataset);

    protected abstract void addItem(String itemId, DatasetT dataset);

    protected abstract List<String> evaluationItemIds(DatasetT dataset);

    protected abstract QuestionResult evaluateItem(String itemId, DatasetT dataset);

    protected abstract BenchmarkReport aggregateResults(List<QuestionResult> results);

    public final BenchmarkReport run() {
        Instant startedAt = Instant.now();
        DatasetT dataset = Objects.requireNonNull(loadDataset(), "loadDataset()");

        Instant addStartedAt = Instant.now();
        for (String itemId : addItemIds(dataset)) {
            if (shouldSkip(ADD_STAGE, itemId)) {
                continue;
            }
            addItem(itemId, dataset);
            checkpoint.markCompleted(ADD_STAGE, itemId);
            checkpoint.flush();
        }
        Duration addDuration = Duration.between(addStartedAt, Instant.now());

        Instant evaluateStartedAt = Instant.now();
        List<QuestionResult> results = new ArrayList<>();
        for (String itemId : evaluationItemIds(dataset)) {
            if (shouldSkip(EVALUATE_STAGE, itemId)) {
                continue;
            }
            QuestionResult result =
                    Objects.requireNonNull(evaluateItem(itemId, dataset), "evaluateItem()");
            results.add(result);
            checkpoint.markCompleted(EVALUATE_STAGE, itemId);
            checkpoint.flush();
        }
        Duration evaluateDuration = Duration.between(evaluateStartedAt, Instant.now());

        BenchmarkReport aggregated =
                Objects.requireNonNull(aggregateResults(results), "aggregateResults()");
        BenchmarkReport report =
                new BenchmarkReport(
                        aggregated.benchmarkName(),
                        startedAt,
                        aggregated.memoryModel(),
                        aggregated.evalModel(),
                        aggregated.sampleCount(),
                        aggregated.questionCount(),
                        aggregated.metrics(),
                        addDuration,
                        evaluateDuration,
                        Duration.between(startedAt, Instant.now()),
                        aggregated.metadata(),
                        aggregated.details());
        reportWriter.write(report, config.outputDir());
        return report;
    }

    private boolean shouldSkip(String stage, String itemId) {
        return config.resumeFromCheckpoint() && checkpoint.isCompleted(stage, itemId);
    }
}
