package com.openmemind.ai.memory.core.tracing;

import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * No-op MemoryObserver implementation, zero overhead direct delegation.
 *
 * <p>Directly call {@code op.get()} to return the original Mono/Flux, without additional wrapping defer,
 * avoiding unnecessary MonoDefer/FluxDefer operator overhead on high-frequency paths.
 * Lazy semantics are guaranteed by the caller (the Supplier passed in by the decorator).
 */
public final class NoopMemoryObserver implements MemoryObserver {

    @Override
    public <T> Mono<T> observeMono(ObservationContext<T> ctx, Supplier<Mono<T>> op) {
        return op.get();
    }

    @Override
    public <T> Flux<T> observeFlux(ObservationContext<T> ctx, Supplier<Flux<T>> op) {
        return op.get();
    }
}
