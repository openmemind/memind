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
package com.openmemind.ai.memory.example.foresight;

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
 * Foresight — demonstrates the quality of predictive memory generation.
 *
 * <p>When {@code enableForesight} is enabled, memind extracts FORESIGHT-type MemoryItems
 * from conversations, containing predictions, evidence sources, and time boundaries.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.openmemind.ai.memory.core",
            "com.openmemind.ai.memory.plugin",
            "com.openmemind.ai.memory.example.common"
        })
public class ForesightExample {

    private static final Logger log = LoggerFactory.getLogger(ForesightExample.class);

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx =
                SpringApplication.run(ForesightExample.class, args)) {
            run(ctx.getBean(Memory.class), ctx.getBean(ExampleDataLoader.class));
        }
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-foresight", "memind");

        // Step 1: Extract memory with Foresight enabled
        ExamplePrinter.printSection("Step 1: Extract Memory — enableForesight=true");
        var messages = loader.loadMessages("example-data/foresight/messages.json");
        log.info("  loaded {} messages", messages.size());

        var result =
                memory.addMessages(
                                memoryId,
                                messages,
                                ExtractionConfig.defaults()
                                        .withEnableInsight(false)
                                        .withEnableForesight(true)
                                        .withLanguage("Chinese"))
                        .block();

        ExamplePrinter.printExtractionResult(result);

        // Step 2: Show FACT vs FORESIGHT breakdown
        ExamplePrinter.printSection("Step 2: FACT vs FORESIGHT Breakdown");
        ExamplePrinter.printMemoryItems(result.memoryItemResult().newItems());

        // Step 3: Retrieve foresight memory
        ExamplePrinter.printSection("Step 3: Retrieve Foresight Memory");
        var query = "未来可能需要什么帮助？";
        log.info("  query: {}", query);

        long t = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.SIMPLE).block();
        long durationMs = System.currentTimeMillis() - t;
        ExamplePrinter.printRetrievalResult(retrieval, durationMs);
    }
}
