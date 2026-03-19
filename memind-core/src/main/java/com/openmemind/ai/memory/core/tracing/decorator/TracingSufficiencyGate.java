package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_SUFFICIENT;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link SufficiencyGate}.
 */
public class TracingSufficiencyGate extends TracingSupport implements SufficiencyGate {

    private final SufficiencyGate delegate;

    public TracingSufficiencyGate(SufficiencyGate delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results) {
        return trace(
                MemorySpanNames.RETRIEVAL_SUFFICIENCY,
                Map.of(),
                r -> Map.of(RETRIEVAL_SUFFICIENT, r.sufficient()),
                () -> delegate.check(context, results));
    }
}
