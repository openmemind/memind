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
package com.openmemind.ai.memory.core.retrieval.scoring.observation;

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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Observation contracts for DefaultRetrievalResultMerger. */
public final class DefaultRetrievalResultMergerObservation {

    private DefaultRetrievalResultMergerObservation() {}

    public static Mono<List<ScoredResult>> observe(
            ObservationRegistry observationRegistry,
            List<List<ScoredResult>> rankedLists,
            double[] weights,
            Supplier<Mono<List<ScoredResult>>> operation) {
        return observe(observationRegistry, rankedLists, weights, ignored -> operation.get());
    }

    public static Mono<List<ScoredResult>> observe(
            ObservationRegistry observationRegistry,
            List<List<ScoredResult>> rankedLists,
            double[] weights,
            Function<ResultMergeObservationContext, Mono<List<ScoredResult>>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                ResultMergeDocument.MERGE,
                ResultMergeConvention.INSTANCE,
                reactorContext ->
                        new ResultMergeObservationContext(rankedLists, weights, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum ResultMergeDocument implements ObservationDocumentation {
        MERGE;

        @Override
        public String getName() {
            return "memind.retrieval.result_merge";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return ResultMergeConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        SOURCE_LIST_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.source_list_count";
            }
        },
        CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.candidate_count";
            }
        },
        DEDUPED_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.deduped_count";
            }
        },
        WEIGHT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.weight_count";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        };
    }

    public static final class ResultMergeObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final List<List<ScoredResult>> rankedLists;
        private final double[] weights;
        private int outputCount;
        private int deduplicatedCount;

        public ResultMergeObservationContext(
                List<List<ScoredResult>> rankedLists, double[] weights) {
            this(rankedLists, weights, null);
        }

        public ResultMergeObservationContext(
                List<List<ScoredResult>> rankedLists,
                double[] weights,
                ContextView reactorContext) {
            super(reactorContext);
            this.rankedLists = rankedLists;
            this.weights = weights;
        }

        public void recordResult(List<ScoredResult> result) {
            int before = candidateCount(rankedLists);
            int after = result == null ? 0 : result.size();
            outputCount = after;
            deduplicatedCount = Math.max(0, before - after);
            add(HighCardinalityKeyNames.RESULT_COUNT, after);
            add(HighCardinalityKeyNames.DEDUPED_COUNT, deduplicatedCount);
        }

        public int inputCount() {
            return candidateCount(rankedLists);
        }

        public int outputCount() {
            return outputCount;
        }

        public int deduplicatedCount() {
            return deduplicatedCount;
        }

        public int sourceCount() {
            return rankedLists == null ? 0 : rankedLists.size();
        }

        public int weightCount() {
            return weights == null ? 0 : weights.length;
        }

        public String strategyName() {
            return null;
        }

        public String source() {
            return "core";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            ResultMergeDocument.MERGE.getName(),
                            ResultMergeDocument.MERGE.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "merge"),
                            Map.of(
                                    HighCardinalityKeyNames.CANDIDATE_COUNT.asString(),
                                    String.valueOf(inputCount()),
                                    HighCardinalityKeyNames.RESULT_COUNT.asString(),
                                    String.valueOf(outputCount())),
                            new RetrievalTraceEvent.MergePayload(
                                    inputCount(),
                                    outputCount(),
                                    deduplicatedCount(),
                                    sourceCount())));
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class ResultMergeConvention
            implements ObservationConvention<ResultMergeObservationContext> {

        public static final ResultMergeConvention INSTANCE = new ResultMergeConvention();

        @Override
        public String getName() {
            return ResultMergeDocument.MERGE.getName();
        }

        @Override
        public String getContextualName(ResultMergeObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ResultMergeObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.SOURCE_LIST_COUNT.withValue(
                            String.valueOf(context.sourceCount())),
                    HighCardinalityKeyNames.CANDIDATE_COUNT.withValue(
                            String.valueOf(context.inputCount())),
                    HighCardinalityKeyNames.DEDUPED_COUNT.withValue(
                            String.valueOf(context.deduplicatedCount())),
                    HighCardinalityKeyNames.WEIGHT_COUNT.withValue(
                            String.valueOf(context.weightCount())));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ResultMergeObservationContext;
        }
    }

    private static int candidateCount(List<List<ScoredResult>> rankedLists) {
        if (rankedLists == null) {
            return 0;
        }
        return rankedLists.stream().filter(Objects::nonNull).mapToInt(List::size).sum();
    }
}
