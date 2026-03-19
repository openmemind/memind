package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.MEMORY_ID;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link MemoryRetriever}.
 *
 * <p>{@link #retrieve} wraps with observer, recording span and attributes;
 * {@link #registerStrategy} and {@link #onDataChanged} delegate directly, no tracking needed.
 */
public class TracingMemoryRetriever extends TracingSupport implements MemoryRetriever {

    private final MemoryRetriever delegate;

    public TracingMemoryRetriever(MemoryRetriever delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        return trace(
                MemorySpanNames.RETRIEVAL,
                Map.of(MEMORY_ID, request.memoryId().toIdentifier()),
                r -> Map.of(RETRIEVAL_RESULT_COUNT, r.items().size()),
                () -> delegate.retrieve(request));
    }

    @Override
    public void registerStrategy(RetrievalStrategy strategy) {
        delegate.registerStrategy(strategy);
    }

    @Override
    public void onDataChanged(MemoryId memoryId) {
        delegate.onDataChanged(memoryId);
    }
}
