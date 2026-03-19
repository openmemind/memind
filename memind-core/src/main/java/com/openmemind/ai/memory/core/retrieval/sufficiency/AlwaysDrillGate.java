package com.openmemind.ai.memory.core.retrieval.sufficiency;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Always drill gate: always returns insufficient, forces drilling down to the next level
 *
 */
public class AlwaysDrillGate implements SufficiencyGate {

    @Override
    public Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results) {
        return Mono.just(SufficiencyResult.fallbackInsufficient());
    }
}
