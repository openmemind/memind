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
package com.openmemind.ai.memory.example.java.insight;

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
 * Insight tree example for the pure Java integration path.
 */
public final class InsightTreeExample {

    private static final Logger log = LoggerFactory.getLogger(InsightTreeExample.class);
    private static final String SCENARIO = "insight";

    private InsightTreeExample() {}

    public static void main(String[] args) {
        var runtime =
                ExampleRuntimeFactory.create(SCENARIO, ExampleMemoryOptions.insightTreeOptions());
        ExamplePrinter.printRuntimeSummary(SCENARIO, runtime);
        run(runtime.memory(), runtime.dataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-insight", "memind");
        var config = ExtractionConfig.defaults().withLanguage("Chinese");
        var dataFiles =
                new String[] {
                    "insight/messages-1.json", "insight/messages-2.json", "insight/messages-3.json"
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

        ExamplePrinter.printSection("Step 4: Retrieve Synthesized Insights");
        var query = "这个用户在身份、关系、偏好和行为上有哪些稳定特征？";
        log.info("  query: {}", query);
        long startedAt = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.DEEP).block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }
}
