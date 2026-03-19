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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingSupportTest {

    // Specific subclass for testing
    static class TestTracingSupport extends TracingSupport {
        TestTracingSupport(MemoryObserver observer) {
            super(observer);
        }

        Mono<String> doTrace(String spanName) {
            return trace(spanName, Map.of(), () -> Mono.just("result"));
        }

        Mono<String> doTraceWithExtractor(String spanName) {
            return trace(
                    spanName, Map.of(), s -> Map.of("len", s.length()), () -> Mono.just("hello"));
        }

        Flux<String> doTraceFlux(String spanName) {
            return traceFlux(spanName, Map.of(), () -> Flux.just("a", "b"));
        }
    }

    @Test
    @DisplayName("trace() delegates to observer.observeMono()")
    void traceDelegatesToObserveMono() {
        var observer = new NoopMemoryObserver();
        var support = new TestTracingSupport(observer);

        StepVerifier.create(support.doTrace("test.span")).expectNext("result").verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("trace() with ResultExtractor passes extractor")
    void traceWithExtractorPassesExtractor() {
        var observer = mock(MemoryObserver.class);
        when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Mono<?>>) inv.getArgument(1)).get());
        var support = new TestTracingSupport(observer);

        StepVerifier.create(support.doTraceWithExtractor("test.span"))
                .expectNext("hello")
                .verifyComplete();

        verify(observer)
                .observeMono(
                        argThat(ctx -> ctx.resultExtractor().extract("hi").containsKey("len")),
                        any());
    }

    @Test
    @DisplayName("traceFlux() delegates to observer.observeFlux()")
    void traceFluxDelegatesToObserveFlux() {
        var observer = new NoopMemoryObserver();
        var support = new TestTracingSupport(observer);

        StepVerifier.create(support.doTraceFlux("test.span")).expectNext("a", "b").verifyComplete();
    }
}
