package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_ITEM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingMemoryItemExtractStepTest {

    @Nested
    @DisplayName("extract()")
    class ExtractTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var result = MemoryItemResult.empty();
            var memoryId = mock(MemoryId.class);
            when(memoryId.toIdentifier()).thenReturn("test-id");
            var rawDataResult = RawDataResult.empty();
            var config = ItemExtractionConfig.defaults();

            var delegate = mock(MemoryItemExtractStep.class);
            when(delegate.extract(any(), any(), any())).thenReturn(Mono.just(result));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<MemoryItemResult>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingMemoryItemExtractStep(delegate, observer);

            StepVerifier.create(traced.extract(memoryId, rawDataResult, config))
                    .expectNext(result)
                    .verifyComplete();

            verify(observer)
                    .observeMono(argThat(ctx -> ctx.spanName().equals(EXTRACTION_ITEM)), any());
            verify(delegate).extract(any(), any(), any());
        }
    }
}
