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
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TIER_NAME;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TOP_K;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_KEYWORD_SEARCH;
import static com.openmemind.ai.memory.core.tracing.MemorySpanNames.RETRIEVAL_VECTOR_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierSearch;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TracingItemTierRetrieverTest {

    @Test
    void searchByVectorPublishesVectorSpanAndPropagatesResults() {
        var observer = new RecordingMemoryObserver();
        ItemTierSearch delegate = mock(ItemTierSearch.class);
        var context = queryContext();
        var tier = RetrievalConfig.TierConfig.enabled(5);
        var scoring = ScoringConfig.defaults();
        var result = scoredResult();
        when(delegate.searchByVector(context, tier, scoring))
                .thenReturn(Mono.just(List.of(result)));

        var traced = new TracingItemTierRetriever(delegate, observer);

        StepVerifier.create(traced.searchByVector(context, tier, scoring))
                .expectNext(List.of(result))
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(RETRIEVAL_VECTOR_SEARCH);
        assertThat(observer.monoContexts().getFirst().requestAttributes())
                .containsEntry(MEMORY_ID, "memory")
                .containsEntry(RETRIEVAL_TIER_NAME, "item")
                .containsEntry(RETRIEVAL_TOP_K, 5);
        assertThat(resultAttributes(observer.monoContexts().getFirst(), List.of(result)))
                .containsEntry(RETRIEVAL_RESULT_COUNT, 1);
    }

    @Test
    void searchByKeywordPublishesKeywordSpanAndPropagatesResults() {
        var observer = new RecordingMemoryObserver();
        ItemTierSearch delegate = mock(ItemTierSearch.class);
        var context = queryContext();
        var tier = RetrievalConfig.TierConfig.enabled(3);
        var scoring = ScoringConfig.defaults();
        var result = scoredResult();
        when(delegate.searchByKeyword(context, tier, scoring))
                .thenReturn(Mono.just(List.of(result)));

        var traced = new TracingItemTierRetriever(delegate, observer);

        StepVerifier.create(traced.searchByKeyword(context, tier, scoring))
                .expectNext(List.of(result))
                .verifyComplete();

        assertThat(observer.monoContexts()).hasSize(1);
        assertThat(observer.monoContexts().getFirst().spanName())
                .isEqualTo(RETRIEVAL_KEYWORD_SEARCH);
        assertThat(resultAttributes(observer.monoContexts().getFirst(), List.of(result)))
                .containsEntry(RETRIEVAL_RESULT_COUNT, 1);
    }

    private QueryContext queryContext() {
        return new QueryContext(
                DefaultMemoryId.of("memory", null), "query", null, List.of(), Map.of(), null, null);
    }

    private ScoredResult scoredResult() {
        return new ScoredResult(ScoredResult.SourceType.ITEM, "item-1", "text", 0.9f, 0.8d);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultAttributes(
            ObservationContext<?> context, List<ScoredResult> result) {
        return ((ObservationContext<List<ScoredResult>>) context).resultExtractor().extract(result);
    }
}
