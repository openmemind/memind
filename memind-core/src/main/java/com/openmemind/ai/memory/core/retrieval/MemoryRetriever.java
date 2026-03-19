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
package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import reactor.core.publisher.Mono;

/**
 * Top-level memory retrieval interface
 *
 */
public interface MemoryRetriever {

    /**
     * Execute memory retrieval
     *
     * @param request Retrieval request
     * @return Retrieval result
     */
    Mono<RetrievalResult> retrieve(RetrievalRequest request);

    /**
     * Register custom retrieval strategy
     *
     * @param strategy Retrieval strategy
     */
    void registerStrategy(RetrievalStrategy strategy);

    /**
     * Data change notification (used for cache invalidation)
     *
     * @param memoryId Memory identifier that has changed
     */
    default void onDataChanged(MemoryId memoryId) {
        // Default empty implementation
    }
}
