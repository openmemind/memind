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
package com.openmemind.ai.memory.core.retrieval.deep.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
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

/** Observation contracts for LlmTypedQueryExpander. */
public final class LlmTypedQueryExpanderObservation {

    private LlmTypedQueryExpanderObservation() {}

    public static Mono<List<ExpandedQuery>> observe(
            ObservationRegistry observationRegistry,
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions,
            Supplier<Mono<List<ExpandedQuery>>> operation) {
        return observe(
                observationRegistry,
                query,
                gaps,
                keyInformation,
                conversationHistory,
                maxExpansions,
                ignored -> operation.get());
    }

    public static Mono<List<ExpandedQuery>> observe(
            ObservationRegistry observationRegistry,
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions,
            Function<MultiQueryExpandObservationContext, Mono<List<ExpandedQuery>>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                MultiQueryExpandDocument.EXPAND,
                MultiQueryExpandConvention.INSTANCE,
                reactorContext ->
                        new MultiQueryExpandObservationContext(
                                query,
                                gaps,
                                keyInformation,
                                conversationHistory,
                                maxExpansions,
                                reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum MultiQueryExpandDocument implements ObservationDocumentation {
        EXPAND;

        @Override
        public String getName() {
            return "memind.retrieval.multi_query_expand";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return MultiQueryExpandConvention.class;
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
        GAP_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.gap_count";
            }
        },
        KEY_INFORMATION_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.key_information_count";
            }
        },
        CONVERSATION_HISTORY_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.conversation_history_count";
            }
        },
        MAX_EXPANSIONS {
            @Override
            public String asString() {
                return "memind.retrieval.max_expansions";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        };
    }

    public static final class MultiQueryExpandObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final String query;
        private final int gapCount;
        private final int keyInformationCount;
        private final int conversationHistoryCount;
        private final int maxExpansions;
        private List<ExpandedQuery> results = List.of();
        private boolean degraded;

        public MultiQueryExpandObservationContext(
                String query,
                List<String> gaps,
                List<String> keyInformation,
                List<String> conversationHistory,
                int maxExpansions) {
            this(query, gaps, keyInformation, conversationHistory, maxExpansions, null);
        }

        public MultiQueryExpandObservationContext(
                String query,
                List<String> gaps,
                List<String> keyInformation,
                List<String> conversationHistory,
                int maxExpansions,
                ContextView reactorContext) {
            super(reactorContext);
            this.query = query;
            this.gapCount = gaps == null ? 0 : gaps.size();
            this.keyInformationCount = keyInformation == null ? 0 : keyInformation.size();
            this.conversationHistoryCount =
                    conversationHistory == null ? 0 : conversationHistory.size();
            this.maxExpansions = maxExpansions;
        }

        public void recordResult(List<ExpandedQuery> results) {
            this.results = results == null ? List.of() : List.copyOf(results);
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(resultCount())));
        }

        public int inputCount() {
            return gapCount;
        }

        public int candidateCount() {
            return maxExpansions;
        }

        public int resultCount() {
            return results.size();
        }

        public void markDegraded() {
            degraded = true;
        }

        public String stage() {
            return "query_expand";
        }

        public String tier() {
            return "none";
        }

        public String method() {
            return "llm";
        }

        public boolean degraded() {
            return degraded;
        }

        public boolean skipped() {
            return maxExpansions <= 0;
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
                            MultiQueryExpandDocument.EXPAND.getName(),
                            MultiQueryExpandDocument.EXPAND.getName(),
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", stage()),
                            Map.of(
                                    HighCardinalityKeyNames.GAP_COUNT.asString(),
                                    String.valueOf(gapCount),
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
                                    List.of())));
        }

        private Map<String, Object> attributes() {
            return Map.of(
                    "gapCount",
                    gapCount,
                    "keyInformationCount",
                    keyInformationCount,
                    "conversationHistoryCount",
                    conversationHistoryCount,
                    "maxExpansions",
                    maxExpansions);
        }
    }

    public static final class MultiQueryExpandConvention
            implements ObservationConvention<MultiQueryExpandObservationContext> {

        public static final MultiQueryExpandConvention INSTANCE = new MultiQueryExpandConvention();

        @Override
        public String getName() {
            return MultiQueryExpandDocument.EXPAND.getName();
        }

        @Override
        public String getContextualName(MultiQueryExpandObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(MultiQueryExpandObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.QUERY.withValue(safe(context.query)),
                    HighCardinalityKeyNames.GAP_COUNT.withValue(String.valueOf(context.gapCount)),
                    HighCardinalityKeyNames.KEY_INFORMATION_COUNT.withValue(
                            String.valueOf(context.keyInformationCount)),
                    HighCardinalityKeyNames.CONVERSATION_HISTORY_COUNT.withValue(
                            String.valueOf(context.conversationHistoryCount)),
                    HighCardinalityKeyNames.MAX_EXPANSIONS.withValue(
                            String.valueOf(context.maxExpansions)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof MultiQueryExpandObservationContext;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
