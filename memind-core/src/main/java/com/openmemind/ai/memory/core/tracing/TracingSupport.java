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

import java.util.Map;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator base class, providing trace shortcut methods.
 *
 * <p>All Tracing decorators inherit this class, delegating business operations
 * to {@link MemoryObserver} through {@link #trace} / {@link #traceFlux}.
 */
public abstract class TracingSupport {

    protected final MemoryObserver observer;

    protected TracingSupport(MemoryObserver observer) {
        this.observer = observer;
    }

    protected <T> Mono<T> trace(
            String spanName, Map<String, Object> attrs, Supplier<Mono<T>> operation) {
        return observer.observeMono(ObservationContext.of(spanName, attrs), operation);
    }

    protected <T> Mono<T> trace(
            String spanName,
            Map<String, Object> attrs,
            ResultAttributeExtractor<T> resultExtractor,
            Supplier<Mono<T>> operation) {
        return observer.observeMono(
                ObservationContext.<T>of(spanName, attrs).withResultExtractor(resultExtractor),
                operation);
    }

    protected <T> Flux<T> traceFlux(
            String spanName, Map<String, Object> attrs, Supplier<Flux<T>> operation) {
        return observer.observeFlux(ObservationContext.of(spanName, attrs), operation);
    }
}
