package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link InsightExtractStep}.
 */
public class TracingInsightExtractStep extends TracingSupport implements InsightExtractStep {

    private final InsightExtractStep delegate;

    public TracingInsightExtractStep(InsightExtractStep delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<InsightResult> extract(MemoryId memoryId, MemoryItemResult memoryItemResult) {
        return trace(
                MemorySpanNames.EXTRACTION_INSIGHT,
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                () -> delegate.extract(memoryId, memoryItemResult));
    }
}
