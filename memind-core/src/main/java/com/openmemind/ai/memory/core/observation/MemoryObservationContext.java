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
package com.openmemind.ai.memory.core.observation;

import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceRecorder;
import io.micrometer.observation.Observation;
import java.util.Optional;
import reactor.util.context.ContextView;

/** Base observation context with request-scoped memory observability state. */
public abstract class MemoryObservationContext extends Observation.Context {

    protected MemoryObservationContext() {}

    protected MemoryObservationContext(ContextView reactorContext) {
        if (reactorContext != null) {
            reactorContext
                    .<RetrievalTraceRecorder>getOrEmpty(RetrievalTraceRecorder.class)
                    .ifPresent(recorder -> put(RetrievalTraceRecorder.class, recorder));
        }
    }

    public Optional<RetrievalTraceRecorder> traceRecorder() {
        return Optional.ofNullable(get(RetrievalTraceRecorder.class));
    }

    public String status() {
        return getError() == null ? "success" : "error";
    }
}
