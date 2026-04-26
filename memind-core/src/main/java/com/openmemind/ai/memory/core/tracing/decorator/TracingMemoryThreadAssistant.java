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
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.RetrievalMemoryThreadSettings;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Tracing decorator for retrieval memory-thread assist.
 */
public final class TracingMemoryThreadAssistant extends TracingSupport
        implements MemoryThreadAssistant {

    private final MemoryThreadAssistant delegate;

    public TracingMemoryThreadAssistant(MemoryThreadAssistant delegate, MemoryObserver observer) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<MemoryThreadAssistResult> assist(
            QueryContext context,
            RetrievalConfig config,
            RetrievalMemoryThreadSettings settings,
            List<ScoredResult> directWindow) {
        return trace(
                MemorySpanNames.RETRIEVAL_MEMORY_THREAD_ASSIST,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_MEMORY_THREAD_ENABLED,
                        settings != null && settings.enabled()),
                result ->
                        Map.of(
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_SEED_THREAD_COUNT,
                                result.stats().seedThreadCount(),
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_CANDIDATE_COUNT,
                                result.stats().candidateCount(),
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_ADMITTED_COUNT,
                                result.stats().admittedMemberCount(),
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_CLAMPED,
                                result.stats().clamped(),
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_DEGRADED,
                                result.stats().degraded(),
                                MemoryAttributes.RETRIEVAL_MEMORY_THREAD_TIMEOUT,
                                result.stats().timedOut()),
                () -> delegate.assist(context, config, settings, directWindow));
    }
}
