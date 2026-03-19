package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightExtractStepTest {

    @Nested
    @DisplayName("extract()")
    class ExtractTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var result = InsightResult.empty();
            var memoryId = mock(MemoryId.class);
            when(memoryId.toIdentifier()).thenReturn("test-id");
            var memoryItemResult = MemoryItemResult.empty();

            var delegate = mock(InsightExtractStep.class);
            when(delegate.extract(any(), any())).thenReturn(Mono.just(result));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<InsightResult>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightExtractStep(delegate, observer);

            StepVerifier.create(traced.extract(memoryId, memoryItemResult))
                    .expectNext(result)
                    .verifyComplete();

            verify(observer)
                    .observeMono(argThat(ctx -> ctx.spanName().equals(EXTRACTION_INSIGHT)), any());
            verify(delegate).extract(memoryId, memoryItemResult);
        }
    }
}
