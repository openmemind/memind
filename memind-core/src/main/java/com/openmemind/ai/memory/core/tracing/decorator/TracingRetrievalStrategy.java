package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.MEMORY_ID;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link RetrievalStrategy}.
 *
 * <p>{@link #retrieve} wraps with observer, recording span and attributes;
 * {@link #name()} and {@link #onDataChanged} delegate directly, no tracking needed.
 */
public class TracingRetrievalStrategy extends TracingSupport implements RetrievalStrategy {

    private final RetrievalStrategy delegate;

    public TracingRetrievalStrategy(RetrievalStrategy delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_STRATEGY,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_STRATEGY,
                        delegate.name()),
                r -> Map.of(RETRIEVAL_RESULT_COUNT, r.items().size()),
                () -> delegate.retrieve(context, config));
    }

    @Override
    public void onDataChanged(MemoryId memoryId) {
        delegate.onDataChanged(memoryId);
    }
}
