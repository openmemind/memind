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
 * Implemented by observation contexts that can expose retrieval debug details.
 *
 * <p>The ObservationHandler does not inspect component-specific fields directly. Instead, each
 * context decides whether it has enough data to emit a trace event and how much detail to include
 * based on the active {@link RetrievalTraceOptions}.
 */
public interface RetrievalTraceEventSource {

    Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
            ObservationTiming timing, RetrievalTraceOptions options);
}
