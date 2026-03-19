package com.openmemind.ai.memory.example.insight;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.example.common.ExampleDataLoader;
import com.openmemind.ai.memory.example.common.ExamplePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Insight Tree — demonstrates full insight tree construction from LEAF → BRANCH → ROOT.
 *
 * <p>Extracts 3 batches of conversations covering all USER insight types (identity, preferences,
 * relationships, experiences, behavior), then calls {@code InsightLayer.flush()} to force a
 * complete insight tree build.
 *
 * <p>Uses {@code application-insight.yml} to lower build thresholds (groupingThreshold=2,
 * buildThreshold=2) so that a small dataset can trigger a full tree build.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.openmemind.ai.memory.core",
            "com.openmemind.ai.memory.plugin",
            "com.openmemind.ai.memory.example.common"
        })
public class InsightTreeExample {

    private static final Logger log = LoggerFactory.getLogger(InsightTreeExample.class);

    public static void main(String[] args) {
        var app = new SpringApplication(InsightTreeExample.class);
        app.setAdditionalProfiles("insight");
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            run(
                    ctx.getBean(Memory.class),
                    ctx.getBean(ExampleDataLoader.class),
                    ctx.getBean(InsightLayer.class),
                    ctx.getBean(MemoryStore.class));
        }
    }

    private static void run(
            Memory memory, ExampleDataLoader loader, InsightLayer insightLayer, MemoryStore store) {
        var memoryId = DefaultMemoryId.of("user-insight", "memind");
        var config = ExtractionConfig.defaults().withLanguage("Chinese");

        // Step 1: Extract 3 batches of conversations
        var dataFiles =
                new String[] {
                    "example-data/insight/messages-1.json",
                    "example-data/insight/messages-2.json",
                    "example-data/insight/messages-3.json"
                };
        var labels =
                new String[] {"identity + preferences", "relationships + experiences", "behavior"};

        for (int i = 0; i < dataFiles.length; i++) {
            ExamplePrinter.printSection(
                    "Step " + (i + 1) + ": Extract Conversation — " + labels[i]);
            var messages = loader.loadMessages(dataFiles[i]);
            log.info("  loaded {} messages", messages.size());

            var result = memory.addMessages(memoryId, messages, config).block();
            ExamplePrinter.printExtractionResult(result);
        }

        // Step 4: Force flush to complete the insight tree build
        ExamplePrinter.printSection("Step 4: Flush — Force Full Insight Tree Build");
        log.info("  calling InsightLayer.flush()...");
        long t = System.currentTimeMillis();
        insightLayer.flush(memoryId, config.language());
        log.info("  flush completed in {}ms", System.currentTimeMillis() - t);

        // Step 5: Print the complete insight tree
        ExamplePrinter.printSection("Step 5: Insight Tree — Full Structure");
        ExamplePrinter.printInsightTree(store, memoryId);
    }
}
