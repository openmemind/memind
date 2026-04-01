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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportWriter {

    private final ObjectMapper objectMapper;

    public ReportWriter(ObjectMapper objectMapper) {
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper")
                        .copy()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void write(BenchmarkReport report, Path outputDir) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(outputDir, "outputDir");

        try {
            Files.createDirectories(outputDir.resolve("details"));
            Files.writeString(outputDir.resolve("report.md"), toMarkdown(report));
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(outputDir.resolve("report.json").toFile(), report);
            writeDetails(report.details(), outputDir.resolve("details").resolve("results.jsonl"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write report into " + outputDir, exception);
        }
    }

    private String toMarkdown(BenchmarkReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(report.benchmarkName()).append(" Benchmark Report\n\n");
        builder.append("- Date: ").append(report.generatedAt()).append('\n');
        builder.append("- Memory Model: ").append(report.memoryModel()).append('\n');
        builder.append("- Eval Model: ").append(report.evalModel()).append('\n');
        builder.append("- Samples: ").append(report.sampleCount()).append('\n');
        builder.append("- Questions: ").append(report.questionCount()).append("\n\n");
        builder.append("## Results\n\n");
        builder.append("| Category | Score | Count |\n");
        builder.append("|----------|-------|-------|\n");
        report.metrics()
                .forEach(
                        (category, summary) ->
                                builder.append("| ")
                                        .append(category)
                                        .append(" | ")
                                        .append(String.format("%.2f%%", summary.score() * 100.0))
                                        .append(" | ")
                                        .append(summary.count())
                                        .append(" |\n"));
        builder.append("\n## Timing\n\n");
        builder.append("- Memory add: ").append(report.addDuration()).append('\n');
        builder.append("- Evaluation: ").append(report.evaluateDuration()).append('\n');
        builder.append("- Total: ").append(report.totalDuration()).append('\n');
        return builder.toString();
    }

    private void writeDetails(List<QuestionResult> details, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (QuestionResult detail : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", detail.questionId());
            row.put("question", detail.question());
            row.put("goldenAnswer", detail.goldenAnswer());
            row.put("retrievedMemories", detail.retrievedMemories());
            row.put("generatedAnswer", detail.generatedAnswer());
            row.put("judgment", detail.judgment());
            row.put("category", detail.category());
            row.put("retrieveTimeMs", detail.retrieveTime().toMillis());
            row.put("answerTimeMs", detail.answerTime().toMillis());
            lines.add(objectMapper.writeValueAsString(row));
        }
        Files.write(path, lines);
    }
}
