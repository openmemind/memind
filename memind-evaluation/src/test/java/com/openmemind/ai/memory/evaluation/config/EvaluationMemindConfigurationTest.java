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
package com.openmemind.ai.memory.evaluation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EvaluationMemindConfigurationTest {

    private final EvaluationMemindConfiguration configuration = new EvaluationMemindConfiguration();

    @Test
    void memindBeanForwardsBubbleTrackerStoreIntoBuiltMemory() {
        var customBubbleTracker = proxy(BubbleTrackerStore.class);
        var memory =
                configuration.memind(
                        proxy(StructuredChatClient.class),
                        memoryStore(),
                        memoryBuffer(),
                        proxy(MemoryVector.class),
                        proxy(MemoryTextSearch.class),
                        new NoopReranker(),
                        configuration.memoryBuildOptions(new EvaluationProperties()),
                        customBubbleTracker);

        var extractor =
                readField((DefaultMemory) memory, "extractor", DefaultMemoryExtractor.class);
        var insightLayer = readField(extractor, "insightStep", InsightLayer.class);
        var scheduler = readField(insightLayer, "scheduler", InsightBuildScheduler.class);
        var reorganizer = readField(scheduler, "treeReorganizer", InsightTreeReorganizer.class);

        assertThat(readField(reorganizer, "bubbleTracker", BubbleTrackerStore.class))
                .isSameAs(customBubbleTracker);
    }

    @Test
    void memoryBuildOptionsUsesDefaultRawDataVectorBatchSize() {
        var options = configuration.memoryBuildOptions(new EvaluationProperties());

        assertThat(options.extraction().rawdata().vectorBatchSize())
                .isEqualTo(RawDataExtractionOptions.DEFAULT_VECTOR_BATCH_SIZE);
    }

    @Test
    void evaluationConfigurationBuildsTrimmedRawDataExtractionOptions() {
        var options = configuration.memoryBuildOptions(new EvaluationProperties());

        assertThat(options.extraction().rawdata())
                .extracting(RawDataExtractionOptions::vectorBatchSize)
                .isEqualTo(64);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> type.getSimpleName() + "Proxy";
                                    case "hashCode" -> System.identityHashCode(instance);
                                    case "equals" -> instance == args[0];
                                    default -> method.invoke(instance, args);
                                };
                            }
                            var returnType = method.getReturnType();
                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            if (returnType == long.class) {
                                return 0L;
                            }
                            if (returnType == Mono.class) {
                                return Mono.empty();
                            }
                            if (returnType == Flux.class) {
                                return Flux.empty();
                            }
                            if (returnType == java.util.List.class) {
                                return java.util.List.of();
                            }
                            if (returnType == java.util.Map.class) {
                                return Map.of();
                            }
                            return null;
                        });
    }

    private static MemoryStore memoryStore() {
        return MemoryStore.of(
                proxy(RawDataOperations.class),
                proxy(ItemOperations.class),
                proxy(InsightOperations.class));
    }

    private static MemoryBuffer memoryBuffer() {
        return MemoryBuffer.of(
                proxy(InsightBuffer.class),
                proxy(PendingConversationBuffer.class),
                proxy(RecentConversationBuffer.class));
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
