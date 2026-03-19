package com.openmemind.ai.memory.core.retrieval.rerank;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Direct pass reranker
 *
 * <p>Does not perform any reordering, directly returns topK
 *
 */
public class NoopReranker implements Reranker {

    @Override
    public Mono<List<ScoredResult>> rerank(String query, List<ScoredResult> results, int topK) {
        if (results.size() <= topK) {
            return Mono.just(results);
        }
        return Mono.just(results.subList(0, topK));
    }
}
