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
package com.openmemind.ai.memory.core.retrieval.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.llm.rerank.observation.LlmRerankerObservation;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.observation.LlmTypedQueryExpanderObservation;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.observation.DefaultRetrievalGraphAssistantObservation;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.observation.LlmSufficiencyGateObservation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RetrievalStageObservationCoverageTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void recordsDocumentedRetrievalStageObservationsFromReactorRecorder() {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new RetrievalTraceObservationHandler());
        var recorder =
                new BoundedRetrievalTraceRecorder(
                        "trace-1", STARTED_AT, new RetrievalTraceOptions(16, 2, 16));
        var queryContext =
                new QueryContext(
                        DefaultMemoryId.of("u1", "a1"),
                        "coffee",
                        null,
                        List.of(),
                        Map.of(),
                        null,
                        null);
        var direct = scored("1", "direct coffee memory");
        var reranked = scored("2", "reranked coffee memory");

        LlmTypedQueryExpanderObservation.observe(
                        registry,
                        "coffee",
                        List.of("preference detail"),
                        List.of("likes coffee"),
                        List.of("history"),
                        3,
                        ignored ->
                                Mono.just(
                                        List.of(
                                                new ExpandedQuery(
                                                        ExpandedQuery.QueryType.VEC,
                                                        "coffee preference"))))
                .contextWrite(context -> context.put(RetrievalTraceRecorder.class, recorder))
                .block();
        LlmSufficiencyGateObservation.observe(
                        registry,
                        queryContext,
                        List.of(direct),
                        ignored ->
                                Mono.just(
                                        new SufficiencyResult(
                                                false,
                                                "needs more",
                                                List.of(),
                                                List.of("time range"),
                                                List.of("coffee"))))
                .contextWrite(context -> context.put(RetrievalTraceRecorder.class, recorder))
                .block();
        LlmRerankerObservation.observe(
                        registry,
                        "coffee",
                        List.of(direct),
                        1,
                        ignored -> Mono.just(List.of(reranked)))
                .contextWrite(context -> context.put(RetrievalTraceRecorder.class, recorder))
                .block();
        DefaultRetrievalGraphAssistantObservation.observe(
                        registry,
                        queryContext,
                        SimpleStrategyConfig.GraphAssistConfig.defaults(),
                        List.of(direct),
                        ignored ->
                                Mono.just(
                                        new RetrievalGraphAssistResult(
                                                List.of(direct, reranked),
                                                new RetrievalGraphAssistResult.GraphAssistStats(
                                                        true, false, false, 1, 2, 0, 2, 1, 0, 0,
                                                        0))))
                .contextWrite(context -> context.put(RetrievalTraceRecorder.class, recorder))
                .block();

        var trace = recorder.snapshot().orElseThrow();
        assertThat(trace.stages())
                .extracting(RetrievalStageTrace::stage)
                .contains("query_expand", "sufficiency", "rerank", "graph_assist");
        assertThat(trace.stages())
                .filteredOn(stage -> "graph_assist".equals(stage.stage()))
                .singleElement()
                .satisfies(
                        stage -> {
                            assertThat(stage.resultCount()).isEqualTo(2);
                            assertThat(stage.candidates()).hasSize(2);
                        });
    }

    private static ScoredResult scored(String sourceId, String text) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, sourceId, text, 0.8F, 0.9D);
    }
}
