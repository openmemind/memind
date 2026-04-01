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
package com.openmemind.ai.memory.example.java.quickstart;

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
 * Quick start example for the pure Java integration path.
 */
public final class QuickStartExample {

    private static final Logger log = LoggerFactory.getLogger(QuickStartExample.class);
    private static final String SCENARIO = "quickstart";

    private QuickStartExample() {}

    public static void main(String[] args) {
        var runtime = ExampleRuntimeFactory.create(SCENARIO, ExampleMemoryOptions.defaultOptions());
        ExamplePrinter.printRuntimeSummary(SCENARIO, runtime);
        run(runtime.memory(), runtime.dataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-quickstart", "memind");
        var messages = loader.loadMessages("quickstart/messages.json");

        ExamplePrinter.printSection("Step 1: Extract Memory — addMessages()");
        log.info("  loaded {} messages", messages.size());

        var result =
                memory.addMessages(
                                memoryId,
                                messages,
                                ExtractionConfig.defaults().withLanguage("Chinese"))
                        .block();
        ExamplePrinter.printExtractionResult(result);

        ExamplePrinter.printSection("Step 2: Retrieve Memory — retrieve()");
        var query = "这个用户的技术背景是什么？";
        log.info("  query: {}", query);

        long startedAt = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.SIMPLE).block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }
}
