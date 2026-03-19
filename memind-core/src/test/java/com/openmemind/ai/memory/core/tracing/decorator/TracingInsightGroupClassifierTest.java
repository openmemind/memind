package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightGroupClassifierTest {

    @Nested
    @DisplayName("classify()")
    class ClassifyTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var item =
                    new MemoryItem(
                            1L, "m1", "content", null, null, null, null, null, null, null, null,
                            null, null);
            var insightType =
                    new MemoryInsightType(
                            1L, "m1", "PROFILE", "desc", null, List.of(), 400, null, null, null,
                            null, null, null, null, null);
            var result = Map.of("group1", List.of(item));
            var items = List.of(item);
            var existingGroupNames = List.of("group1");

            var delegate = mock(InsightGroupClassifier.class);
            when(delegate.classify(any(), any(), any())).thenReturn(Mono.just(result));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<Map<String, List<MemoryItem>>>> op =
                                        invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightGroupClassifier(delegate, observer);

            StepVerifier.create(traced.classify(insightType, items, existingGroupNames))
                    .expectNext(result)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(
                                    ctx ->
                                            ctx.spanName()
                                                    .equals(EXTRACTION_INSIGHT_GROUP_CLASSIFY)),
                            any());
            verify(delegate).classify(insightType, items, existingGroupNames);
        }
    }
}
