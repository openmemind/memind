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
package com.openmemind.ai.memory.core.retrieval.cache;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.util.Optional;

/**
 * Retrieval cache interface
 *
 */
public interface RetrievalCache {

    /**
     * Get cached result
     *
     * @param memoryId   Memory identifier
     * @param queryHash  Query hash
     * @param configHash Configuration hash
     * @return Cached retrieval result
     */
    Optional<RetrievalResult> get(MemoryId memoryId, String queryHash, String configHash);

    /**
     * Write to cache
     *
     * @param memoryId   Memory identifier
     * @param queryHash  Query hash
     * @param configHash Configuration hash
     * @param result     Retrieval result
     */
    void put(MemoryId memoryId, String queryHash, String configHash, RetrievalResult result);

    /**
     * Invalidate all caches for the specified memoryId
     *
     * @param memoryId Memory identifier
     */
    void invalidate(MemoryId memoryId);
}
