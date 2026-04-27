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
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraint;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelSettings;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Tracing decorator for temporal item channel retrieval. */
public final class TracingTemporalItemChannel extends TracingSupport
        implements TemporalItemChannel {

    private final TemporalItemChannel delegate;

    public TracingTemporalItemChannel(TemporalItemChannel delegate, MemoryObserver observer) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<TemporalItemChannelResult> retrieve(
            QueryContext context,
            RetrievalConfig config,
            Optional<TemporalConstraint> temporalConstraint,
            TemporalItemChannelSettings settings) {
        return trace(
                MemorySpanNames.RETRIEVAL_TEMPORAL_CHANNEL,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_CHANNEL,
                        "temporal",
                        MemoryAttributes.RETRIEVAL_TEMPORAL_ENABLED,
                        settings != null && settings.enabled(),
                        MemoryAttributes.RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT,
                        temporalConstraint != null && temporalConstraint.isPresent()),
                result ->
                        Map.of(
                                MemoryAttributes.RETRIEVAL_RESULT_COUNT,
                                result.items().size(),
                                MemoryAttributes.RETRIEVAL_CANDIDATE_COUNT,
                                result.candidateCount(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_ENABLED,
                                result.enabled(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT,
                                result.constraintPresent(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_DEGRADED,
                                result.degraded()),
                () -> delegate.retrieve(context, config, temporalConstraint, settings));
    }
}
