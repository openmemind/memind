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

import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * Reactor Context access point for the request-scoped retrieval trace recorder.
 *
 * <p>The recorder is deliberately not stored in ObservationRegistry because it is mutable and
 * belongs to one retrieve call. Observation contexts copy it from Reactor Context when each
 * instrumented operation starts, so the ObservationHandler can find it later on stop.
 */
public final class RetrievalTraceContext {

    private static final Class<RetrievalTraceRecorder> KEY = RetrievalTraceRecorder.class;

    private RetrievalTraceContext() {}

    /**
     * Attaches a real recorder to the reactive chain; no-op recorders are not propagated.
     */
    public static Context withRecorder(Context context, RetrievalTraceRecorder recorder) {
        if (recorder == null) {
            return context;
        }
        return context.put(KEY, recorder);
    }

    /**
     * Returns the current request recorder, or the no-op singleton when tracing is disabled.
     */
    public static RetrievalTraceRecorder recorder(ContextView context) {
        if (context == null || !context.hasKey(KEY)) {
            return NoopRetrievalTraceRecorder.INSTANCE;
        }
        return context.get(KEY);
    }
}
