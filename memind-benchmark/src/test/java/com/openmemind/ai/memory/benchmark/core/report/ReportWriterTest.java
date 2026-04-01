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
package com.openmemind.ai.memory.benchmark.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {

    @TempDir Path tempDir;

    @Test
    void writeCreatesMarkdownJsonAndDetailsFiles() throws Exception {
        ReportWriter writer = new ReportWriter(new ObjectMapper());
        BenchmarkReport report =
                new BenchmarkReport(
                        "locomo",
                        Instant.parse("2026-04-01T00:00:00Z"),
                        "gpt-4o-mini",
                        "gpt-4o",
                        1,
                        1,
                        Map.of("overall", new BenchmarkReport.MetricSummary(1.0, 1)),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(7),
                        Duration.ofSeconds(12),
                        Map.of(),
                        List.of(
                                new QuestionResult(
                                        "q1",
                                        "Where did we meet?",
                                        "In Hangzhou.",
                                        "Hangzhou memory",
                                        "In Hangzhou.",
                                        new Judgment("CORRECT", 1.0, "matched"),
                                        "single-hop",
                                        Duration.ofMillis(300),
                                        Duration.ofMillis(900))));

        writer.write(report, tempDir);

        assertThat(Files.exists(tempDir.resolve("report.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("report.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("details").resolve("results.jsonl"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("report.md")))
                .contains("# locomo Benchmark Report");
    }
}
