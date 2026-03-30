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
package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryRewriter;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("DefaultMemoryRetriever Unit Test")
class DefaultMemoryRetrieverTest {

    private final com.openmemind.ai.memory.core.data.MemoryId memoryId = TestMemoryIds.userAgent();

    @Test
    @DisplayName("Should fail fast when requested retrieval strategy is not registered")
    void shouldFailFastWhenStrategyIsMissing() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());

        var retriever = new DefaultMemoryRetriever(cache, store);
        var request = RetrievalRequest.of(memoryId, "query", RetrievalConfig.Strategy.SIMPLE);

        assertThatThrownBy(() -> retriever.retrieve(request).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simple")
                .hasMessageContaining("No retrieval strategy registered");
    }

    @Test
    @DisplayName("cache hit short-circuits strategy dispatch and query rewrite")
    void cacheHitShortCircuitsStrategyAndQueryRewrite() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        var rewriter = mock(QueryRewriter.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        var cached = RetrievalResult.empty("simple", "query");
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.of(cached));

        var retriever = new DefaultMemoryRetriever(cache, store, null, rewriter);
        retriever.registerStrategy(
                new RetrievalStrategy() {
                    @Override
                    public String name() {
                        return "simple";
                    }

                    @Override
                    public Mono<RetrievalResult> retrieve(
                            QueryContext context, RetrievalConfig config) {
                        return Mono.error(new AssertionError("should not run"));
                    }
                });

        var result =
                retriever
                        .retrieve(
                                new RetrievalRequest(
                                        memoryId,
                                        "query",
                                        List.of("history"),
                                        RetrievalConfig.simple(),
                                        Map.of(),
                                        null,
                                        null))
                        .block();

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(rewriter);
    }

    @Test
    @DisplayName("query rewrite failure falls back to original query")
    void queryRewriteFailureFallsBackToOriginalQuery() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var itemOperations = mock(ItemOperations.class);
        var rewriter = mock(QueryRewriter.class);
        var strategy = mock(RetrievalStrategy.class);
        when(store.itemOperations()).thenReturn(itemOperations);
        when(itemOperations.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());
        when(rewriter.rewrite(memoryId, "query", List.of("history")))
                .thenReturn(Mono.error(new RuntimeException("boom")));
        when(strategy.name()).thenReturn("simple");
        when(strategy.retrieve(any(), any()))
                .thenReturn(Mono.just(RetrievalResult.empty("simple", "query")));

        var retriever = new DefaultMemoryRetriever(cache, store, null, rewriter);
        retriever.registerStrategy(strategy);

        StepVerifier.create(
                        retriever.retrieve(
                                new RetrievalRequest(
                                        memoryId,
                                        "query",
                                        List.of("history"),
                                        RetrievalConfig.simple(),
                                        Map.of(),
                                        null,
                                        null)))
                .assertNext(result -> assertThat(result.query()).isEqualTo("query"))
                .verifyComplete();

        verify(strategy)
                .retrieve(
                        argThat(
                                context ->
                                        context.rewrittenQuery() == null
                                                && context.originalQuery().equals("query")),
                        any());
    }

    @Test
    @DisplayName("onDataChanged should tolerate strategy registration during iteration")
    void onDataChangedShouldTolerateStrategyRegistrationDuringIteration() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var textSearch = mock(MemoryTextSearch.class);
        var retriever = new DefaultMemoryRetriever(cache, store, textSearch);

        RetrievalStrategy strategyB =
                new RetrievalStrategy() {
                    @Override
                    public String name() {
                        return "strategy-b";
                    }

                    @Override
                    public Mono<RetrievalResult> retrieve(
                            QueryContext context, RetrievalConfig config) {
                        return Mono.just(RetrievalResult.empty(name(), context.searchQuery()));
                    }
                };

        RetrievalStrategy strategyA =
                new RetrievalStrategy() {
                    @Override
                    public String name() {
                        return "strategy-a";
                    }

                    @Override
                    public Mono<RetrievalResult> retrieve(
                            QueryContext context, RetrievalConfig config) {
                        return Mono.just(RetrievalResult.empty(name(), context.searchQuery()));
                    }

                    @Override
                    public void onDataChanged(
                            com.openmemind.ai.memory.core.data.MemoryId changedMemoryId) {
                        retriever.registerStrategy(strategyB);
                    }
                };

        retriever.registerStrategy(strategyA);

        assertThatCode(() -> retriever.onDataChanged(memoryId)).doesNotThrowAnyException();

        verify(cache).invalidate(memoryId);
        verify(textSearch).invalidate(memoryId);
    }
}
