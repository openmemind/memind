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
package com.openmemind.ai.memory.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.generator.LlmInsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.LlmInsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.item.MemoryItemLayer;
import com.openmemind.ai.memory.core.extraction.item.dedup.CompositeDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.DefaultItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.DefaultGraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.graph.DefaultRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.scoring.DefaultRetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.sufficiency.LlmSufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.temporal.DefaultTemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.thread.DefaultMemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.LlmInsightTypeRouter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservationDocumentationSchemaTest {

    private static final List<Class<?>> OBSERVED_COMPONENTS =
            List.of(
                    DefaultMemoryExtractor.class,
                    RawDataLayer.class,
                    MemoryItemLayer.class,
                    CompositeDeduplicator.class,
                    DefaultItemGraphMaterializer.class,
                    InsightLayer.class,
                    LlmInsightGenerator.class,
                    LlmInsightGroupClassifier.class,
                    InsightBuildScheduler.class,
                    LlmReranker.class,
                    DefaultMemoryRetriever.class,
                    LlmTypedQueryExpander.class,
                    DefaultGraphItemChannel.class,
                    DefaultRetrievalGraphAssistant.class,
                    DefaultRetrievalResultMerger.class,
                    SimpleRetrievalStrategy.class,
                    DeepRetrievalStrategy.class,
                    LlmSufficiencyGate.class,
                    DefaultTemporalItemChannel.class,
                    DefaultMemoryThreadAssistant.class,
                    InsightTierRetriever.class,
                    ItemTierRetriever.class,
                    LlmInsightTypeRouter.class);

    @Test
    void observedDocumentsDeclareHighCardinalityKeySchema() {
        var missingDocuments = new ArrayList<String>();

        for (var component : OBSERVED_COMPONENTS) {
            if (!declaresHighCardinalityKeyNames(component)) {
                continue;
            }
            for (var document : documents(component)) {
                if (document.getHighCardinalityKeyNames().length == 0) {
                    missingDocuments.add(
                            observationHolder(component).getName() + "." + document.getName());
                }
            }
        }

        assertThat(missingDocuments).isEmpty();
    }

    @Test
    void observedComponentsKeepObservationContractsInObservationSubpackage() {
        var misplacedContracts = new ArrayList<String>();

        for (var component : OBSERVED_COMPONENTS) {
            for (var nestedClass : component.getDeclaredClasses()) {
                if (ObservationDocumentation.class.isAssignableFrom(nestedClass)
                        || Observation.Context.class.isAssignableFrom(nestedClass)) {
                    misplacedContracts.add(component.getName() + "." + nestedClass.getSimpleName());
                }
            }
            observationHolder(component);
        }

        assertThat(misplacedContracts).isEmpty();
    }

    private static List<ObservationDocumentation> documents(Class<?> component) {
        var documents = new ArrayList<ObservationDocumentation>();
        for (var nestedClass : observationHolder(component).getDeclaredClasses()) {
            if (!ObservationDocumentation.class.isAssignableFrom(nestedClass)
                    || !nestedClass.isEnum()) {
                continue;
            }
            for (var constant : nestedClass.getEnumConstants()) {
                documents.add((ObservationDocumentation) constant);
            }
        }
        assertThat(documents)
                .as(
                        observationHolder(component).getName()
                                + " should declare local observation documents")
                .isNotEmpty();
        return documents;
    }

    private static boolean declaresHighCardinalityKeyNames(Class<?> component) {
        for (var nestedClass : observationHolder(component).getDeclaredClasses()) {
            if (nestedClass.getSimpleName().equals("HighCardinalityKeyNames")) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> observationHolder(Class<?> component) {
        var holderName =
                component.getPackageName()
                        + ".observation."
                        + component.getSimpleName()
                        + "Observation";
        try {
            return Class.forName(holderName);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Missing observation holder " + holderName, ex);
        }
    }
}
