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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Shared Reactor + Micrometer Observation template for memory components. */
public final class MemoryObservation {

    private MemoryObservation() {}

    public static <T, ContextT extends Observation.Context> Mono<T> mono(
            ObservationRegistry observationRegistry,
            ObservationDocumentation document,
            ObservationConvention<ContextT> convention,
            Supplier<ContextT> contextFactory,
            Function<ContextT, Mono<T>> operation) {
        Objects.requireNonNull(contextFactory, "contextFactory");
        return mono(
                observationRegistry,
                document,
                convention,
                ignored -> contextFactory.get(),
                operation);
    }

    public static <T, ContextT extends Observation.Context> Mono<T> mono(
            ObservationRegistry observationRegistry,
            ObservationDocumentation document,
            ObservationConvention<ContextT> convention,
            Function<ContextView, ContextT> contextFactory,
            Function<ContextT, Mono<T>> operation) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(convention, "convention");
        Objects.requireNonNull(contextFactory, "contextFactory");
        Objects.requireNonNull(operation, "operation");
        ObservationRegistry registry =
                observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        return Mono.deferContextual(
                reactorContext -> {
                    ContextT context = contextFactory.apply(reactorContext);
                    Mono<T> publisher;
                    try {
                        publisher =
                                Objects.requireNonNull(
                                        operation.apply(context), "operation returned null");
                    } catch (Throwable error) {
                        publisher = Mono.error(error);
                    }
                    return publisher
                            .doOnError(context::setError)
                            .name(document.getName())
                            .tap(
                                    Micrometer.observation(
                                            registry,
                                            actualRegistry ->
                                                    document.observation(
                                                            null,
                                                            convention,
                                                            () -> context,
                                                            actualRegistry)));
                });
    }

    public static <T, ContextT extends Observation.Context> Flux<T> flux(
            ObservationRegistry observationRegistry,
            ObservationDocumentation document,
            ObservationConvention<ContextT> convention,
            Supplier<ContextT> contextFactory,
            Function<ContextT, Flux<T>> operation) {
        Objects.requireNonNull(contextFactory, "contextFactory");
        return flux(
                observationRegistry,
                document,
                convention,
                ignored -> contextFactory.get(),
                operation);
    }

    public static <T, ContextT extends Observation.Context> Flux<T> flux(
            ObservationRegistry observationRegistry,
            ObservationDocumentation document,
            ObservationConvention<ContextT> convention,
            Function<ContextView, ContextT> contextFactory,
            Function<ContextT, Flux<T>> operation) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(convention, "convention");
        Objects.requireNonNull(contextFactory, "contextFactory");
        Objects.requireNonNull(operation, "operation");
        ObservationRegistry registry =
                observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        return Flux.deferContextual(
                reactorContext -> {
                    ContextT context = contextFactory.apply(reactorContext);
                    Flux<T> publisher;
                    try {
                        publisher =
                                Objects.requireNonNull(
                                        operation.apply(context), "operation returned null");
                    } catch (Throwable error) {
                        publisher = Flux.error(error);
                    }
                    return publisher
                            .doOnError(context::setError)
                            .name(document.getName())
                            .tap(
                                    Micrometer.observation(
                                            registry,
                                            actualRegistry ->
                                                    document.observation(
                                                            null,
                                                            convention,
                                                            () -> context,
                                                            actualRegistry)));
                });
    }
}
