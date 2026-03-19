package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link TypedQueryExpander}.
 */
public class TracingTypedQueryExpander extends TracingSupport implements TypedQueryExpander {

    private final TypedQueryExpander delegate;

    public TracingTypedQueryExpander(TypedQueryExpander delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<List<ExpandedQuery>> expand(
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions) {
        return trace(
                MemorySpanNames.RETRIEVAL_MULTI_QUERY_EXPAND,
                Map.of(),
                () ->
                        delegate.expand(
                                query, gaps, keyInformation, conversationHistory, maxExpansions));
    }
}
