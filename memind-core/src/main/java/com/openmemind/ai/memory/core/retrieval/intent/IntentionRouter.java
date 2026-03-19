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
package com.openmemind.ai.memory.core.retrieval.intent;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Intention routing interface
 *
 * <p>Determine whether the current query needs to retrieve memory, and can also override query
 *
 */
public interface IntentionRouter {

    /**
     * Determine retrieval intention
     *
     * @param memoryId            Memory identifier
     * @param query               User query
     * @param conversationHistory Recent conversation history
     * @return Retrieval intention
     */
    Mono<RetrievalIntent> route(MemoryId memoryId, String query, List<String> conversationHistory);
}
