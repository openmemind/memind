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
