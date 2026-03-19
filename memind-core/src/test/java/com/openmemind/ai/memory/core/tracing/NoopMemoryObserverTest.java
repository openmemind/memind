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
package com.openmemind.ai.memory.core.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class NoopMemoryObserverTest {

    private final NoopMemoryObserver observer = new NoopMemoryObserver();

    @Nested
    @DisplayName("observeMono()")
    class ObserveMonoTests {

        @Test
        @DisplayName("Directly return supplier result, no additional wrapping")
        void directDelegation() {
            var ctx = ObservationContext.<String>of("test.span");
            StepVerifier.create(
                            observer.observeMono(
                                    ctx, () -> reactor.core.publisher.Mono.just("hello")))
                    .expectNext("hello")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("observeFlux()")
    class ObserveFluxTests {

        @Test
        @DisplayName("Directly return supplier result, no additional wrapping")
        void directDelegation() {
            var ctx = ObservationContext.<String>of("test.span");
            StepVerifier.create(
                            observer.observeFlux(
                                    ctx, () -> reactor.core.publisher.Flux.just("a", "b")))
                    .expectNext("a", "b")
                    .verifyComplete();
        }
    }
}
