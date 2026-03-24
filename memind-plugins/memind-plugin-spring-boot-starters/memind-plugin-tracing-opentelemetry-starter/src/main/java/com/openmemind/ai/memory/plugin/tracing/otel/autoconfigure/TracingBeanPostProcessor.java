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

import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Spring {@link BeanPostProcessor} that automatically wraps memind beans
 * with tracing decorators when a non-noop {@link MemoryObserver} is available.
 *
 * <p>Uses pattern matching to detect each supported interface and wraps it
 * exactly once (already-wrapped beans are returned as-is).
 */
public class TracingBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<MemoryObserver> observerProvider;

    public TracingBeanPostProcessor(ObjectProvider<MemoryObserver> observerProvider) {
        this.observerProvider = observerProvider;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!isTraceableBean(bean) || isAlreadyWrapped(bean)) {
            return bean;
        }

        MemoryObserver observer = observerProvider.getIfAvailable();
        if (observer == null || observer instanceof NoopMemoryObserver) {
            return bean;
        }

        if (bean instanceof MemoryExtractionPipeline d
                && !(bean instanceof TracingMemoryExtractionPipeline)) {
            return new TracingMemoryExtractionPipeline(d, observer);
        }
        if (bean instanceof RawDataExtractStep d && !(bean instanceof TracingRawDataExtractStep)) {
            return new TracingRawDataExtractStep(d, observer);
        }
        if (bean instanceof MemoryItemExtractStep d
                && !(bean instanceof TracingMemoryItemExtractStep)) {
            return new TracingMemoryItemExtractStep(d, observer);
        }
        if (bean instanceof InsightExtractStep d && !(bean instanceof TracingInsightExtractStep)) {
            return new TracingInsightExtractStep(d, observer);
        }
        if (bean instanceof InsightGenerator d && !(bean instanceof TracingInsightGenerator)) {
            return new TracingInsightGenerator(d, observer);
        }
        if (bean instanceof InsightGroupClassifier d
                && !(bean instanceof TracingInsightGroupClassifier)) {
            return new TracingInsightGroupClassifier(d, observer);
        }
        if (bean instanceof MemoryItemDeduplicator d
                && !(bean instanceof TracingMemoryItemDeduplicator)) {
            return new TracingMemoryItemDeduplicator(d, observer);
        }

        if (bean instanceof MemoryRetriever d && !(bean instanceof TracingMemoryRetriever)) {
            return new TracingMemoryRetriever(d, observer);
        }
        if (bean instanceof RetrievalStrategy d && !(bean instanceof TracingRetrievalStrategy)) {
            return new TracingRetrievalStrategy(d, observer);
        }
        if (bean instanceof Reranker d && !(bean instanceof TracingReranker)) {
            return new TracingReranker(d, observer);
        }
        if (bean instanceof InsightTypeRouter d && !(bean instanceof TracingInsightTypeRouter)) {
            return new TracingInsightTypeRouter(d, observer);
        }
        if (bean instanceof TypedQueryExpander d && !(bean instanceof TracingTypedQueryExpander)) {
            return new TracingTypedQueryExpander(d, observer);
        }
        if (bean instanceof SufficiencyGate d && !(bean instanceof TracingSufficiencyGate)) {
            return new TracingSufficiencyGate(d, observer);
        }

        return bean;
    }

    private boolean isTraceableBean(Object bean) {
        return bean instanceof MemoryExtractionPipeline
                || bean instanceof RawDataExtractStep
                || bean instanceof MemoryItemExtractStep
                || bean instanceof InsightExtractStep
                || bean instanceof InsightGenerator
                || bean instanceof InsightGroupClassifier
                || bean instanceof MemoryItemDeduplicator
                || bean instanceof MemoryRetriever
                || bean instanceof RetrievalStrategy
                || bean instanceof Reranker
                || bean instanceof InsightTypeRouter
                || bean instanceof TypedQueryExpander
                || bean instanceof SufficiencyGate;
    }

    private boolean isAlreadyWrapped(Object bean) {
        return bean instanceof TracingMemoryExtractionPipeline
                || bean instanceof TracingRawDataExtractStep
                || bean instanceof TracingMemoryItemExtractStep
                || bean instanceof TracingInsightExtractStep
                || bean instanceof TracingInsightGenerator
                || bean instanceof TracingInsightGroupClassifier
                || bean instanceof TracingMemoryItemDeduplicator
                || bean instanceof TracingMemoryRetriever
                || bean instanceof TracingRetrievalStrategy
                || bean instanceof TracingReranker
                || bean instanceof TracingInsightTypeRouter
                || bean instanceof TracingTypedQueryExpander
                || bean instanceof TracingSufficiencyGate;
    }
}
