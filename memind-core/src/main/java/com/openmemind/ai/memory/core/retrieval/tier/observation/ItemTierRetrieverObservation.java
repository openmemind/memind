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

/** Observation contracts for ItemTierRetriever. */
public final class ItemTierRetrieverObservation {

    private ItemTierRetrieverObservation() {}

    public static Mono<TierResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String tierName,
            ItemTierDocument document,
            int topK,
            Supplier<Mono<TierResult>> operation) {
        return observe(
                observationRegistry,
                queryContext,
                tierName,
                document,
                topK,
                ignored -> operation.get());
    }

    public static Mono<TierResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String tierName,
            ItemTierDocument document,
            int topK,
            Function<ItemTierObservationContext, Mono<TierResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                document,
                ItemTierConvention.of(document),
                reactorContext ->
                        new ItemTierObservationContext(
                                queryContext, tierName, document, topK, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public static Mono<List<ScoredResult>> observeResults(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String tierName,
            ItemTierDocument document,
            int topK,
            Supplier<Mono<List<ScoredResult>>> operation) {
        return observeResults(
                observationRegistry,
                queryContext,
                tierName,
                document,
                topK,
                ignored -> operation.get());
    }

    public static Mono<List<ScoredResult>> observeResults(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String tierName,
            ItemTierDocument document,
            int topK,
            Function<ItemTierObservationContext, Mono<List<ScoredResult>>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                document,
                ItemTierConvention.of(document),
                reactorContext ->
                        new ItemTierObservationContext(
                                queryContext, tierName, document, topK, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResults));
    }

    public enum ItemTierDocument implements ObservationDocumentation {
        VECTOR_SEARCH("memind.retrieval.vector_search"),
        KEYWORD_SEARCH("memind.retrieval.keyword_search"),
        HYBRID_SEARCH("memind.retrieval.tier.item");

        private final String name;

        ItemTierDocument(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return ItemTierConvention.class;
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

    public static final class ItemTierObservationContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private final QueryContext queryContext;
        private final String tierName;
        private final ItemTierDocument document;
        private final int topK;
        private List<ScoredResult> results = List.of();
        private int resultCount;

        public ItemTierObservationContext(
                QueryContext queryContext, String tierName, ItemTierDocument document, int topK) {
            this(queryContext, tierName, document, topK, null);
        }

        public ItemTierObservationContext(
                QueryContext queryContext,
                String tierName,
                ItemTierDocument document,
                int topK,
                ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.tierName = tierName;
            this.document = document;
            this.topK = topK;
        }

        public void recordResult(int resultCount) {
            this.resultCount = resultCount;
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(resultCount)));
        }

        public void recordResult(TierResult result) {
            recordResults(result == null ? List.of() : result.results());
        }

        public void recordResults(List<ScoredResult> results) {
            this.results = results == null ? List.of() : List.copyOf(results);
            recordResult(this.results.size());
        }

        public int resultCount() {
            return resultCount;
        }

        public String method() {
            return switch (document) {
                case VECTOR_SEARCH -> "vector";
                case KEYWORD_SEARCH -> "keyword";
                case HYBRID_SEARCH -> "hybrid";
            };
        }

        public String strategyName() {
            return null;
        }

        public String stage() {
            return "tier";
        }

        public String tier() {
            return "item";
        }

        public String source() {
            return "core";
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            document.getName(),
                            document.getName(),
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
                                    "item",
                                    method(),
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

    public static final class ItemTierConvention
            implements ObservationConvention<ItemTierObservationContext> {

        public static final ItemTierConvention VECTOR_SEARCH =
                new ItemTierConvention(ItemTierDocument.VECTOR_SEARCH);
        public static final ItemTierConvention KEYWORD_SEARCH =
                new ItemTierConvention(ItemTierDocument.KEYWORD_SEARCH);
        public static final ItemTierConvention HYBRID_SEARCH =
                new ItemTierConvention(ItemTierDocument.HYBRID_SEARCH);

        private final ItemTierDocument document;

        public ItemTierConvention(ItemTierDocument document) {
            this.document = document;
        }

        public static ItemTierConvention of(ItemTierDocument document) {
            return switch (document) {
                case VECTOR_SEARCH -> VECTOR_SEARCH;
                case KEYWORD_SEARCH -> KEYWORD_SEARCH;
                case HYBRID_SEARCH -> HYBRID_SEARCH;
            };
        }

        @Override
        public String getName() {
            return document.getName();
        }

        @Override
        public String getContextualName(ItemTierObservationContext context) {
            return context.document.getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ItemTierObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.TIER_NAME.withValue(context.tierName),
                    HighCardinalityKeyNames.TOP_K.withValue(String.valueOf(context.topK)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ItemTierObservationContext;
        }
    }
}
