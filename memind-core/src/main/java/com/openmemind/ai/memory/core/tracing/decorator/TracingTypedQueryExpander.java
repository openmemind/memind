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

import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link TypedQueryExpander}.
 */
public class TracingTypedQueryExpander extends TracingSupport implements TypedQueryExpander {

    private final TypedQueryExpander delegate;

    public TracingTypedQueryExpander(TypedQueryExpander delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<List<ExpandedQuery>> expand(
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions) {
        return trace(
                MemorySpanNames.RETRIEVAL_MULTI_QUERY_EXPAND,
                Map.of(),
                () ->
                        delegate.expand(
                                query, gaps, keyInformation, conversationHistory, maxExpansions));
    }
}
