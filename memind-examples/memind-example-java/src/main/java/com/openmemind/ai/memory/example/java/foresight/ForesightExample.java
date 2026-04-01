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
package com.openmemind.ai.memory.example.java.foresight;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.java.support.ExampleMemoryOptions;
import com.openmemind.ai.memory.example.java.support.ExamplePrinter;
import com.openmemind.ai.memory.example.java.support.ExampleRuntimeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Foresight example for the pure Java integration path.
 */
public final class ForesightExample {

    private static final Logger log = LoggerFactory.getLogger(ForesightExample.class);
    private static final String SCENARIO = "foresight";

    private ForesightExample() {}

    public static void main(String[] args) {
        var runtime = ExampleRuntimeFactory.create(SCENARIO, ExampleMemoryOptions.defaultOptions());
        ExamplePrinter.printRuntimeSummary(SCENARIO, runtime);
        run(runtime.memory(), runtime.dataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-foresight", "memind");

        ExamplePrinter.printSection("Step 1: Extract Memory — enableForesight=true");
        var messages = loader.loadMessages("foresight/messages.json");
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

        ExamplePrinter.printSection("Step 2: FACT vs FORESIGHT Breakdown");
        ExamplePrinter.printMemoryItems(result.memoryItemResult().newItems());

        ExamplePrinter.printSection("Step 3: Retrieve Foresight Memory");
        var query = "未来可能需要什么帮助？";
        log.info("  query: {}", query);
        long startedAt = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.SIMPLE).block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }
}
