package com.openmemind.ai.memory.example.quickstart;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.example.common.ExampleDataLoader;
import com.openmemind.ai.memory.example.common.ExamplePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Quick Start — minimal example showing the two core operations: extract memory + retrieve memory.
 *
 * <p>Run this example to quickly understand what memind stores and what it returns.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.openmemind.ai.memory.core",
            "com.openmemind.ai.memory.plugin",
            "com.openmemind.ai.memory.example.common"
        })
public class QuickStartExample {

    private static final Logger log = LoggerFactory.getLogger(QuickStartExample.class);

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx =
                SpringApplication.run(QuickStartExample.class, args)) {
            run(ctx.getBean(Memory.class), ctx.getBean(ExampleDataLoader.class));
        }
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-quickstart", "memind");
        var messages = loader.loadMessages("example-data/quickstart/messages.json");

        // Step 1: Extract memory
        ExamplePrinter.printSection("Step 1: Extract Memory — addMessages()");
        log.info("  loaded {} messages", messages.size());

        var result =
                memory.addMessages(
                                memoryId,
                                messages,
                                ExtractionConfig.defaults().withLanguage("Chinese"))
                        .block();

        ExamplePrinter.printExtractionResult(result);

        // Step 2: Retrieve memory
        ExamplePrinter.printSection("Step 2: Retrieve Memory — retrieve()");
        var query = "这个用户的技术背景是什么？";
        log.info("  query: {}", query);

        long t = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.SIMPLE).block();
        long durationMs = System.currentTimeMillis() - t;

        ExamplePrinter.printRetrievalResult(retrieval, durationMs);
    }
}
