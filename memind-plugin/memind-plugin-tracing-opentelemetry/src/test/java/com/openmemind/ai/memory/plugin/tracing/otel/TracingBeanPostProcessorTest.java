package com.openmemind.ai.memory.plugin.tracing.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightExtractStep;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGenerator;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGroupClassifier;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryExtractionPipeline;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryItemDeduplicator;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryItemExtractStep;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryRetriever;
import com.openmemind.ai.memory.core.tracing.decorator.TracingRawDataExtractStep;
import com.openmemind.ai.memory.core.tracing.decorator.TracingReranker;
import com.openmemind.ai.memory.core.tracing.decorator.TracingRetrievalStrategy;
import com.openmemind.ai.memory.core.tracing.decorator.TracingSufficiencyGate;
import com.openmemind.ai.memory.core.tracing.decorator.TracingTypedQueryExpander;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

@DisplayName("TracingBeanPostProcessor")
class TracingBeanPostProcessorTest {

    private TracingBeanPostProcessor createProcessor(MemoryObserver observer) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("memoryObserver", observer);
        ObjectProvider<MemoryObserver> provider = factory.getBeanProvider(MemoryObserver.class);
        return new TracingBeanPostProcessor(provider);
    }

    @Nested
    @DisplayName("With real MemoryObserver")
    class WithRealObserver {

        private final TracingBeanPostProcessor processor =
                createProcessor(mock(MemoryObserver.class));

        @Test
        @DisplayName("wraps MemoryExtractionPipeline")
        void wrapsExtractionPipeline() {
            var bean = mock(MemoryExtractionPipeline.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryExtractionPipeline.class);
        }

        @Test
        @DisplayName("wraps RawDataExtractStep")
        void wrapsRawDataExtractStep() {
            var bean = mock(RawDataExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingRawDataExtractStep.class);
        }

        @Test
        @DisplayName("wraps MemoryItemExtractStep")
        void wrapsMemoryItemExtractStep() {
            var bean = mock(MemoryItemExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryItemExtractStep.class);
        }

        @Test
        @DisplayName("wraps InsightExtractStep")
        void wrapsInsightExtractStep() {
            var bean = mock(InsightExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightExtractStep.class);
        }

        @Test
        @DisplayName("wraps InsightGenerator")
        void wrapsInsightGenerator() {
            var bean = mock(InsightGenerator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightGenerator.class);
        }

        @Test
        @DisplayName("wraps InsightGroupClassifier")
        void wrapsInsightGroupClassifier() {
            var bean = mock(InsightGroupClassifier.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightGroupClassifier.class);
        }

        @Test
        @DisplayName("wraps MemoryItemDeduplicator")
        void wrapsMemoryItemDeduplicator() {
            var bean = mock(MemoryItemDeduplicator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryItemDeduplicator.class);
        }

        @Test
        @DisplayName("wraps MemoryRetriever")
        void wrapsMemoryRetriever() {
            var bean = mock(MemoryRetriever.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryRetriever.class);
        }

        @Test
        @DisplayName("wraps RetrievalStrategy")
        void wrapsRetrievalStrategy() {
            var bean = mock(RetrievalStrategy.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingRetrievalStrategy.class);
        }

        @Test
        @DisplayName("wraps Reranker")
        void wrapsReranker() {
            var bean = mock(Reranker.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingReranker.class);
        }

        @Test
        @DisplayName("wraps InsightTypeRouter")
        void wrapsInsightTypeRouter() {
            var bean = mock(InsightTypeRouter.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightTypeRouter.class);
        }

        @Test
        @DisplayName("wraps TypedQueryExpander")
        void wrapsTypedQueryExpander() {
            var bean = mock(TypedQueryExpander.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingTypedQueryExpander.class);
        }

        @Test
        @DisplayName("wraps SufficiencyGate")
        void wrapsSufficiencyGate() {
            var bean = mock(SufficiencyGate.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingSufficiencyGate.class);
        }

        @Test
        @DisplayName("does not double-wrap already wrapped bean")
        void doesNotDoubleWrap() {
            var delegate = mock(MemoryExtractionPipeline.class);
            var alreadyWrapped = processor.postProcessAfterInitialization(delegate, "first");
            var result = processor.postProcessAfterInitialization(alreadyWrapped, "second");
            assertThat(result).isSameAs(alreadyWrapped);
        }

        @Test
        @DisplayName("returns unrelated beans unchanged")
        void returnsUnrelatedBeansUnchanged() {
            var bean = new Object();
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isSameAs(bean);
        }
    }

    @Nested
    @DisplayName("With NoopMemoryObserver")
    class WithNoopObserver {

        private final TracingBeanPostProcessor processor =
                createProcessor(new NoopMemoryObserver());

        @Test
        @DisplayName("does not wrap beans when observer is noop")
        void doesNotWrapWithNoop() {
            var bean = mock(InsightGenerator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isSameAs(bean);
        }
    }
}
