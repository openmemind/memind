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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class InsightTreeExampleTest {

    @Test
    void flushesInsightTreeBeforeRetrieval() throws Exception {
        List<String> events = new ArrayList<>();
        Memory memory = recordingMemory(events);

        var run =
                InsightTreeExample.class.getDeclaredMethod(
                        "run", Memory.class, ExampleDataLoader.class);
        run.setAccessible(true);
        run.invoke(null, memory, new ExampleDataLoader());

        assertThat(events)
                .containsExactly(
                        "addMessages",
                        "addMessages",
                        "addMessages",
                        "flushInsights:Chinese",
                        "retrieve");
    }

    private Memory recordingMemory(List<String> events) {
        return (Memory)
                Proxy.newProxyInstance(
                        Memory.class.getClassLoader(),
                        new Class<?>[] {Memory.class},
                        (proxy, method, args) -> {
                            return switch (method.getName()) {
                                case "addMessages" -> {
                                    events.add("addMessages");
                                    yield Mono.just(
                                            ExtractionResult.success(
                                                    null, null, null, null, Duration.ZERO));
                                }
                                case "flushInsights" -> {
                                    events.add("flushInsights:" + args[1]);
                                    yield null;
                                }
                                case "retrieve" -> {
                                    events.add("retrieve");
                                    yield Mono.just(RetrievalResult.empty("deep_retrieval", ""));
                                }
                                case "close" -> null;
                                case "toString" -> "recordingMemory";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unexpected method: " + method);
                            };
                        });
    }
}
