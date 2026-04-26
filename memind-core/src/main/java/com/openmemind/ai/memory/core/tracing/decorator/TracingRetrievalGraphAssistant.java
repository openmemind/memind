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

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Tracing decorator for retrieval graph assist.
 */
public final class TracingRetrievalGraphAssistant extends TracingSupport
        implements RetrievalGraphAssistant {

    private final RetrievalGraphAssistant delegate;

    public TracingRetrievalGraphAssistant(
            RetrievalGraphAssistant delegate, MemoryObserver observer) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<RetrievalGraphAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems) {
        return trace(
                MemorySpanNames.RETRIEVAL_GRAPH_ASSIST,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_GRAPH_ENABLED,
                        graphSettings != null && graphSettings.enabled()),
                result ->
                        Map.of(
                                MemoryAttributes.RETRIEVAL_GRAPH_SEED_COUNT,
                                result.stats().seedCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_LINK_EXPANSION_COUNT,
                                result.stats().linkExpansionCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_ENTITY_EXPANSION_COUNT,
                                result.stats().entityExpansionCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_DEDUPED_CANDIDATE_COUNT,
                                result.stats().dedupedCandidateCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_ADMITTED_CANDIDATE_COUNT,
                                result.stats().admittedGraphCandidateCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_DISPLACED_DIRECT_COUNT,
                                result.stats().displacedDirectCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_OVERLAP_COUNT,
                                result.stats().overlapCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT,
                                result.stats().skippedOverFanoutEntityCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_TIMEOUT,
                                result.stats().timedOut(),
                                MemoryAttributes.RETRIEVAL_GRAPH_DEGRADED,
                                result.stats().degraded()),
                () -> delegate.assist(context, config, graphSettings, directItems));
    }
}
