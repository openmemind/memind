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
package com.openmemind.ai.memory.core.retrieval.strategy;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import reactor.core.publisher.Mono;

/**
 * Retrieval strategy interface
 *
 * <p>Ordinary interface (non-sealed), anyone can implement new strategies and register them to DefaultMemoryRetriever
 *
 */
public interface RetrievalStrategy {

    /**
     * Strategy name (used for registration and routing)
     *
     * @return Strategy name
     */
    String name();

    /**
     * Execute retrieval
     *
     * @param context Query context
     * @param config  Retrieval configuration
     * @return Retrieval result
     */
    Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config);

    /**
     * Data change notification (used for invalidating internal caches, etc.)
     *
     * @param memoryId Changed memory identifier
     */
    default void onDataChanged(MemoryId memoryId) {}
}
