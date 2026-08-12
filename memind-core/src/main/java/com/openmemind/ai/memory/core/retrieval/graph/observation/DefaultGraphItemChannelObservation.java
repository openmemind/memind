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
package com.openmemind.ai.memory.core.retrieval.graph.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.graph.GraphExpansionResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
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

/** Observation contracts for DefaultGraphItemChannel. */
public final class DefaultGraphItemChannelObservation {

    private DefaultGraphItemChannelObservation() {}

    public static Mono<GraphExpansionResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds,
            Supplier<Mono<GraphExpansionResult>> operation) {
        return observe(
                observationRegistry, queryContext, settings, seeds, ignored -> operation.get());
    }

    public static Mono<GraphExpansionResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds,
            Function<GraphItemChannelObservationContext, Mono<GraphExpansionResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                GraphItemChannelDocument.RETRIEVE,
                GraphItemChannelConvention.INSTANCE,
                reactorContext ->
                        new GraphItemChannelObservationContext(
                                queryContext, settings, seeds, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum GraphItemChannelDocument implements ObservationDocumentation {
        RETRIEVE;

        @Override
        public String getName() {
            return "memind.retrieval.channel.graph";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return GraphItemChannelConvention.class;
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
        GRAPH_ENABLED {
            @Override
            public String asString() {
                return "memind.retrieval.graph.enabled";
            }
        },
        GRAPH_SEED_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.seed_count";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        },
        GRAPH_LINK_EXPANSION_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.link_expansion_count";
            }
        },
        GRAPH_ENTITY_EXPANSION_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.entity_expansion_count";
            }
        },
        GRAPH_DEDUPED_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.deduped_candidate_count";
            }
        },
        GRAPH_OVERLAP_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.overlap_count";
            }
        },
        GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.skipped_overfanout_entity_count";
            }
        },
        GRAPH_TIMEOUT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.timeout";
            }
        },
        GRAPH_DEGRADED {
            @Override
            public String asString() {
                return "memind.retrieval.graph.degraded";
            }
        };
    }

    public static final class GraphItemChannelObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final RetrievalGraphSettings settings;
        private final int seedCount;
        private GraphExpansionResult result;

        public GraphItemChannelObservationContext(
                QueryContext queryContext,
                RetrievalGraphSettings settings,
                List<ScoredResult> seeds) {
            this(queryContext, settings, seeds, null);
        }

        public GraphItemChannelObservationContext(
                QueryContext queryContext,
                RetrievalGraphSettings settings,
                List<ScoredResult> seeds,
                ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.settings = settings;
            this.seedCount = seeds == null ? 0 : seeds.size();
        }

        public void recordResult(GraphExpansionResult result) {
            this.result = result;
            add(HighCardinalityKeyNames.RESULT_COUNT, resultCount(result));
            add(
                    HighCardinalityKeyNames.GRAPH_LINK_EXPANSION_COUNT,
                    result == null ? 0 : result.linkExpansionCount());
            add(
                    HighCardinalityKeyNames.GRAPH_ENTITY_EXPANSION_COUNT,
                    result == null ? 0 : result.entityExpansionCount());
            add(
                    HighCardinalityKeyNames.GRAPH_DEDUPED_CANDIDATE_COUNT,
                    result == null ? 0 : result.dedupedCandidateCount());
            add(
                    HighCardinalityKeyNames.GRAPH_OVERLAP_COUNT,
                    result == null ? 0 : result.overlapCount());
            add(
                    HighCardinalityKeyNames.GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT,
                    result == null ? 0 : result.skippedOverFanoutEntityCount());
            add(HighCardinalityKeyNames.GRAPH_TIMEOUT, result != null && result.timedOut());
            add(HighCardinalityKeyNames.GRAPH_DEGRADED, result != null && result.degraded());
        }

        private int resultCount(GraphExpansionResult result) {
            return result == null || result.graphItems() == null ? 0 : result.graphItems().size();
        }

        public int resultCount() {
            return resultCount(result);
        }

        public int candidateCount() {
            return result == null ? 0 : result.dedupedCandidateCount();
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
            return "graph";
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
            List<ScoredResult> results = result == null ? List.of() : result.graphItems();
            return Optional.of(
                    new RetrievalTraceEvent(
                            GraphItemChannelDocument.RETRIEVE.getName(),
                            GraphItemChannelDocument.RETRIEVE.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "channel"),
                            Map.of(
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(resultCount()),
                                    HighCardinalityKeyNames.GRAPH_DEDUPED_CANDIDATE_COUNT
                                            .asString(),
                                    String.valueOf(candidateCount())),
                            new RetrievalTraceEvent.StagePayload(
                                    "channel",
                                    "item",
                                    "graph",
                                    seedCount,
                                    candidateCount(),
                                    resultCount(),
                                    degraded(),
                                    skipped(),
                                    attributes(),
                                    RetrievalTraceEvent.candidates(
                                            results,
                                            options.maxCandidatesPerStage(),
                                            options.maxTextLength()))));
        }

        private Map<String, Object> attributes() {
            if (result == null) {
                return Map.of();
            }
            return Map.of(
                    "linkExpansionCount",
                    result.linkExpansionCount(),
                    "entityExpansionCount",
                    result.entityExpansionCount(),
                    "overlapCount",
                    result.overlapCount(),
                    "timedOut",
                    result.timedOut());
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class GraphItemChannelConvention
            implements ObservationConvention<GraphItemChannelObservationContext> {

        public static final GraphItemChannelConvention INSTANCE = new GraphItemChannelConvention();

        @Override
        public String getName() {
            return GraphItemChannelDocument.RETRIEVE.getName();
        }

        @Override
        public String getContextualName(GraphItemChannelObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(GraphItemChannelObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.CHANNEL.withValue("graph"),
                    HighCardinalityKeyNames.GRAPH_ENABLED.withValue(
                            String.valueOf(context.settings != null && context.settings.enabled())),
                    HighCardinalityKeyNames.GRAPH_SEED_COUNT.withValue(
                            String.valueOf(context.seedCount)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof GraphItemChannelObservationContext;
        }
    }
}
