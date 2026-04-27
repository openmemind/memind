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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Tracing decorator for insight tier retrieval. */
public class TracingInsightTierRetriever extends TracingSupport implements InsightTierSearch {

    private final InsightTierSearch delegate;

    public TracingInsightTierRetriever(InsightTierSearch delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<TierResult> retrieve(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_TIER_INSIGHT,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "insight",
                        RETRIEVAL_TOP_K,
                        config.tier1().topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.results().size()),
                () -> delegate.retrieve(context, config));
    }

    @Override
    public void invalidateCache(MemoryId memoryId) {
        delegate.invalidateCache(memoryId);
    }
}
