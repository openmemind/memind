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
package com.openmemind.ai.memory.example.springboot.foresight;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.example.springboot.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.springboot.support.ExamplePrinter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Foresight example for the Spring Boot integration path.
 */
@SpringBootApplication(scanBasePackageClasses = {ForesightExample.class, ExampleDataLoader.class})
public class ForesightExample {

    private static final Logger log = LoggerFactory.getLogger(ForesightExample.class);

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = newApplication().run(args)) {
            run(ctx.getBean(Memory.class), ctx.getBean(ExampleDataLoader.class));
        }
    }

    private static SpringApplication newApplication() {
        var application = new SpringApplication(ForesightExample.class);
        application.setDefaultProperties(runtimeProperties("foresight"));
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
