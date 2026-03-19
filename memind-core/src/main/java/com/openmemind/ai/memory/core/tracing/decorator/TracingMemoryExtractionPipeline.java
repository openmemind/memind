package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link MemoryExtractionPipeline}.
 *
 * <p>All extraction methods are wrapped by the observer to record spans and attributes.
 */
public class TracingMemoryExtractionPipeline extends TracingSupport
        implements MemoryExtractionPipeline {

    private final MemoryExtractionPipeline delegate;

    public TracingMemoryExtractionPipeline(
            MemoryExtractionPipeline delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<ExtractionResult> extract(ExtractionRequest request) {
        return trace(
                MemorySpanNames.EXTRACTION,
                Map.of(MemoryAttributes.MEMORY_ID, request.memoryId().toIdentifier()),
                () -> delegate.extract(request));
    }

    @Override
    public Mono<ExtractionResult> addMessage(
            MemoryId memoryId, Message message, ExtractionConfig config) {
        return trace(
                MemorySpanNames.EXTRACTION,
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                () -> delegate.addMessage(memoryId, message, config));
    }
}
