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

import java.util.Optional;

/**
 * Collects normalized retrieval trace events for one retrieval execution.
 *
 * <p>Implementations should be cheap when disabled and bounded when enabled. Production code
 * usually reads the recorder from {@link RetrievalTraceContext}; tests can inject their own
 * recorder through Reactor Context to assert what observations emitted.
 */
public interface RetrievalTraceRecorder {

    void record(RetrievalTraceEvent event);

    /** Returns the current response snapshot, or empty when the recorder intentionally captures nothing. */
    Optional<RetrievalDebugTrace> snapshot();

    /** Limits applied by event sources before they attach candidate previews or long text. */
    default RetrievalTraceOptions options() {
        return RetrievalTraceOptions.defaults();
    }
}
