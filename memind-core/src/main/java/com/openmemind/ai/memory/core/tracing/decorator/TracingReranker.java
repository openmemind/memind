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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_QUERY;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TOP_K;

import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link Reranker}.
 */
public class TracingReranker extends TracingSupport implements Reranker {

    private final Reranker delegate;

    public TracingReranker(Reranker delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<List<ScoredResult>> rerank(String query, List<ScoredResult> results, int topK) {
        return trace(
                MemorySpanNames.RETRIEVAL_RERANK,
                Map.of(
                        RETRIEVAL_QUERY,
                        query,
                        "memind.retrieval.rerank.candidates",
                        results.size(),
                        RETRIEVAL_TOP_K,
                        topK),
                () -> delegate.rerank(query, results, topK));
    }
}
