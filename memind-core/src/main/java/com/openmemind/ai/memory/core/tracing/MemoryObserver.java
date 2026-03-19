package com.openmemind.ai.memory.core.tracing;

import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Observability provider abstraction
 *
 * <p>Defines the observation wrapping capability for Mono/Flux operations. The default implementation is {@link NoopMemoryObserver} (zero overhead),
 * specific implementations are provided by plugin modules (such as OpenTelemetry).
 *
 * <p><b>Constraints:</b> operation supplier must return cold Mono/Flux (lazy), cannot return already subscribed hot streams.
 */
public interface MemoryObserver {

    <T> Mono<T> observeMono(ObservationContext<T> ctx, Supplier<Mono<T>> operation);

    <T> Flux<T> observeFlux(ObservationContext<T> ctx, Supplier<Flux<T>> operation);
}
