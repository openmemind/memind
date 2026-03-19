package com.openmemind.ai.memory.core.tracing;

import java.util.Map;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator base class, providing trace shortcut methods.
 *
 * <p>All Tracing decorators inherit this class, delegating business operations to {@link MemoryObserver} through {@link #trace} / {@link #traceFlux}.
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
