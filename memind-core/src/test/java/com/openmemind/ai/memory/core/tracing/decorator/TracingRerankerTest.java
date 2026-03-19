package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_RERANK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRerankerTest {

    @Nested
    @DisplayName("rerank()")
    class RerankTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var scored = new ScoredResult(ScoredResult.SourceType.ITEM, "id-1", "text", 0.9f, 0.85);
            var results = List.of(scored);
            var delegate = mock(Reranker.class);
            when(delegate.rerank(any(), any(), any(int.class))).thenReturn(Mono.just(results));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<List<ScoredResult>>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingReranker(delegate, observer);

            StepVerifier.create(traced.rerank("test query", results, 5))
                    .expectNext(results)
                    .verifyComplete();

            verify(observer)
                    .observeMono(argThat(ctx -> ctx.spanName().equals(RETRIEVAL_RERANK)), any());
            verify(delegate).rerank("test query", results, 5);
        }
    }
}
