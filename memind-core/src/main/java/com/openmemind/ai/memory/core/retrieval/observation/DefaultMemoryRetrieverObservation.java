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
package com.openmemind.ai.memory.core.retrieval.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.trace.ObservationTiming;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceEvent;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceEventSource;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Observation contracts for DefaultMemoryRetriever. */
public final class DefaultMemoryRetrieverObservation {

    private DefaultMemoryRetrieverObservation() {}

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<RetrievalResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<RetrievalObservationContext, Mono<RetrievalResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                RetrievalDocument.RETRIEVAL,
                RetrievalObservationConvention.INSTANCE,
                reactorContext -> new RetrievalObservationContext(memoryId, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum RetrievalDocument implements ObservationDocumentation {
        RETRIEVAL;

        @Override
        public String getName() {
            return "memind.retrieval";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return RetrievalObservationConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        MEMORY_ID {
            @Override
            public String asString() {
                return "memind.memory_id";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        };
    }

    public static final class RetrievalObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final MemoryId memoryId;
        private String strategyName = "unknown";
        private int itemCount;
        private int insightCount;
        private int rawDataCount;
        private int evidenceCount;
        private String resultStatus = "unknown";

        public RetrievalObservationContext(MemoryId memoryId) {
            this(memoryId, null);
        }

        public RetrievalObservationContext(MemoryId memoryId, ContextView reactorContext) {
            super(reactorContext);
            this.memoryId = memoryId;
        }

        public void recordResult(RetrievalResult result) {
            strategyName =
                    result == null || result.strategy() == null ? "unknown" : result.strategy();
            itemCount = result == null || result.items() == null ? 0 : result.items().size();
            insightCount =
                    result == null || result.insights() == null ? 0 : result.insights().size();
            rawDataCount = result == null || result.rawData() == null ? 0 : result.rawData().size();
            evidenceCount =
                    result == null || result.evidences() == null ? 0 : result.evidences().size();
            resultStatus =
                    result == null || result.status() == null
                            ? "unknown"
                            : result.status().name().toLowerCase();
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(itemCount)));
        }

        @Override
        public String status() {
            String terminalStatus = errorOrCancellationStatus();
            return terminalStatus == null ? resultStatus : terminalStatus;
        }

        public String strategyName() {
            return strategyName;
        }

        public int itemCount() {
            return itemCount;
        }

        public int insightCount() {
            return insightCount;
        }

        public int rawDataCount() {
            return rawDataCount;
        }

        public int evidenceCount() {
            return evidenceCount;
        }

        public String source() {
            return "core";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            RetrievalDocument.RETRIEVAL.getName(),
                            RetrievalDocument.RETRIEVAL.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "strategy", strategyName),
                            Map.of(
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(itemCount)),
                            new RetrievalTraceEvent.FinalPayload(
                                    strategyName,
                                    itemCount,
                                    insightCount,
                                    rawDataCount,
                                    evidenceCount)));
        }
    }

    public static final class RetrievalObservationConvention
            implements ObservationConvention<RetrievalObservationContext> {

        public static final RetrievalObservationConvention INSTANCE =
                new RetrievalObservationConvention();

        @Override
        public String getName() {
            return RetrievalDocument.RETRIEVAL.getName();
        }

        @Override
        public String getContextualName(RetrievalObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RetrievalObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RetrievalObservationContext;
        }
    }
}
