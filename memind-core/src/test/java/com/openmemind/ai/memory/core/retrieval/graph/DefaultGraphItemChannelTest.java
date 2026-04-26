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
package com.openmemind.ai.memory.core.retrieval.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultGraphItemChannelTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final QueryContext CONTEXT =
            new QueryContext(MEMORY_ID, "query", null, List.of(), Map.of(), null, null);
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();
    private static final SimpleStrategyConfig.GraphAssistConfig ENABLED_SETTINGS =
            SimpleStrategyConfig.GraphAssistConfig.defaults().withEnabled(true);

    @Test
    void disabledGraphSettingsReturnsEmpty() {
        var channel =
                new DefaultGraphItemChannel(
                        new GraphExpansionEngine(
                                (context, config, settings, seeds) -> Mono.empty()));

        var result =
                channel.retrieve(
                                CONTEXT,
                                CONFIG,
                                ENABLED_SETTINGS.withEnabled(false),
                                List.of(seed("1")))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.enabled()).isFalse();
        assertThat(result.graphItems()).isEmpty();
    }

    @Test
    void noSeedsReturnsEmpty() {
        var channel =
                new DefaultGraphItemChannel(
                        new GraphExpansionEngine(
                                (context, config, settings, seeds) -> Mono.empty()));

        var result = channel.retrieve(CONTEXT, CONFIG, ENABLED_SETTINGS, List.of()).block();

        assertThat(result).isNotNull();
        assertThat(result.enabled()).isTrue();
        assertThat(result.graphItems()).isEmpty();
    }

    @Test
    void returnsGraphOnlyCandidatesAndExcludesSeeds() {
        var assistant =
                (RetrievalGraphAssistant)
                        (context, config, settings, seeds) ->
                                Mono.just(
                                        new RetrievalGraphAssistResult(
                                                List.of(seed("1"), graph("2")),
                                                new RetrievalGraphAssistResult.GraphAssistStats(
                                                        true, false, false, 1, 1, 0, 1, 1, 0, 0,
                                                        0)));
        var channel = new DefaultGraphItemChannel(new GraphExpansionEngine(assistant));

        var result =
                channel.retrieve(CONTEXT, CONFIG, ENABLED_SETTINGS, List.of(seed("1"))).block();

        assertThat(result).isNotNull();
        assertThat(result.graphItems()).extracting(ScoredResult::sourceId).containsExactly("2");
        assertThat(result.seedCount()).isEqualTo(1);
        assertThat(result.rawCandidateCount()).isEqualTo(1);
    }

    @Test
    void assistantFailureReturnsDegradedEmptyResult() {
        var assistant =
                (RetrievalGraphAssistant)
                        (context, config, settings, seeds) ->
                                Mono.error(new IllegalStateException("boom"));
        var channel = new DefaultGraphItemChannel(new GraphExpansionEngine(assistant));

        var result =
                channel.retrieve(CONTEXT, CONFIG, ENABLED_SETTINGS, List.of(seed("1"))).block();

        assertThat(result).isNotNull();
        assertThat(result.degraded()).isTrue();
        assertThat(result.graphItems()).isEmpty();
    }

    private static ScoredResult seed(String id) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, id, "seed " + id, 0.9f, 0.9d);
    }

    private static ScoredResult graph(String id) {
        return new ScoredResult(ScoredResult.SourceType.ITEM, id, "graph " + id, 0.0f, 0.5d);
    }
}
