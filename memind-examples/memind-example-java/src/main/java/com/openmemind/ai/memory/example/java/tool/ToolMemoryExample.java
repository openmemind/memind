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
package com.openmemind.ai.memory.example.java.tool;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.java.support.ExampleMemoryOptions;
import com.openmemind.ai.memory.example.java.support.ExamplePrinter;
import com.openmemind.ai.memory.example.java.support.ExampleRuntimeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool memory example for the pure Java integration path.
 */
public final class ToolMemoryExample {

    private static final Logger log = LoggerFactory.getLogger(ToolMemoryExample.class);
    private static final String SCENARIO = "tool";

    private ToolMemoryExample() {}

    public static void main(String[] args) {
        var runtime = ExampleRuntimeFactory.create(SCENARIO, ExampleMemoryOptions.defaultOptions());
        ExamplePrinter.printRuntimeSummary(SCENARIO, runtime);
        run(runtime.memory(), runtime.dataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-tool", "memind");

        ExamplePrinter.printSection("Step 1: Report Tool Calls — reportToolCalls()");
        var toolRecords = loader.loadToolCalls("tool/tool-calls.json");
        log.info("  loaded {} tool call records", toolRecords.size());

        var toolResult = memory.reportToolCalls(memoryId, toolRecords).block();
        ExamplePrinter.printExtractionResult(toolResult);

        ExamplePrinter.printSection("Step 2: Tool Statistics — getToolStats() / getAllToolStats()");
        log.info("  ── grep_code stats ──");
        ExamplePrinter.printToolStats(
                "grep_code", memory.getToolStats(memoryId, "grep_code").block());

        log.info("  ── all tool stats ──");
        ExamplePrinter.printAllToolStats(memory.getAllToolStats(memoryId).block());
    }
}
