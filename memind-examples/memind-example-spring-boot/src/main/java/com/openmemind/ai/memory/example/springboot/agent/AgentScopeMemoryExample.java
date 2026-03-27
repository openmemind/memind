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
package com.openmemind.ai.memory.example.springboot.agent;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.example.springboot.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.springboot.support.ExamplePrinter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Agent-scope memory example for the Spring Boot integration path.
 */
@SpringBootApplication(
        scanBasePackageClasses = {AgentScopeMemoryExample.class, ExampleDataLoader.class})
public class AgentScopeMemoryExample {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeMemoryExample.class);
    private static final String LANGUAGE = "Chinese";

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = newApplication().run(args)) {
            run(
                    ctx.getBean(Memory.class),
                    ctx.getBean(ExampleDataLoader.class),
                    ctx.getBean(MemoryStore.class));
        }
    }

    private static SpringApplication newApplication() {
        var application = new SpringApplication(AgentScopeMemoryExample.class);
        application.setDefaultProperties(runtimeProperties("agent"));
        application.setAdditionalProfiles("insight");
        return application;
    }

    private static Map<String, Object> runtimeProperties(String scenario) {
        return Map.of(
                "spring.datasource.url",
                "jdbc:sqlite:./target/example-runtime/" + scenario + "/memind.db",
                "spring.datasource.driver-class-name",
                "org.sqlite.JDBC",
                "memind.vector.store-path",
                "./target/example-runtime/" + scenario + "/vector-store.json");
    }

    private static void run(Memory memory, ExampleDataLoader loader, MemoryStore store) {
        var memoryId = DefaultMemoryId.of("user-agent-scope", "memind");
        var config = ExtractionConfig.agentOnly().withLanguage(LANGUAGE);
        var dataFiles = new String[] {"agent/messages-1.json", "agent/messages-2.json"};
        var labels = new String[] {"directives + playbooks", "resolutions"};

        for (int i = 0; i < dataFiles.length; i++) {
            ExamplePrinter.printSection(
                    "Step " + (i + 1) + ": Extract Agent Memory — " + labels[i]);
            var messages = loader.loadMessages(dataFiles[i]);
            log.info("  loaded {} messages", messages.size());

            var result = memory.addMessages(memoryId, messages, config).block();
            ExamplePrinter.printExtractionResult(result);
        }

        ExamplePrinter.printSection("Step 3: Flush Agent Insights — flushInsights()");
        log.info("  flushing agent insight tree...");
        long flushStartedAt = System.currentTimeMillis();
        memory.flushInsights(memoryId, LANGUAGE);
        log.info("  flush completed in {}ms", System.currentTimeMillis() - flushStartedAt);

        ExamplePrinter.printSection("Step 4: Agent Insight Tree — Full Structure");
        ExamplePrinter.printInsightTree(store, memoryId);

        retrieveAgentMemory(
                memory, memoryId, "Step 5: Retrieve Directive Memory", "开始修改生产配置前必须准备什么？");
        retrieveAgentMemory(
                memory, memoryId, "Step 6: Retrieve Playbook Memory", "收到 webhook 重试告警时应该怎么排查？");
        retrieveAgentMemory(
                memory, memoryId, "Step 7: Retrieve Resolution Memory", "staging 启动失败的问题后来怎么解决的？");
    }

    private static void retrieveAgentMemory(
            Memory memory, DefaultMemoryId memoryId, String title, String query) {
        ExamplePrinter.printSection(title);
        log.info("  query: {}", query);

        long startedAt = System.currentTimeMillis();
        var retrieval =
                memory.retrieve(
                                RetrievalRequest.agentMemory(
                                        memoryId, query, RetrievalConfig.Strategy.SIMPLE))
                        .block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }
}
