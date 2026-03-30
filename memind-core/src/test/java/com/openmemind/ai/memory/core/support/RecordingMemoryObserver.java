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
package com.openmemind.ai.memory.core.support;

import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class RecordingMemoryObserver implements MemoryObserver {

    private final List<ObservationContext<?>> monoContexts = new ArrayList<>();
    private final List<ObservationContext<?>> fluxContexts = new ArrayList<>();

    @Override
    public <T> Mono<T> observeMono(ObservationContext<T> ctx, Supplier<Mono<T>> operation) {
        monoContexts.add(ctx);
        return operation.get();
    }

    @Override
    public <T> Flux<T> observeFlux(ObservationContext<T> ctx, Supplier<Flux<T>> operation) {
        fluxContexts.add(ctx);
        return operation.get();
    }

    public List<ObservationContext<?>> monoContexts() {
        return List.copyOf(monoContexts);
    }

    public List<ObservationContext<?>> fluxContexts() {
        return List.copyOf(fluxContexts);
    }
}
