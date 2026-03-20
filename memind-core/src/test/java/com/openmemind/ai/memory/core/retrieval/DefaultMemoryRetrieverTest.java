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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@DisplayName("DefaultMemoryRetriever Unit Test")
class DefaultMemoryRetrieverTest {

    private final MemoryId memoryId = DefaultMemoryId.of("user1", "agent1");

    @Test
    @DisplayName("Should fail fast when requested retrieval strategy is not registered")
    void shouldFailFastWhenStrategyIsMissing() {
        var cache = mock(RetrievalCache.class);
        var store = mock(MemoryStore.class);
        var retriever = new DefaultMemoryRetriever(cache, store);
        var request = RetrievalRequest.of(memoryId, "query", RetrievalConfig.Strategy.SIMPLE);

        when(store.hasItems(memoryId)).thenReturn(true);
        when(cache.get(eq(memoryId), anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retriever.retrieve(request).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simple")
                .hasMessageContaining("No retrieval strategy registered");
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
                    public void onDataChanged(MemoryId changedMemoryId) {
                        retriever.registerStrategy(strategyB);
                    }
                };

        retriever.registerStrategy(strategyA);

        assertThatCode(() -> retriever.onDataChanged(memoryId)).doesNotThrowAnyException();

        verify(cache).invalidate(memoryId);
        verify(textSearch).invalidate(memoryId);
    }
}
