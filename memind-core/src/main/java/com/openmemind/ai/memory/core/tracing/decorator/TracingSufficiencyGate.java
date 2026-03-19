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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_SUFFICIENT;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link SufficiencyGate}.
 */
public class TracingSufficiencyGate extends TracingSupport implements SufficiencyGate {

    private final SufficiencyGate delegate;

    public TracingSufficiencyGate(SufficiencyGate delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = delegate;
    }

    @Override
    public Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results) {
        return trace(
                MemorySpanNames.RETRIEVAL_SUFFICIENCY,
                Map.of(),
                r -> Map.of(RETRIEVAL_SUFFICIENT, r.sufficient()),
                () -> delegate.check(context, results));
    }
}
