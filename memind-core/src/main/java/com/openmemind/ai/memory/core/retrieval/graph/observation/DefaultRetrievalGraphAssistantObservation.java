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
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
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

/** Observation contracts for DefaultRetrievalGraphAssistant. */
public final class DefaultRetrievalGraphAssistantObservation {

    private DefaultRetrievalGraphAssistantObservation() {}

    public static Mono<RetrievalGraphAssistResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems,
            Supplier<Mono<RetrievalGraphAssistResult>> operation) {
        return observe(
                observationRegistry,
                queryContext,
                graphSettings,
                directItems,
                ignored -> operation.get());
    }

    public static Mono<RetrievalGraphAssistResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalGraphSettings graphSettings,
            List<ScoredResult> directItems,
            Function<GraphAssistObservationContext, Mono<RetrievalGraphAssistResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                GraphAssistDocument.ASSIST,
                GraphAssistConvention.INSTANCE,
                reactorContext ->
                        new GraphAssistObservationContext(
                                queryContext, graphSettings, directItems, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum GraphAssistDocument implements ObservationDocumentation {
        ASSIST;

        @Override
        public String getName() {
            return "memind.retrieval.graph.assist";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return GraphAssistConvention.class;
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
        GRAPH_ENABLED {
            @Override
            public String asString() {
                return "memind.retrieval.graph.enabled";
            }
        },
        DIRECT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.direct_count";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        },
        GRAPH_SEED_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.seed_count";
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
        GRAPH_ADMITTED_CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.admitted_candidate_count";
            }
        },
        GRAPH_DISPLACED_DIRECT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.graph.displaced_direct_count";
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

    public static final class GraphAssistObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final RetrievalGraphSettings graphSettings;
        private final int directCount;
        private RetrievalGraphAssistResult result;

        public GraphAssistObservationContext(
                QueryContext queryContext, RetrievalGraphSettings graphSettings) {
            this(queryContext, graphSettings, null);
        }

        public GraphAssistObservationContext(
                QueryContext queryContext,
                RetrievalGraphSettings graphSettings,
                List<ScoredResult> directItems) {
            this(queryContext, graphSettings, directItems, null);
        }

        public GraphAssistObservationContext(
                QueryContext queryContext,
                RetrievalGraphSettings graphSettings,
                List<ScoredResult> directItems,
                ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.graphSettings = graphSettings;
            this.directCount = directItems == null ? 0 : directItems.size();
        }

        public void recordResult(RetrievalGraphAssistResult result) {
            this.result = result;
            if (result == null) {
                add(HighCardinalityKeyNames.RESULT_COUNT, 0);
                return;
            }
            var stats = result.stats();
            add(HighCardinalityKeyNames.RESULT_COUNT, resultCount());
            add(HighCardinalityKeyNames.GRAPH_SEED_COUNT, stats.seedCount());
            add(HighCardinalityKeyNames.GRAPH_LINK_EXPANSION_COUNT, stats.linkExpansionCount());
            add(HighCardinalityKeyNames.GRAPH_ENTITY_EXPANSION_COUNT, stats.entityExpansionCount());
            add(
                    HighCardinalityKeyNames.GRAPH_DEDUPED_CANDIDATE_COUNT,
                    stats.dedupedCandidateCount());
            add(
                    HighCardinalityKeyNames.GRAPH_ADMITTED_CANDIDATE_COUNT,
                    stats.admittedGraphCandidateCount());
            add(HighCardinalityKeyNames.GRAPH_DISPLACED_DIRECT_COUNT, stats.displacedDirectCount());
            add(HighCardinalityKeyNames.GRAPH_OVERLAP_COUNT, stats.overlapCount());
            add(
                    HighCardinalityKeyNames.GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT,
                    stats.skippedOverFanoutEntityCount());
            add(HighCardinalityKeyNames.GRAPH_TIMEOUT, stats.timedOut());
            add(HighCardinalityKeyNames.GRAPH_DEGRADED, stats.degraded());
        }

        public int inputCount() {
            return directCount;
        }

        public int candidateCount() {
            return result == null ? 0 : result.stats().dedupedCandidateCount();
        }

        public int resultCount() {
            return result == null || result.items() == null ? 0 : result.items().size();
        }

        public boolean degraded() {
            return result != null && result.stats().degraded();
        }

        public boolean skipped() {
            return result == null || !result.stats().graphEnabled() || directCount == 0;
        }

        public String stage() {
            return "graph_assist";
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
            if (getError() != null) {
                return "error";
            }
            if (degraded()) {
                return "degraded";
            }
            return skipped() ? "skipped" : "success";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            List<ScoredResult> results = result == null ? List.of() : result.items();
            return Optional.of(
                    new RetrievalTraceEvent(
                            GraphAssistDocument.ASSIST.getName(),
                            GraphAssistDocument.ASSIST.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", stage()),
                            Map.of(
                                    HighCardinalityKeyNames.DIRECT_COUNT.asString(),
                                    String.valueOf(directCount),
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(resultCount())),
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
                                    RetrievalTraceEvent.candidates(
                                            results,
                                            options.maxCandidatesPerStage(),
                                            options.maxTextLength()))));
        }

        private Map<String, Object> attributes() {
            if (result == null) {
                return Map.of();
            }
            var stats = result.stats();
            return Map.of(
                    "seedCount",
                    stats.seedCount(),
                    "linkExpansionCount",
                    stats.linkExpansionCount(),
                    "entityExpansionCount",
                    stats.entityExpansionCount(),
                    "admittedGraphCandidateCount",
                    stats.admittedGraphCandidateCount(),
                    "timedOut",
                    stats.timedOut());
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class GraphAssistConvention
            implements ObservationConvention<GraphAssistObservationContext> {

        public static final GraphAssistConvention INSTANCE = new GraphAssistConvention();

        @Override
        public String getName() {
            return GraphAssistDocument.ASSIST.getName();
        }

        @Override
        public String getContextualName(GraphAssistObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(GraphAssistObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(memoryId(context.queryContext)),
                    HighCardinalityKeyNames.GRAPH_ENABLED.withValue(
                            String.valueOf(
                                    context.graphSettings != null
                                            && context.graphSettings.enabled())),
                    HighCardinalityKeyNames.DIRECT_COUNT.withValue(
                            String.valueOf(context.directCount)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof GraphAssistObservationContext;
        }
    }

    private static String memoryId(QueryContext queryContext) {
        return queryContext == null || queryContext.memoryId() == null
                ? "unknown"
                : queryContext.memoryId().toIdentifier();
    }
}
