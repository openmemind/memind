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
package com.openmemind.ai.memory.core.extraction.insight.generator.observation;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.generator.observation.LlmInsightGeneratorObservation.InsightGenerateObservationContext;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmInsightGeneratorObservationTest {

    @Test
    void createsFreshContextForEachSubscription() {
        var contexts = new CopyOnWriteArrayList<InsightGenerateObservationContext>();
        var subscriptions = new AtomicInteger();
        Mono<InsightPointGenerateResponse> observed =
                LlmInsightGeneratorObservation.observeLeafPointGeneration(
                        ObservationRegistry.create(),
                        new MemoryInsightType(
                                1L,
                                "profile",
                                "Profile",
                                null,
                                List.of(),
                                300,
                                null,
                                null,
                                null,
                                InsightAnalysisMode.BRANCH,
                                null,
                                MemoryScope.AGENT),
                        "work",
                        context -> {
                            contexts.add(context);
                            return Mono.defer(
                                    () ->
                                            subscriptions.getAndIncrement() == 0
                                                    ? Mono.error(new IllegalStateException("first"))
                                                    : Mono.just(
                                                            new InsightPointGenerateResponse(
                                                                    List.of())));
                        });

        StepVerifier.create(observed)
                .expectErrorMatches(error -> "first".equals(error.getMessage()))
                .verify();
        StepVerifier.create(observed).expectNextCount(1).verifyComplete();

        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
        assertThat(contexts.get(0).getError()).isNotNull();
        assertThat(contexts.get(1).getError()).isNull();
    }
}
