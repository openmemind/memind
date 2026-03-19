package com.openmemind.ai.memory.evaluation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.EvaluationResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.QuestionJudgment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Write evaluation results to report.txt (human-readable) and eval_results.json (machine-readable).
 *
 * <p>The output format aligns with the EverMemOS evaluation framework.
 *
 */
@Component
public class ReportWriter {
    private static final Logger log = LoggerFactory.getLogger(ReportWriter.class);
    private static final String SEPARATOR =
            "============================================================";
    private final ObjectMapper mapper;

    public ReportWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(
            EvaluationResult result,
            Path runDir,
            String dataset,
            String adapter,
            String runName,
            long elapsedMs,
            PipelineConfig config) {
        try {
            Files.createDirectories(runDir);
            writeReport(result, runDir, adapter, elapsedMs);
            writeJson(result, runDir, config);
            log.info("Report written to {}", runDir);
        } catch (IOException e) {
            log.error("Failed to write report", e);
        }
    }

    private void writeReport(EvaluationResult result, Path runDir, String adapter, long elapsedMs)
            throws IOException {

        String report =
                String.join(
                        "\n",
                        SEPARATOR,
                        "📊 Evaluation Report",
                        SEPARATOR,
                        "",
                        "System: " + adapter,
                        "Time Elapsed: " + String.format("%.2fs", elapsedMs / 1000.0),
                        "",
                        "Total Questions: " + result.totalQuestions(),
                        "Correct: " + result.correct(),
                        "Accuracy: " + String.format("%.2f%%", result.accuracy() * 100),
                        "",
                        SEPARATOR,
                        "");
        Files.writeString(runDir.resolve("report.txt"), report);
    }

    private void writeJson(EvaluationResult result, Path runDir, PipelineConfig config)
            throws IOException {
        Map<String, Object> output = toEverMemOsEvalResult(result, config);
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(runDir.resolve("eval_results.json").toFile(), output);
    }

    /**
     * Convert EvaluationResult to EverMemOS compatible Map structure:
     * - detailed_results: grouped by conversation_id
     * - metadata: includes model/num_runs/mean_accuracy/std_accuracy/run_scores/category_accuracies
     */
    private Map<String, Object> toEverMemOsEvalResult(
            EvaluationResult result, PipelineConfig config) {
        // detailed_results grouped by conversation_id
        Map<String, List<Map<String, Object>>> detailedResults = new LinkedHashMap<>();
        for (QuestionJudgment j : result.details()) {
            String convId = extractConvId(j.questionId());
            detailedResults.computeIfAbsent(convId, k -> new ArrayList<>()).add(toDetailMap(j));
        }

        // category_accuracies
        Map<String, Map<String, Object>> categoryAccuracies = new LinkedHashMap<>();
        result.categoryStats()
                .forEach(
                        (cat, cs) -> {
                            Map<String, Object> catMap = new LinkedHashMap<>();
                            catMap.put("mean", cs.mean());
                            catMap.put("std", cs.std());
                            catMap.put("individual_runs", cs.individualRuns());
                            catMap.put("total", cs.total());
                            categoryAccuracies.put(cat, catMap);
                        });

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("model", config != null ? config.model() : "unknown");
        metadata.put("num_runs", config != null ? config.numRuns() : 1);
        metadata.put("mean_accuracy", result.meanAccuracy());
        metadata.put("std_accuracy", result.stdAccuracy());
        metadata.put("run_scores", result.runScores());
        metadata.put("category_accuracies", categoryAccuracies);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("total_questions", result.totalQuestions());
        output.put("correct", result.correct());
        output.put("accuracy", result.accuracy());
        output.put("detailed_results", detailedResults);
        output.put("metadata", metadata);
        return output;
    }

    /** Details of each question, matching EverMemOS LLMJudge output format */
    private Map<String, Object> toDetailMap(QuestionJudgment j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question_id", j.questionId());
        m.put("question", j.question());
        m.put("golden_answer", j.goldenAnswer());
        m.put("generated_answer", j.generatedAnswer());
        if (j.llmJudgments() != null) {
            m.put("llm_judgments", j.llmJudgments());
        }
        m.put("category", j.category());
        return m;
    }

    /** Infer conversation_id from question_id (remove the last segment _qaN) */
    private String extractConvId(String questionId) {
        if (questionId == null) return "unknown";
        int lastUnderscore = questionId.lastIndexOf('_');
        if (lastUnderscore > 0 && questionId.substring(lastUnderscore + 1).startsWith("qa")) {
            return questionId.substring(0, lastUnderscore);
        }
        return questionId;
    }

    /** Write search_results.json, directly serialize RetrievalResult */
    public void writeSearchResults(List<SearchResult> results, Path runDir) throws IOException {
        List<Map<String, Object>> output = new ArrayList<>();
        for (SearchResult sr : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("query", sr.query());
            m.put("conversation_id", sr.conversationId());
            m.put("retrieval_result", sr.retrievalResult());
            output.add(m);
        }
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(runDir.resolve("search_results.json").toFile(), output);
    }

    /** Write answer_results.json, align with EverMemOS format: snake_case, answer field, includes metadata */
    public void writeAnswerResults(List<AnswerResult> results, Path runDir) throws IOException {
        List<Map<String, Object>> output = new ArrayList<>();
        for (AnswerResult ar : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question_id", ar.questionId());
            m.put("question", ar.question());
            m.put("answer", ar.generatedAnswer());
            m.put("golden_answer", ar.goldenAnswer());
            m.put("category", ar.category());
            m.put("conversation_id", ar.conversationId());
            m.put("formatted_context", ar.formattedContext());
            m.put("metadata", Map.of("conversation_id", ar.conversationId()));
            output.add(m);
        }
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(runDir.resolve("answer_results.json").toFile(), output);
    }
}
