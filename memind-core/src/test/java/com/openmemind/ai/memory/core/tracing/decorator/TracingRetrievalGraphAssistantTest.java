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
package com.openmemind.ai.memory.core.tracing.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingRetrievalGraphAssistantTest {

    @Test
    void tracingDecoratorShouldEmitFullGraphAssistObservationSurface() {
        var observer = new RecordingMemoryObserver();
        RetrievalGraphAssistant delegate =
                (context, config, strategyConfig, directItems) ->
                        Mono.just(
                                new RetrievalGraphAssistResult(
                                        directItems,
                                        new RetrievalGraphAssistResult.GraphAssistStats(
                                                true,
                                                false,
                                                false,
                                                2,
                                                3,
                                                4,
                                                5,
                                                6,
                                                1,
                                                1,
                                                1)));
        var traced = new TracingRetrievalGraphAssistant(delegate, observer);
        var context =
                new QueryContext(
                        TestMemoryIds.userAgent(),
                        "what changed",
                        null,
                        List.of(),
                        Map.of(),
                        null,
                        null);
        var direct = List.of(new ScoredResult(ScoredResult.SourceType.ITEM, "101", "item-101", 0.8f, 1.0d));

        StepVerifier.create(
                        traced.assist(
                                context,
                                RetrievalConfig.simple(),
                                SimpleStrategyConfig.defaults()
                                        .withGraphAssist(
                                                SimpleStrategyConfig.GraphAssistConfig.defaults().withEnabled(true)),
                                direct))
                .assertNext(result -> assertThat(result.items()).containsExactlyElementsOf(direct))
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        var observation = observer.monoContexts().getFirst();
        assertThat(observation.spanName()).isEqualTo(MemorySpanNames.RETRIEVAL_GRAPH_ASSIST);
        assertThat(observation.requestAttributes())
                .containsEntry(MemoryAttributes.MEMORY_ID, context.memoryId().toIdentifier())
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_ENABLED, true);

        @SuppressWarnings("unchecked")
        var resultAttributes =
                ((com.openmemind.ai.memory.core.tracing.ObservationContext<RetrievalGraphAssistResult>) observation)
                        .resultExtractor()
                        .extract(
                                new RetrievalGraphAssistResult(
                                        direct,
                                        new RetrievalGraphAssistResult.GraphAssistStats(
                                                true,
                                                false,
                                                false,
                                                2,
                                                3,
                                                4,
                                                5,
                                                6,
                                                1,
                                                1,
                                                1)));
        assertThat(resultAttributes)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_SEED_COUNT, 2)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_LINK_EXPANSION_COUNT, 3)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_ENTITY_EXPANSION_COUNT, 4)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_DEDUPED_CANDIDATE_COUNT, 5)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_ADMITTED_CANDIDATE_COUNT, 6)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_DISPLACED_DIRECT_COUNT, 1)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_OVERLAP_COUNT, 1)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT, 1)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_TIMEOUT, false)
                .containsEntry(MemoryAttributes.RETRIEVAL_GRAPH_DEGRADED, false);
    }
}
