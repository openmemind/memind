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
package com.openmemind.ai.memory.example.java.document;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.java.support.ExampleMemoryOptions;
import com.openmemind.ai.memory.example.java.support.ExamplePrinter;
import com.openmemind.ai.memory.example.java.support.ExampleRuntimeFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document memory example for parser-backed file ingestion.
 */
public final class DocumentMemoryExample {

    private static final Logger log = LoggerFactory.getLogger(DocumentMemoryExample.class);
    private static final String SCENARIO = "document";
    private static final ExtractionConfig EXTRACTION_CONFIG =
            ExtractionConfig.defaults().withLanguage("English");
    private static final int MAX_DISPLAY_RESULTS = 3;
    private static final int MAX_DISPLAY_CHARS = 220;

    private DocumentMemoryExample() {}

    public static void main(String[] args) {
        var runtime = ExampleRuntimeFactory.create(SCENARIO, ExampleMemoryOptions.defaultOptions());
        ExamplePrinter.printRuntimeSummary(SCENARIO, runtime);
        run(runtime.memory(), runtime.dataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-document", "memind");
        var documents = defaultDocuments();

        ExamplePrinter.printSection("Step 1: Inspect Document Knowledge Pack");
        printDocumentInventory(documents);

        ExamplePrinter.printSection("Step 2: Extract Document Memory — extract(file)");
        for (var document : documents) {
            extractDocument(memory, memoryId, loader, document);
        }

        ExamplePrinter.printSection("Step 3: Retrieve Document Memory — retrieve()");
        for (var queryCase : defaultQueryCases()) {
            runQuery(memory, memoryId, queryCase);
        }
    }

    private static void extractDocument(
            Memory memory, MemoryId memoryId, ExampleDataLoader loader, ExampleDocument document) {
        var bytes = loader.loadBytes(document.relativePath());
        var fileName = Path.of(document.relativePath()).getFileName().toString();
        log.info("");
        log.info("  [{}] {}", fileName, document.title());
        log.info("    role      : {}", document.role());
        log.info("    mimeType  : {}", document.mimeType());
        log.info("    size      : {} bytes", bytes.length);
        log.info("    focus     : {}", document.focus());

        var result =
                memory.extract(
                                ExtractionRequest.file(
                                                memoryId, fileName, bytes, document.mimeType())
                                        .withConfig(EXTRACTION_CONFIG))
                        .block();
        ExamplePrinter.printExtractionResult(result);
    }

    private static void runQuery(Memory memory, MemoryId memoryId, ExampleQueryCase queryCase) {
        log.info("");
        log.info("  [{}]", queryCase.title());
        log.info("    question  : {}", queryCase.query());
        log.info("    strategy  : {}", queryCase.strategy());
        long startedAt = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, queryCase.query(), queryCase.strategy()).block();
        printRetrievalSummary(retrieval, System.currentTimeMillis() - startedAt);
    }

    static List<ExampleDocument> defaultDocuments() {
        return List.of(
                new ExampleDocument(
                        "document/release-readiness.md",
                        "Billing Release Readiness Packet",
                        "release readiness packet",
                        "Go/no-go conditions, rollback triggers, communication and release"
                                + " window."),
                new ExampleDocument(
                        "document/migration-runbook.md",
                        "Ledger Migration Runbook",
                        "migration runbook",
                        "Migration window, prechecks, execution steps, and rollback constraints."),
                new ExampleDocument(
                        "document/incident-retrospective.html",
                        "Payment API Incident Retrospective",
                        "incident retrospective",
                        "429/503 failure analysis, escalation timing, and observability lessons."),
                new ExampleDocument(
                        "document/service-ownership.csv",
                        "Service Ownership Matrix",
                        "service ownership matrix",
                        "Owner teams, on-call coverage, approval path, and service-level targets."),
                new ExampleDocument(
                        "document/weekend-handoff.txt",
                        "Weekend Release Handoff",
                        "weekend release handoff",
                        "Shift notes, active risks, watch metrics, and who to notify during"
                                + " rollout."));
    }

