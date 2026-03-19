package com.openmemind.ai.memory.core.retrieval.rerank;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Reranking interface
 *
 * <p>Refine the list of recalled results and return the topK results after reordering
 *
 */
public interface Reranker {

    Mono<List<ScoredResult>> rerank(String query, List<ScoredResult> results, int topK);
}
