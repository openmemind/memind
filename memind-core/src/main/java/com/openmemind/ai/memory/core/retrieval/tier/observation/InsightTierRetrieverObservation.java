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
package com.openmemind.ai.memory.core.retrieval.tier.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
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

/** Observation contracts for InsightTierRetriever. */
public final class InsightTierRetrieverObservation {

    private InsightTierRetrieverObservation() {}

    public static Mono<TierResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalConfig config,
            Supplier<Mono<TierResult>> operation) {
        return observe(observationRegistry, queryContext, config, ignored -> operation.get());
    }

    public static Mono<TierResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalConfig config,
            Function<InsightTierObservationContext, Mono<TierResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                InsightTierDocument.RETRIEVE,
                InsightTierConvention.INSTANCE,
                reactorContext ->
                        new InsightTierObservationContext(queryContext, config, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum InsightTierDocument implements ObservationDocumentation {
        RETRIEVE;

        @Override
        public String getName() {
            return "memind.retrieval.tier.insight";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return InsightTierConvention.class;
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
        },
        TIER_NAME {
            @Override
            public String asString() {
                return "memind.retrieval.tier";
            }
        },
        TOP_K {
            @Override
            public String asString() {
                return "memind.retrieval.top_k";
            }
        };
    }

    public static final class InsightTierObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final RetrievalConfig config;
        private List<ScoredResult> results = List.of();
        private int resultCount;

        public InsightTierObservationContext(QueryContext queryContext, RetrievalConfig config) {
            this(queryContext, config, null);
        }

        public InsightTierObservationContext(
                QueryContext queryContext, RetrievalConfig config, ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.config = config;
        }

        public void recordResult(TierResult result) {
            results = result == null || result.results() == null ? List.of() : result.results();
            resultCount = results.size();
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(resultCount)));
        }

        public int resultCount() {
            return resultCount;
        }

        public String strategyName() {
            return null;
        }

        public String stage() {
            return "tier";
        }

        public String tier() {
            return "insight";
        }

        public String method() {
            return "vector";
        }

        public String source() {
            return "core";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            InsightTierDocument.RETRIEVE.getName(),
                            InsightTierDocument.RETRIEVE.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "tier"),
                            Map.of(
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(resultCount)),
                            new RetrievalTraceEvent.StagePayload(
                                    "tier",
                                    "insight",
                                    "vector",
                                    null,
                                    null,
                                    resultCount,
                                    false,
                                    false,
                                    Map.of(),
                                    RetrievalTraceEvent.candidates(
                                            results,
                                            options.maxCandidatesPerStage(),
                                            options.maxTextLength()))));
        }
    }

    public static final class InsightTierConvention
            implements ObservationConvention<InsightTierObservationContext> {

        public static final InsightTierConvention INSTANCE = new InsightTierConvention();

        @Override
        public String getName() {
            return InsightTierDocument.RETRIEVE.getName();
        }

        @Override
        public String getContextualName(InsightTierObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(InsightTierObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.TIER_NAME.withValue("insight"),
                    HighCardinalityKeyNames.TOP_K.withValue(
                            String.valueOf(context.config.tier1().topK())));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof InsightTierObservationContext;
        }
    }
}