    static List<ExampleQueryCase> defaultQueryCases() {
        return List.of(
                new ExampleQueryCase(
                        "Migration Window And Preconditions",
                        "What is the approved migration window for the billing release, and which"
                                + " prechecks must be completed before it starts?",
                        RetrievalConfig.Strategy.SIMPLE),
                new ExampleQueryCase(
                        "429 Escalation And Observability",
                        "If payment-api starts returning sustained 429 responses during the"
                            + " release, when should the on-call escalate and which dashboards or"
                            + " signals should be checked first?",
                        RetrievalConfig.Strategy.SIMPLE),
                new ExampleQueryCase(
                        "Rollback Decision Path",
                        "Under what conditions should the team stop rollout and switch to the"
                                + " rollback path for the billing migration?",
                        RetrievalConfig.Strategy.SIMPLE),
                new ExampleQueryCase(
                        "Cross-Document Operational Summary",
                        "Summarize the weekend billing release plan across the knowledge pack:"
                            + " owners, migration risks, rollback path, and incident lessons that"
                            + " should shape monitoring and escalation.",
                        RetrievalConfig.Strategy.DEEP));
    }

    static String mimeTypeFor(String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) {
            return "text/html";
        }
        if (normalized.endsWith(".csv")) {
            return "text/csv";
        }
        if (normalized.endsWith(".txt")) {
            return "text/plain";
        }
        throw new IllegalArgumentException("Unsupported example document type: " + relativePath);
    }

    private static void printDocumentInventory(List<ExampleDocument> documents) {
        log.info("  default corpus : {} documents", documents.size());
        for (int i = 0; i < documents.size(); i++) {
            var document = documents.get(i);
            log.info(
                    "    {}. {} [{}]",
                    i + 1,
                    document.title(),
                    Path.of(document.relativePath()).getFileName());
            log.info("       role   : {}", document.role());
            log.info("       source : {}", document.relativePath());
            log.info("       focus  : {}", document.focus());
        }
    }

    private static void printRetrievalSummary(RetrievalResult result, long durationMs) {
        log.info("    duration  : {}ms", durationMs);
        log.info("    items     : {}", result.items().size());
        log.info("    rawData   : {}", result.rawData().size());
        log.info("    insights  : {}", result.insights().size());

        if (!result.rawData().isEmpty()) {
            log.info("    ── Matching Documents ──");
            for (int i = 0; i < Math.min(MAX_DISPLAY_RESULTS, result.rawData().size()); i++) {
                var rawData = result.rawData().get(i);
                log.info(
                        "      {}. [score={}] {}",
                        i + 1,
                        formatScore(rawData.maxScore()),
                        trimForDisplay(rawData.caption()));
            }
        }

        if (!result.items().isEmpty()) {
            log.info("    ── Retrieved Items ──");
            for (int i = 0; i < Math.min(MAX_DISPLAY_RESULTS, result.items().size()); i++) {
                var item = result.items().get(i);
                log.info(
                        "      {}. [score={}] {}",
                        i + 1,
                        formatScore(item.finalScore()),
                        trimForDisplay(item.text()));
            }
        }

        if (!result.insights().isEmpty()) {
            log.info("    ── Synthesized Insights ──");
            for (int i = 0; i < Math.min(MAX_DISPLAY_RESULTS, result.insights().size()); i++) {
                var insight = result.insights().get(i);
                log.info(
                        "      {}. [{}] {}",
                        i + 1,
                        insight.tier() != null ? insight.tier() : "UNKNOWN",
                        trimForDisplay(insight.text()));
            }
        }
    }

    private static String trimForDisplay(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_DISPLAY_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_DISPLAY_CHARS - 3) + "...";
    }

    private static String formatScore(double score) {
        return String.format(Locale.ROOT, "%.3f", score);
    }

    record ExampleDocument(String relativePath, String title, String role, String focus) {

        String mimeType() {
            return mimeTypeFor(relativePath);
        }
    }

    record ExampleQueryCase(String title, String query, RetrievalConfig.Strategy strategy) {}
}
