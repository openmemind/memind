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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Disabled-mode thread enrichment assistant.
 */
public final class NoOpThreadEnrichmentAssistant implements ThreadEnrichmentAssistant {

    public static final NoOpThreadEnrichmentAssistant INSTANCE =
            new NoOpThreadEnrichmentAssistant();

    private NoOpThreadEnrichmentAssistant() {}

    @Override
    public Mono<List<ThreadEnrichmentResult>> enrich(
            MemoryThreadProjection thread, List<MemoryThreadEvent> itemBackedEvents) {
        return Mono.just(List.of());
    }
}
