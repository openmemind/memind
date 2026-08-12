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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

class RetrievalTraceObservationHandlerTest {

    @Test
    void recordsEventFromObservationContextRecorderOnStop() {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new RetrievalTraceObservationHandler());
        var recorder = new BoundedRetrievalTraceRecorder(new RetrievalTraceOptions(8, 4, 16));
        var context = new TestTraceContext(Context.of(RetrievalTraceRecorder.class, recorder));

        Observation.createNotStarted("memind.test", () -> context, registry).observe(() -> {});

        var trace = recorder.snapshot().orElseThrow();
        assertThat(trace.stages()).hasSize(1);
        assertThat(trace.stages().getFirst().stage()).isEqualTo("tier");
        assertThat(trace.stages().getFirst().durationMillis()).isNotNegative();
    }

    @Test
    void ignoresObservationContextsWithoutTraceRecorder() {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new RetrievalTraceObservationHandler());
        var context = new TestTraceContext(Context.empty());

        Observation.createNotStarted("memind.test", () -> context, registry).observe(() -> {});

        assertThat(context.traceRecorder()).isEmpty();
    }

    private static final class TestTraceContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private TestTraceContext(ContextView reactorContext) {
            super(reactorContext);
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            "memind.test",
                            "memind.test",
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "tier"),
                            Map.of("memind.retrieval.result_count", "1"),
                            new RetrievalTraceEvent.StagePayload(
                                    "tier", "item", "vector", null, null, 1, false, false, Map.of(),
                                    List.of())));
        }
    }
}
