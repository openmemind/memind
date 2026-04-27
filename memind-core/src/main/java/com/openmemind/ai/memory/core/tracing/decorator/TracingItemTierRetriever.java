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

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Tracing decorator for item tier retrieval operations. */
public class TracingItemTierRetriever extends TracingSupport implements ItemTierSearch {

    private final ItemTierSearch delegate;

    public TracingItemTierRetriever(ItemTierSearch delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<TierResult> searchByVector(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_VECTOR_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        config.tier2().topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.results().size()),
                () -> delegate.searchByVector(context, config));
    }

    @Override
    public MemoryTextSearch textSearch() {
        return delegate.textSearch();
    }

    @Override
    public Mono<List<ScoredResult>> searchByVector(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_VECTOR_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () -> delegate.searchByVector(context, tier, scoring));
    }

    @Override
    public Mono<List<ScoredResult>> searchByKeyword(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_KEYWORD_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () -> delegate.searchByKeyword(context, tier, scoring));
    }

    @Override
    public Mono<List<ScoredResult>> searchHybrid(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_TIER_ITEM,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () -> delegate.searchHybrid(context, tier, scoring));
    }
}
