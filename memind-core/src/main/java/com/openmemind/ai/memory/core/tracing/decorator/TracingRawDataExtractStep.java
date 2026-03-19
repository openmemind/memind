package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link RawDataExtractStep}.
 *
 * <p>Records the number of segments as a result attribute.
 */
public class TracingRawDataExtractStep extends TracingSupport implements RawDataExtractStep {

    private final RawDataExtractStep delegate;

    public TracingRawDataExtractStep(RawDataExtractStep delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<RawDataResult> extract(
            MemoryId memoryId,
            RawContent content,
            String contentType,
            Map<String, Object> metadata) {
        return trace(
                MemorySpanNames.EXTRACTION_RAWDATA,
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                r -> Map.of(MemoryAttributes.EXTRACTION_SEGMENT_COUNT, r.segments().size()),
                () -> delegate.extract(memoryId, content, contentType, metadata));
    }
}
