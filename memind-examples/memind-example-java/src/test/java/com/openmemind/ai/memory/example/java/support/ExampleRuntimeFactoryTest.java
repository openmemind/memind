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
package com.openmemind.ai.memory.example.java.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTracker;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteBubbleTrackerStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExampleRuntimeFactoryTest {

    @Test
    void createUsesJdbcBubbleTrackerStoreForPureJavaRuntime(@TempDir Path tempDir)
            throws Exception {
        var settings =
                ExampleSettings.load(
                        "tool",
                        new ExampleSettings.ValueResolver() {
                            @Override
                            public String systemProperty(String key) {
                                return switch (key) {
                                    case "memind.examples.openai.api-key" -> "test-key";
                                    case "memind.examples.openai.base-url" ->
                                            "https://api.openai.com";
                                    case "memind.examples.openai.chat-model" -> "gpt-4o-mini";
                                    case "memind.examples.openai.embedding-model" ->
                                            "text-embedding-3-small";
                                    case "memind.examples.runtime-root-dir" -> tempDir.toString();
                                    case "memind.examples.vector-store.file-name" ->
                                            "vector-store.json";
                                    case "memind.examples.sqlite.file-name" -> "example.db";
                                    default -> null;
                                };
                            }

                            @Override
                            public String environmentVariable(String key) {
                                return null;
                            }
                        });

        try (var runtime =
                ExampleRuntimeFactory.create("tool", MemoryBuildOptions.defaults(), settings)) {
            var extractor =
                    readField(
                            (DefaultMemory) runtime.memory(),
                            "extractor",
                            DefaultMemoryExtractor.class);
            var insightLayer = readField(extractor, "insightStep", InsightLayer.class);
            var scheduler = readField(insightLayer, "scheduler", InsightBuildScheduler.class);
            var reorganizer = readField(scheduler, "treeReorganizer", InsightTreeReorganizer.class);

            assertThat(readField(reorganizer, "bubbleTracker", BubbleTrackerStore.class))
                    .isInstanceOf(SqliteBubbleTrackerStore.class)
                    .isNotInstanceOf(BubbleTracker.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
