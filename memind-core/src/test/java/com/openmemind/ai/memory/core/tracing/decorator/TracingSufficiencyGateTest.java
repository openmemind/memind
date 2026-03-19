package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_SUFFICIENCY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
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

class TracingSufficiencyGateTest {

    @Nested
    @DisplayName("check()")
    class CheckTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var sufficiencyResult =
                    new SufficiencyResult(
                            true,
                            "sufficient",
                            List.of("evidence1"),
                            List.of(),
                            List.of("key info"));
            var delegate = mock(SufficiencyGate.class);
            when(delegate.check(any(), any())).thenReturn(Mono.just(sufficiencyResult));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<SufficiencyResult>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingSufficiencyGate(delegate, observer);
            var memoryId = mock(MemoryId.class);
            var context =
                    new QueryContext(
                            memoryId, "test query", "test query", List.of(), Map.of(), null, null);
            var scored = new ScoredResult(ScoredResult.SourceType.ITEM, "id-1", "text", 0.9f, 0.85);
            var results = List.of(scored);

            StepVerifier.create(traced.check(context, results))
                    .expectNext(sufficiencyResult)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals(RETRIEVAL_SUFFICIENCY)), any());
            verify(delegate).check(context, results);
        }
    }
}
