package com.openmemind.ai.memory.core.retrieval.sufficiency;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Sufficiency Gate Interface
 *
 * <p>Determine whether the current results are sufficient between each layer of retrieval, and continue to drill down only if they are not sufficient
 *
 */
public interface SufficiencyGate {

    /**
     * Check the sufficiency of the current results
     *
     * @param context Query context
     * @param results Current layer's retrieval results
     * @return SufficiencyResult, containing sufficient/reasoning/evidences/gaps
     */
    Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results);
}
