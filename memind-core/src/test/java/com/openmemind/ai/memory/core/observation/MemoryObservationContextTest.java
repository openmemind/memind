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
package com.openmemind.ai.memory.core.observation;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.observation.SimpleRetrievalStrategyObservation;
import com.openmemind.ai.memory.core.retrieval.strategy.observation.SimpleRetrievalStrategyObservation.StrategyObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MemoryObservationContextTest {

    @Test
    void mapsReactorCancellationToCancelledStatus() {
        var observationRegistry = ObservationRegistry.create();
        observationRegistry
                .observationConfig()
                .observationHandler(
                        new ObservationHandler<Observation.Context>() {
                            @Override
                            public boolean supportsContext(Observation.Context context) {
                                return true;
                            }
                        });
        var observedContext = new AtomicReference<StrategyObservationContext>();
        Mono<com.openmemind.ai.memory.core.retrieval.RetrievalResult> observed =
                SimpleRetrievalStrategyObservation.observe(
                        observationRegistry,
                        new QueryContext(
                                () -> "memory-1",
                                "query",
                                null,
                                List.of(),
                                Map.of(),
                                null,
                                Set.of()),
                        "simple",
                        context -> {
                            observedContext.set(context);
                            return Mono.never();
                        });

        StepVerifier.create(observed).thenCancel().verify();

        assertThat(observedContext.get()).isNotNull();
        assertThat(observedContext.get().status()).isEqualTo("cancelled");
    }
}
