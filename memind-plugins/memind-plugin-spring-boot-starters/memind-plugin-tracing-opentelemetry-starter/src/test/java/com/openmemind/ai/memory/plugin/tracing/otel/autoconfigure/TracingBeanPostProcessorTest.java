/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.plugin.tracing.otel.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import com.openmemind.ai.memory.core.tracing.decorator.*;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryExtractor;

import java.lang.reflect.Proxy;
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

    private TracingBeanPostProcessor createProcessor(
            ObjectProvider<MemoryObserver> observerProvider) {
        return new TracingBeanPostProcessor(observerProvider);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "toString" -> type.getSimpleName() + "Proxy";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default -> null;
                                });
    }

    @Nested
    @DisplayName("With real MemoryObserver")
    class WithRealObserver {

        private final TracingBeanPostProcessor processor =
                createProcessor(new TestMemoryObserver());

        @Test
        @DisplayName("wraps MemoryExtractor")
        void wrapsExtractionPipeline() {
            var bean = proxy(MemoryExtractor.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryExtractor.class);
        }

        @Test
        @DisplayName("wraps RawDataExtractStep")
        void wrapsRawDataExtractStep() {
            var bean = proxy(RawDataExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingRawDataExtractStep.class);
        }

        @Test
        @DisplayName("wraps MemoryItemExtractStep")
        void wrapsMemoryItemExtractStep() {
            var bean = proxy(MemoryItemExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryItemExtractStep.class);
        }

        @Test
        @DisplayName("wraps InsightExtractStep")
        void wrapsInsightExtractStep() {
            var bean = proxy(InsightExtractStep.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightExtractStep.class);
        }

        @Test
        @DisplayName("wraps InsightGenerator")
        void wrapsInsightGenerator() {
            var bean = proxy(InsightGenerator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightGenerator.class);
        }

        @Test
        @DisplayName("wraps InsightGroupClassifier")
        void wrapsInsightGroupClassifier() {
            var bean = proxy(InsightGroupClassifier.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightGroupClassifier.class);
        }

        @Test
        @DisplayName("wraps MemoryItemDeduplicator")
        void wrapsMemoryItemDeduplicator() {
            var bean = proxy(MemoryItemDeduplicator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryItemDeduplicator.class);
        }

        @Test
        @DisplayName("wraps MemoryRetriever")
        void wrapsMemoryRetriever() {
            var bean = proxy(MemoryRetriever.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingMemoryRetriever.class);
        }

        @Test
        @DisplayName("wraps RetrievalStrategy")
        void wrapsRetrievalStrategy() {
            var bean = proxy(RetrievalStrategy.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingRetrievalStrategy.class);
        }

        @Test
        @DisplayName("wraps Reranker")
        void wrapsReranker() {
            var bean = proxy(Reranker.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingReranker.class);
        }

        @Test
        @DisplayName("wraps InsightTypeRouter")
        void wrapsInsightTypeRouter() {
            var bean = proxy(InsightTypeRouter.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingInsightTypeRouter.class);
        }

        @Test
        @DisplayName("wraps TypedQueryExpander")
        void wrapsTypedQueryExpander() {
            var bean = proxy(TypedQueryExpander.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingTypedQueryExpander.class);
        }

        @Test
        @DisplayName("wraps SufficiencyGate")
        void wrapsSufficiencyGate() {
            var bean = proxy(SufficiencyGate.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isInstanceOf(TracingSufficiencyGate.class);
        }

        @Test
        @DisplayName("does not double-wrap already wrapped bean")
        void doesNotDoubleWrap() {
            var delegate = proxy(MemoryExtractor.class);
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

        @Test
        @DisplayName("does not resolve observer for unrelated beans")
        void doesNotResolveObserverForUnrelatedBeans() {
            ObjectProvider<MemoryObserver> provider =
                    new ObjectProvider<>() {
                        @Override
                        public MemoryObserver getIfAvailable() {
                            throw new AssertionError(
                                    "observerProvider should not be accessed for unrelated beans");
                        }
                    };

            var unrelatedBean = new Object();
            var result =
                    createProcessor(provider)
                            .postProcessAfterInitialization(unrelatedBean, "unrelatedBean");

            assertThat(result).isSameAs(unrelatedBean);
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
            var bean = proxy(InsightGenerator.class);
            var result = processor.postProcessAfterInitialization(bean, "test");
            assertThat(result).isSameAs(bean);
        }
    }

    private static final class TestMemoryObserver implements MemoryObserver {

        @Override
        public <T> reactor.core.publisher.Mono<T> observeMono(
                com.openmemind.ai.memory.core.tracing.ObservationContext<T> ctx,
                java.util.function.Supplier<reactor.core.publisher.Mono<T>> operation) {
            return operation.get();
        }

        @Override
        public <T> reactor.core.publisher.Flux<T> observeFlux(
                com.openmemind.ai.memory.core.tracing.ObservationContext<T> ctx,
                java.util.function.Supplier<reactor.core.publisher.Flux<T>> operation) {
            return operation.get();
        }
    }
}
