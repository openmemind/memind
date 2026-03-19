package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link InsightTypeRouter}.
 */
public class TracingInsightTypeRouter extends TracingSupport implements InsightTypeRouter {

    private final InsightTypeRouter delegate;

    public TracingInsightTypeRouter(InsightTypeRouter delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<List<String>> route(
            String query, List<String> conversationHistory, Map<String, String> availableTypes) {
        return trace(
                MemorySpanNames.RETRIEVAL_INSIGHT_TYPE_ROUTING,
                Map.of(),
                () -> delegate.route(query, conversationHistory, availableTypes));
    }
}
