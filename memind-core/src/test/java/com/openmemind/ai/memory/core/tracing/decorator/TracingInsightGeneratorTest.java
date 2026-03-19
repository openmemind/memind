package com.openmemind.ai.memory.core.tracing.decorator;

import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_BRANCH;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_LEAF;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_ROOT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingInsightGeneratorTest {

    private static MemoryInsightType insightType() {
        return new MemoryInsightType(
                null,
                null,
                "test-type",
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static MemoryInsight memoryInsight() {
        return new MemoryInsight(
                null, null, null, null, null, null, null, null, 0f, null, null, null, null, null,
                null, null, 0);
    }

    @Nested
    @DisplayName("generatePoints()")
    class GeneratePointsTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with leaf span")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var response = new InsightPointGenerateResponse(List.of());
            var delegate = mock(InsightGenerator.class);
            when(delegate.generatePoints(any(), any(), any(), any(), anyInt(), any(), any()))
                    .thenReturn(Mono.just(response));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<InsightPointGenerateResponse>> op =
                                        invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightGenerator(delegate, observer);

            StepVerifier.create(
                            traced.generatePoints(
                                    insightType(),
                                    "group-a",
                                    List.of(),
                                    List.of(),
                                    100,
                                    null,
                                    null))
                    .expectNext(response)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals(EXTRACTION_INSIGHT_GENERATE_LEAF)),
                            any());
            verify(delegate).generatePoints(any(), any(), any(), any(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("generateBranchSummary()")
    class GenerateBranchSummaryTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with branch span")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var response = new InsightPointGenerateResponse(List.of());
            var delegate = mock(InsightGenerator.class);
            when(delegate.generateBranchSummary(any(), any(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(response));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<InsightPointGenerateResponse>> op =
                                        invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightGenerator(delegate, observer);
            List<MemoryInsight> leafInsights = List.of(memoryInsight());

            StepVerifier.create(
                            traced.generateBranchSummary(
                                    insightType(), List.of(), leafInsights, 100, null))
                    .expectNext(response)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(
                                    ctx ->
                                            ctx.spanName()
                                                    .equals(EXTRACTION_INSIGHT_GENERATE_BRANCH)),
                            any());
            verify(delegate).generateBranchSummary(any(), any(), any(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("generateRootSynthesis()")
    class GenerateRootSynthesisTests {

        @Test
        @DisplayName("Delegate to delegate and wrap with root span")
        @SuppressWarnings("unchecked")
        void delegatesAndWraps() {
            var response = new InsightPointGenerateResponse(List.of());
            var delegate = mock(InsightGenerator.class);
            when(delegate.generateRootSynthesis(any(), any(), any(), anyInt(), any()))
                    .thenReturn(Mono.just(response));

            var observer = mock(MemoryObserver.class);
            when(observer.observeMono(any(ObservationContext.class), any(Supplier.class)))
                    .thenAnswer(
                            invocation -> {
                                Supplier<Mono<InsightPointGenerateResponse>> op =
                                        invocation.getArgument(1);
                                return op.get();
                            });

            var traced = new TracingInsightGenerator(delegate, observer);
            List<MemoryInsight> branchInsights = List.of(memoryInsight());

            StepVerifier.create(
                            traced.generateRootSynthesis(
                                    insightType(), "existing summary", branchInsights, 100, null))
                    .expectNext(response)
                    .verifyComplete();

            verify(observer)
                    .observeMono(
                            argThat(ctx -> ctx.spanName().equals(EXTRACTION_INSIGHT_GENERATE_ROOT)),
                            any());
            verify(delegate).generateRootSynthesis(any(), any(), any(), anyInt(), any());
        }
    }
}
