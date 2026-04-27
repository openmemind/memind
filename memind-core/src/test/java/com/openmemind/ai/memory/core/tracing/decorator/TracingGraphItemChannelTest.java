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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.MEMORY_ID;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_CHANNEL;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_GRAPH_DEGRADED;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_GRAPH_ENABLED;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_GRAPH_SEED_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_GRAPH_TIMEOUT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_GRAPH_CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.graph.GraphExpansionResult;
import com.openmemind.ai.memory.core.retrieval.graph.GraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingGraphItemChannelTest {

    @Test
    void retrievePublishesGraphChannelSpanAndPropagatesResult() {
        var observer = new RecordingMemoryObserver();
        GraphItemChannel delegate = mock(GraphItemChannel.class);
        var context = queryContext();
        var config = RetrievalConfig.simple();
        var settings = SimpleStrategyConfig.GraphAssistConfig.defaults();
        var seed = scoredResult("seed-1");
        var graphResult = scoredResult("graph-1");
        var result =
                new GraphExpansionResult(
                        List.of(graphResult), true, false, false, 1, 2, 3, 4, 5, 6);
        when(delegate.retrieve(context, config, settings, List.of(seed)))
                .thenReturn(Mono.just(result));

        var traced = new TracingGraphItemChannel(delegate, observer);

        StepVerifier.create(traced.retrieve(context, config, settings, List.of(seed)))
                .expectNext(result)
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        var observation = observer.monoContexts().getFirst();
        assertThat(observation.spanName()).isEqualTo(RETRIEVAL_GRAPH_CHANNEL);
        assertThat(observation.requestAttributes())
                .containsEntry(MEMORY_ID, "memory")
                .containsEntry(RETRIEVAL_CHANNEL, "graph")
                .containsEntry(RETRIEVAL_GRAPH_ENABLED, true)
                .containsEntry(RETRIEVAL_GRAPH_SEED_COUNT, 1);
        assertThat(resultAttributes(observation, result))
                .containsEntry(RETRIEVAL_RESULT_COUNT, 1)
                .containsEntry(RETRIEVAL_GRAPH_TIMEOUT, false)
                .containsEntry(RETRIEVAL_GRAPH_DEGRADED, false);
    }

    private QueryContext queryContext() {
        return new QueryContext(
                DefaultMemoryId.of("memory", null), "query", null, List.of(), Map.of(), null, null);
    }

    private ScoredResult scoredResult(String id) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, id, "text", 0.9f, 0.8d);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultAttributes(
            ObservationContext<?> context, GraphExpansionResult result) {
        return ((ObservationContext<GraphExpansionResult>) context)
                .resultExtractor()
                .extract(result);
    }
}
