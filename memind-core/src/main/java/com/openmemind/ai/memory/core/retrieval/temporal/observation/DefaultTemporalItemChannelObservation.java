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
package com.openmemind.ai.memory.core.retrieval.temporal.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraint;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelSettings;
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

/** Observation contracts for DefaultTemporalItemChannel. */
public final class DefaultTemporalItemChannelObservation {

    private DefaultTemporalItemChannelObservation() {}

    public static Mono<TemporalItemChannelResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            Optional<TemporalConstraint> temporalConstraint,
            TemporalItemChannelSettings settings,
            Supplier<Mono<TemporalItemChannelResult>> operation) {
        return observe(
                observationRegistry,
                queryContext,
                temporalConstraint,
                settings,
                ignored -> operation.get());
    }

    public static Mono<TemporalItemChannelResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            Optional<TemporalConstraint> temporalConstraint,
            TemporalItemChannelSettings settings,
            Function<TemporalItemChannelObservationContext, Mono<TemporalItemChannelResult>>
                    operation) {
        return MemoryObservation.mono(
                observationRegistry,
                TemporalItemChannelDocument.RETRIEVE,
                TemporalItemChannelConvention.INSTANCE,
                reactorContext ->
                        new TemporalItemChannelObservationContext(
                                queryContext, temporalConstraint, settings, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum TemporalItemChannelDocument implements ObservationDocumentation {
        RETRIEVE;

        @Override
        public String getName() {
            return "memind.retrieval.channel.temporal";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return TemporalItemChannelConvention.class;
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
        CHANNEL {
            @Override
            public String asString() {
                return "memind.retrieval.channel";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        },
        CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.candidate_count";
            }
        },
        TEMPORAL_ENABLED {
            @Override
            public String asString() {
                return "memind.retrieval.temporal.enabled";
            }
        },
        TEMPORAL_CONSTRAINT_PRESENT {
            @Override
            public String asString() {
                return "memind.retrieval.temporal.constraint_present";
            }
        },
        TEMPORAL_DEGRADED {
            @Override
            public String asString() {
                return "memind.retrieval.temporal.degraded";
            }
        };
    }

    public static final class TemporalItemChannelObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final Optional<TemporalConstraint> temporalConstraint;
        private final TemporalItemChannelSettings settings;
        private TemporalItemChannelResult result;

        public TemporalItemChannelObservationContext(
                QueryContext queryContext,
                Optional<TemporalConstraint> temporalConstraint,
                TemporalItemChannelSettings settings) {
            this(queryContext, temporalConstraint, settings, null);
        }

        public TemporalItemChannelObservationContext(
                QueryContext queryContext,
                Optional<TemporalConstraint> temporalConstraint,
                TemporalItemChannelSettings settings,
                ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.temporalConstraint = temporalConstraint;
            this.settings = settings;
        }

        public void recordResult(TemporalItemChannelResult result) {
            this.result = result;
            add(HighCardinalityKeyNames.RESULT_COUNT, resultCount(result));
            add(
                    HighCardinalityKeyNames.CANDIDATE_COUNT,
                    result == null ? 0 : result.candidateCount());
            add(HighCardinalityKeyNames.TEMPORAL_ENABLED, result != null && result.enabled());
            add(
                    HighCardinalityKeyNames.TEMPORAL_CONSTRAINT_PRESENT,
                    result != null && result.constraintPresent());
            add(HighCardinalityKeyNames.TEMPORAL_DEGRADED, result != null && result.degraded());
        }

        private int resultCount(TemporalItemChannelResult result) {
            return result == null || result.items() == null ? 0 : result.items().size();
        }

        public int resultCount() {
            return resultCount(result);
        }

        public int candidateCount() {
            return result == null ? 0 : result.candidateCount();
        }

        public boolean degraded() {
            return result != null && result.degraded();
        }

        public boolean skipped() {
            return result == null || !result.enabled();
        }

        public String strategyName() {
            return null;
        }

        public String stage() {
            return "channel";
        }

        public String tier() {
            return "item";
        }

        public String method() {
            return "temporal";
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
            List<ScoredResult> results = result == null ? List.of() : result.items();
            return Optional.of(
                    new RetrievalTraceEvent(
                            TemporalItemChannelDocument.RETRIEVE.getName(),
                            TemporalItemChannelDocument.RETRIEVE.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "channel"),
                            Map.of(
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(resultCount()),
                                    HighCardinalityKeyNames.CANDIDATE_COUNT.asString(),
                                    String.valueOf(candidateCount())),
                            new RetrievalTraceEvent.StagePayload(
                                    "channel",
                                    "item",
                                    "temporal",
                                    null,
                                    candidateCount(),
                                    resultCount(),
                                    degraded(),
                                    skipped(),
                                    result == null
                                            ? Map.of()
                                            : Map.of(
                                                    "constraintPresent",
                                                    result.constraintPresent()),
                                    RetrievalTraceEvent.candidates(
                                            results,
                                            options.maxCandidatesPerStage(),
                                            options.maxTextLength()))));
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class TemporalItemChannelConvention
            implements ObservationConvention<TemporalItemChannelObservationContext> {

        public static final TemporalItemChannelConvention INSTANCE =
                new TemporalItemChannelConvention();

        @Override
        public String getName() {
            return TemporalItemChannelDocument.RETRIEVE.getName();
        }

        @Override
        public String getContextualName(TemporalItemChannelObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(
                TemporalItemChannelObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.CHANNEL.withValue("temporal"),
                    HighCardinalityKeyNames.TEMPORAL_ENABLED.withValue(
                            String.valueOf(context.settings != null && context.settings.enabled())),
                    HighCardinalityKeyNames.TEMPORAL_CONSTRAINT_PRESENT.withValue(
                            String.valueOf(
                                    context.temporalConstraint != null
                                            && context.temporalConstraint.isPresent())));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TemporalItemChannelObservationContext;
        }
    }
}
