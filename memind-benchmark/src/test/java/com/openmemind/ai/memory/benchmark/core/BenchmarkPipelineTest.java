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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.benchmark.core.dataset.Dataset;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import com.openmemind.ai.memory.benchmark.core.report.BenchmarkReport;
import com.openmemind.ai.memory.benchmark.core.report.ReportWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkPipelineTest {

    @TempDir Path tempDir;

    @Test
    void runSkipsCompletedAddItemsAndCompletedQuestions() throws Exception {
        CheckpointStore checkpointStore =
                new CheckpointStore(new ObjectMapper(), tempDir.resolve("checkpoint.json"));
        checkpointStore.markCompleted("ADD", "conv-1");
        checkpointStore.markCompleted("EVALUATE", "q-1");
        checkpointStore.flush();

        FakePipeline pipeline =
                new FakePipeline(
                        checkpointStore, new ReportWriter(new ObjectMapper()), tempDir, true);

        BenchmarkReport report = pipeline.run();

        assertThat(pipeline.addedConversations).containsExactly("conv-2");
        assertThat(pipeline.evaluatedQuestions).containsExactly("q-2");
        assertThat(report.questionCount()).isEqualTo(1);
        assertThat(Files.exists(tempDir.resolve("report.md"))).isTrue();
    }

    @Test
    void runProcessesAllItemsWhenResumeIsDisabled() {
        CheckpointStore checkpointStore =
                new CheckpointStore(new ObjectMapper(), tempDir.resolve("checkpoint.json"));
        checkpointStore.markCompleted("ADD", "conv-1");
        checkpointStore.markCompleted("EVALUATE", "q-1");
        checkpointStore.flush();

        FakePipeline pipeline =
                new FakePipeline(
                        checkpointStore, new ReportWriter(new ObjectMapper()), tempDir, false);

        BenchmarkReport report = pipeline.run();

        assertThat(pipeline.addedConversations).containsExactly("conv-1", "conv-2");
        assertThat(pipeline.evaluatedQuestions).containsExactly("q-1", "q-2");
        assertThat(report.questionCount()).isEqualTo(2);
    }

    private static final class FakePipeline extends BenchmarkPipeline<FakeDataset> {

        private final List<String> addedConversations = new ArrayList<>();
        private final List<String> evaluatedQuestions = new ArrayList<>();

        private FakePipeline(
                CheckpointStore checkpointStore,
                ReportWriter reportWriter,
                Path outputDir,
                boolean resumeFromCheckpoint) {
            super(
                    null,
                    new BenchmarkConfig(
                            outputDir, 10, 3, false, 1, 1, outputDir, resumeFromCheckpoint),
                    checkpointStore,
                    reportWriter);
        }

        @Override
        protected FakeDataset loadDataset() {
            return new FakeDataset();
        }

        @Override
        protected List<String> addItemIds(FakeDataset dataset) {
            return List.of("conv-1", "conv-2");
        }

        @Override
        protected void addItem(String itemId, FakeDataset dataset) {
            addedConversations.add(itemId);
        }

        @Override
        protected List<String> evaluationItemIds(FakeDataset dataset) {
            return List.of("q-1", "q-2");
        }

        @Override
        protected QuestionResult evaluateItem(String itemId, FakeDataset dataset) {
            evaluatedQuestions.add(itemId);
            return new QuestionResult(
                    itemId,
                    "question-" + itemId,
                    "golden",
                    "retrieved",
                    "generated",
                    new Judgment("CORRECT", 1.0, "ok"),
                    "overall",
                    Duration.ofMillis(10),
                    Duration.ofMillis(20));
        }

        @Override
        protected BenchmarkReport aggregateResults(List<QuestionResult> results) {
            return new BenchmarkReport(
                    "fake",
                    Instant.parse("2026-04-01T00:00:00Z"),
                    "memory-model",
                    "eval-model",
                    2,
                    results.size(),
                    Map.of("overall", new BenchmarkReport.MetricSummary(1.0, results.size())),
                    Duration.ZERO,
                    Duration.ZERO,
                    Duration.ZERO,
                    Map.of(),
                    results);
        }
    }

    private record FakeDataset() implements Dataset {

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public int conversationCount() {
            return 2;
        }

        @Override
        public int questionCount() {
            return 2;
        }
    }
}
