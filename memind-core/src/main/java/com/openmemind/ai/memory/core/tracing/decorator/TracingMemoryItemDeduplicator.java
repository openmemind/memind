package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link MemoryItemDeduplicator}.
 *
 * <p>Uses the delegate's spanName as the span name.
 */
public class TracingMemoryItemDeduplicator extends TracingSupport
        implements MemoryItemDeduplicator {

    private final MemoryItemDeduplicator delegate;

    public TracingMemoryItemDeduplicator(MemoryItemDeduplicator delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<DeduplicationResult> deduplicate(
            MemoryId memoryId, List<ExtractedMemoryEntry> entries) {
        return trace(
                delegate.spanName(),
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                () -> delegate.deduplicate(memoryId, entries));
    }

    @Override
    public String spanName() {
        return delegate.spanName();
    }
}
