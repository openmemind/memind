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
package com.openmemind.ai.memory.example.springboot.insight;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
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
 * Insight tree example for the Spring Boot integration path.
 */
@SpringBootApplication(scanBasePackageClasses = {InsightTreeExample.class, ExampleDataLoader.class})
public class InsightTreeExample {

    private static final Logger log = LoggerFactory.getLogger(InsightTreeExample.class);

    public static void main(String[] args) {
        var application = new SpringApplication(InsightTreeExample.class);
        application.setDefaultProperties(runtimeProperties("insight"));
        application.setAdditionalProfiles("insight");
        try (ConfigurableApplicationContext ctx = application.run(args)) {
            run(
                    ctx.getBean(Memory.class),
                    ctx.getBean(ExampleDataLoader.class),
                    ctx.getBean(InsightLayer.class),
                    ctx.getBean(MemoryStore.class));
        }
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

    private static void run(
            Memory memory, ExampleDataLoader loader, InsightLayer insightLayer, MemoryStore store) {
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

        ExamplePrinter.printSection("Step 4: Flush — Force Full Insight Tree Build");
        log.info("  calling InsightLayer.flush()...");
        long startedAt = System.currentTimeMillis();
        insightLayer.flush(memoryId, config.language());
        log.info("  flush completed in {}ms", System.currentTimeMillis() - startedAt);

        ExamplePrinter.printSection("Step 5: Insight Tree — Full Structure");
        ExamplePrinter.printInsightTree(store, memoryId);
    }
}
