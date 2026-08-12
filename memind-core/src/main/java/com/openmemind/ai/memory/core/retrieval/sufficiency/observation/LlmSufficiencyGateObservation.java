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
package com.openmemind.ai.memory.core.retrieval.sufficiency.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Observation contracts for LlmSufficiencyGate. */
public final class LlmSufficiencyGateObservation {

    private LlmSufficiencyGateObservation() {}

    public static Mono<SufficiencyResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            List<ScoredResult> results,
            Supplier<Mono<SufficiencyResult>> operation) {
        return observe(observationRegistry, queryContext, results, ignored -> operation.get());
    }

    public static Mono<SufficiencyResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            List<ScoredResult> results,
            Function<SufficiencyObservationContext, Mono<SufficiencyResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                SufficiencyDocument.SUFFICIENCY,
                SufficiencyConvention.INSTANCE,
                reactorContext ->
                        new SufficiencyObservationContext(queryContext, results, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum SufficiencyDocument implements ObservationDocumentation {
        SUFFICIENCY;

        @Override
        public String getName() {
            return "memind.retrieval.sufficiency";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return SufficiencyConvention.class;
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
        INPUT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.input_count";
            }
        },
        SUFFICIENT {
            @Override
            public String asString() {
                return "memind.retrieval.sufficient";
            }
        },
        GAP_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.gap_count";
            }
        },
        EVIDENCE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.evidence_count";
            }
        },
        KEY_INFORMATION_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.key_information_count";
            }
        };
    }

    public static final class SufficiencyObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final int inputCount;
        private SufficiencyResult result;

        public SufficiencyObservationContext(
                QueryContext queryContext, List<ScoredResult> results) {
            this(queryContext, results, null);
        }

        public SufficiencyObservationContext(
                QueryContext queryContext, List<ScoredResult> results, ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.inputCount = results == null ? 0 : results.size();
        }

        public void recordResult(SufficiencyResult result) {
            this.result = result;
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.SUFFICIENT.withValue(
                            String.valueOf(result != null && result.sufficient())));
            add(HighCardinalityKeyNames.GAP_COUNT, gapCount());
            add(HighCardinalityKeyNames.EVIDENCE_COUNT, evidenceCount());
            add(HighCardinalityKeyNames.KEY_INFORMATION_COUNT, keyInformationCount());
        }

        public int inputCount() {
            return inputCount;
        }

        public int candidateCount() {
            return inputCount;
        }

        public int resultCount() {
            return evidenceCount();
        }

        public boolean degraded() {
            return result != null && "fallback".equals(result.reasoning());
        }

        public boolean skipped() {
            return false;
        }

        public String stage() {
            return "sufficiency";
        }

        public String tier() {
            return "item";
        }

        public String method() {
            return "llm";
        }

        public String source() {
            return "core";
        }

        @Override
        public String status() {
            String terminalStatus = errorOrCancellationStatus();
            if (terminalStatus != null) {
                return terminalStatus;
            }
            return degraded() ? "degraded" : "success";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            SufficiencyDocument.SUFFICIENCY.getName(),
                            SufficiencyDocument.SUFFICIENCY.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", stage()),
                            Map.of(
                                    HighCardinalityKeyNames.INPUT_COUNT.asString(),
                                    String.valueOf(inputCount),
                                    HighCardinalityKeyNames.SUFFICIENT.asString(),
                                    String.valueOf(sufficient())),
                            new RetrievalTraceEvent.StagePayload(
                                    stage(),
                                    tier(),
                                    method(),
                                    inputCount(),
                                    candidateCount(),
                                    resultCount(),
                                    degraded(),
                                    skipped(),
                                    attributes(),
                                    List.of())));
        }

        private Map<String, Object> attributes() {
            return Map.of(
                    "sufficient",
                    sufficient(),
                    "gapCount",
                    gapCount(),
                    "evidenceCount",
                    evidenceCount(),
                    "keyInformationCount",
                    keyInformationCount());
        }

        private boolean sufficient() {
            return result != null && result.sufficient();
        }

        private int gapCount() {
            return result == null || result.gaps() == null ? 0 : result.gaps().size();
        }

        private int evidenceCount() {
            return result == null || result.evidences() == null ? 0 : result.evidences().size();
        }

        private int keyInformationCount() {
            return result == null || result.keyInformation() == null
                    ? 0
                    : result.keyInformation().size();
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class SufficiencyConvention
            implements ObservationConvention<SufficiencyObservationContext> {

        public static final SufficiencyConvention INSTANCE = new SufficiencyConvention();

        @Override
        public String getName() {
            return SufficiencyDocument.SUFFICIENCY.getName();
        }

        @Override
        public String getContextualName(SufficiencyObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(SufficiencyObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(memoryId(context.queryContext)),
                    HighCardinalityKeyNames.INPUT_COUNT.withValue(
                            String.valueOf(context.inputCount)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof SufficiencyObservationContext;
        }
    }

    private static String memoryId(QueryContext queryContext) {
        return queryContext == null || queryContext.memoryId() == null
                ? "unknown"
                : queryContext.memoryId().toIdentifier();
    }
}
