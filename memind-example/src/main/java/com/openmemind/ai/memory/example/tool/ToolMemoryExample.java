package com.openmemind.ai.memory.example.tool;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.example.common.ExampleDataLoader;
import com.openmemind.ai.memory.example.common.ExamplePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Tool Memory — demonstrates the full memory workflow for tool call data.
 *
 * <p>Covers: tool call reporting, tool statistics querying, PROCEDURAL memory extraction,
 * and agent memory retrieval.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.openmemind.ai.memory.core",
            "com.openmemind.ai.memory.plugin",
            "com.openmemind.ai.memory.example.common"
        })
public class ToolMemoryExample {

    private static final Logger log = LoggerFactory.getLogger(ToolMemoryExample.class);

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx =
                SpringApplication.run(ToolMemoryExample.class, args)) {
            run(ctx.getBean(Memory.class), ctx.getBean(ExampleDataLoader.class));
        }
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-tool", "memind");

        // Step 1: Report tool calls
        ExamplePrinter.printSection("Step 1: Report Tool Calls — reportToolCalls()");
        var toolRecords = loader.loadToolCalls("example-data/tool/tool-calls.json");
        log.info("  loaded {} tool call records", toolRecords.size());

        var toolResult = memory.reportToolCalls(memoryId, toolRecords).block();
        ExamplePrinter.printExtractionResult(toolResult);

        // Step 2: Query tool statistics
        ExamplePrinter.printSection("Step 2: Tool Statistics — getToolStats() / getAllToolStats()");

        log.info("  ── grep_code stats ──");
        var grepStats = memory.getToolStats(memoryId, "grep_code").block();
        ExamplePrinter.printToolStats("grep_code", grepStats);

        log.info("  ── all tool stats ──");
        var allStats = memory.getAllToolStats(memoryId).block();
        ExamplePrinter.printAllToolStats(allStats);

        // Step 3: Extract PROCEDURAL memory
        ExamplePrinter.printSection("Step 3: Extract PROCEDURAL Memory — addMessages(agentOnly)");
        var messages = loader.loadMessages("example-data/tool/messages.json");
        log.info("  loaded {} messages", messages.size());

        var convResult =
                memory.addMessages(
                                memoryId,
                                messages,
                                ExtractionConfig.agentOnly()
                                        .withEnableInsight(false)
                                        .withLanguage("Chinese"))
                        .block();
        ExamplePrinter.printExtractionResult(convResult);

        // Step 4: Retrieve agent memory
        ExamplePrinter.printSection("Step 4: Retrieve Agent Memory — retrieve(agentMemory)");
        var query = "部署流程是怎样的？";
        log.info("  query: {}", query);

        long t = System.currentTimeMillis();
        var retrieval =
                memory.retrieve(
                                RetrievalRequest.agentMemory(
                                        memoryId, query, RetrievalConfig.Strategy.SIMPLE))
                        .block();
        long durationMs = System.currentTimeMillis() - t;
        ExamplePrinter.printRetrievalResult(retrieval, durationMs);
    }
}
