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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Retrieval remains direct-only during the thread projection V1 rollout.
 */
public final class DefaultMemoryThreadAssistant implements MemoryThreadAssistant {

    public DefaultMemoryThreadAssistant(MemoryStore store) {}

    @Override
    public Mono<MemoryThreadAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        return Mono.just(
                MemoryThreadAssistResult.directOnly(
                        directWindow, settings != null && settings.enabled()));
    }
}
