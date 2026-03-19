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
package com.openmemind.ai.memory.evaluation.adapter;

import com.openmemind.ai.memory.evaluation.adapter.model.AddRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.AddResult;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import reactor.core.publisher.Mono;

/**
 * Memory system adapter interface, defining four core operations: add/search/answer/clean
 *
 */
public interface MemoryAdapter {
    String name();

    Mono<AddResult> add(AddRequest request);

    Mono<SearchResult> search(SearchRequest request);

    Mono<String> answer(String question, String formattedContext, QAPair qaPair);

    /**
     * Clear all memories of the user corresponding to the specified conversationId; default no-op
     *
     */
    default Mono<Void> clean(String conversationId) {
        return Mono.empty();
    }
}
