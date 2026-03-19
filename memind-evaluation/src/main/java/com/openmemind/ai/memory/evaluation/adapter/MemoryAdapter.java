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
