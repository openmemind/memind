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
package com.openmemind.ai.memory.core.retrieval.trace;

import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Duration;
import java.time.Instant;

/**
 * Bridges Micrometer Observation lifecycle callbacks into retrieval debug trace events.
 *
 * <p>The handler is registered globally with the ObservationRegistry, but it only records when the
 * current {@link MemoryObservationContext} carries a request-scoped {@link RetrievalTraceRecorder}.
 * This keeps tracing opt-in per retrieval call while still letting every retrieval Observation use
 * the normal Micrometer tap/handler path.
 */
public final class RetrievalTraceObservationHandler
        implements ObservationHandler<MemoryObservationContext> {

    private static final Object STARTED_AT = new Object();
    private static final Object START_NANOS = new Object();

    @Override
    public void onStart(MemoryObservationContext context) {
        context.put(STARTED_AT, Instant.now());
        context.put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onStop(MemoryObservationContext context) {
        RetrievalTraceRecorder recorder = context.get(RetrievalTraceRecorder.class);
        if (recorder == null || !(context instanceof RetrievalTraceEventSource source)) {
            return;
        }
        // Only contexts that explicitly know how to project themselves into trace data are
        // recorded.
        source.toRetrievalTraceEvent(timing(context), recorder.options())
                .ifPresent(recorder::record);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof MemoryObservationContext;
    }

    private ObservationTiming timing(MemoryObservationContext context) {
        Instant completedAt = Instant.now();
        Instant startedAt = context.get(STARTED_AT);
        Long startNanos = context.get(START_NANOS);
        if (startedAt == null || startNanos == null) {
            return new ObservationTiming(completedAt, completedAt, 0L);
        }
        return new ObservationTiming(
                startedAt,
                completedAt,
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
    }
}
