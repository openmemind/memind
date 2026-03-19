package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_INSIGHT_TYPE_ROUTING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
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

class TracingInsightTypeRouterTest {

    @Nested
    @DisplayName("route()")
    class RouteTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with observer")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var routedTypes = List.of("preference", "behavior");
            var delegate = mock(InsightTypeRouter.class);
            when(delegate.route(any(), any(), any())).thenReturn(Mono.just(routedTypes));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<List<String>>> op = invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightTypeRouter(delegate, observer);
            var availableTypes =
                    Map.of("preference", "User preferences", "behavior", "User behavior");

            StepVerifier.create(traced.route("test query", List.of(), availableTypes))
                    .expectNext(routedTypes)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals(RETRIEVAL_INSIGHT_TYPE_ROUTING)),
                            any());
            verify(delegate).route("test query", List.of(), availableTypes);
        }
    }
}
