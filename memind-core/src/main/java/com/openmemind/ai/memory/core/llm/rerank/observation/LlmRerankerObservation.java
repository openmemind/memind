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
package com.openmemind.ai.memory.core.llm.rerank.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
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

/** Observation contracts for LlmReranker. */
public final class LlmRerankerObservation {

    private LlmRerankerObservation() {}

    public static Mono<List<ScoredResult>> observe(
            ObservationRegistry observationRegistry,
            String query,
            List<ScoredResult> results,
            int topK,
            Supplier<Mono<List<ScoredResult>>> operation) {
        return observe(observationRegistry, query, results, topK, ignored -> operation.get());
    }

    public static Mono<List<ScoredResult>> observe(
            ObservationRegistry observationRegistry,
            String query,
            List<ScoredResult> results,
            int topK,
            Function<RerankObservationContext, Mono<List<ScoredResult>>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                RerankDocument.RERANK,
                RerankConvention.INSTANCE,
                reactorContext ->
                        new RerankObservationContext(query, results, topK, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum RerankDocument implements ObservationDocumentation {
        RERANK;

        @Override
        public String getName() {
            return "memind.retrieval.rerank";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return RerankConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        QUERY {
            @Override
            public String asString() {
                return "memind.retrieval.query";
            }
        },
        CANDIDATES {
            @Override
            public String asString() {
                return "memind.retrieval.rerank.candidates";
            }
        },
        TOP_K {
            @Override
            public String asString() {
                return "memind.retrieval.top_k";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        };
    }

    public static final class RerankObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final String query;
        private final int candidateCount;
        private final int topK;
        private List<ScoredResult> results = List.of();
        private boolean degraded;

        public RerankObservationContext(String query, List<ScoredResult> results, int topK) {
            this(query, results, topK, null);
        }

        public RerankObservationContext(
                String query, List<ScoredResult> results, int topK, ContextView reactorContext) {
            super(reactorContext);
            this.query = query;
            this.candidateCount = results == null ? 0 : results.size();
            this.topK = topK;
        }

        public void recordResult(List<ScoredResult> results) {
            this.results = results == null ? List.of() : List.copyOf(results);
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(resultCount())));
        }

        public int inputCount() {
            return candidateCount;
        }

        public int candidateCount() {
            return candidateCount;
        }

        public int resultCount() {
            return results.size();
        }

        public void markDegraded() {
            degraded = true;
        }

        public boolean degraded() {
            return degraded;
        }

        public boolean skipped() {
            return topK <= 0 || candidateCount == 0;
        }

        public String stage() {
            return "rerank";
        }

        public String tier() {
            return "item";
        }

        public String method() {
            return "rerank";
        }

        public String source() {
            return "core";
        }

        @Override
        public String status() {
            String terminalStatus = errorOrCancellationStatus();
            return terminalStatus == null && degraded ? "degraded" : super.status();
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            RerankDocument.RERANK.getName(),
                            RerankDocument.RERANK.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", stage()),
                            Map.of(
                                    HighCardinalityKeyNames.CANDIDATES.asString(),
                                    String.valueOf(candidateCount),
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
                                    Map.of("topK", topK),
                                    RetrievalTraceEvent.candidates(
                                            results,
                                            options.maxCandidatesPerStage(),
                                            options.maxTextLength()))));
        }
    }

    public static final class RerankConvention
            implements ObservationConvention<RerankObservationContext> {

        public static final RerankConvention INSTANCE = new RerankConvention();

        @Override
        public String getName() {
            return RerankDocument.RERANK.getName();
        }

        @Override
        public String getContextualName(RerankObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RerankObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.QUERY.withValue(safe(context.query)),
                    HighCardinalityKeyNames.CANDIDATES.withValue(
                            String.valueOf(context.candidateCount)),
                    HighCardinalityKeyNames.TOP_K.withValue(String.valueOf(context.topK)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RerankObservationContext;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
