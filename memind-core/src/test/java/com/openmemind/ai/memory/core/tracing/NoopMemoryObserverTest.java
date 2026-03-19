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
