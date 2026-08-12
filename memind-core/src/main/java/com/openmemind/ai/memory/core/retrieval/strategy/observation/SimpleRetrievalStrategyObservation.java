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
package com.openmemind.ai.memory.core.retrieval.strategy.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Observation contracts for SimpleRetrievalStrategy. */
public final class SimpleRetrievalStrategyObservation {

    private SimpleRetrievalStrategyObservation() {}

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String strategyName,
            Supplier<Mono<RetrievalResult>> operation) {
        return observe(observationRegistry, queryContext, strategyName, ignored -> operation.get());
    }

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            String strategyName,
            Function<StrategyObservationContext, Mono<RetrievalResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                StrategyDocument.RETRIEVE,
                StrategyConvention.INSTANCE,
                reactorContext ->
                        new StrategyObservationContext(queryContext, strategyName, reactorContext),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum StrategyDocument implements ObservationDocumentation {
        RETRIEVE;

        @Override
        public String getName() {
            return "memind.retrieval.strategy";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return StrategyConvention.class;
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
        STRATEGY {
            @Override
            public String asString() {
                return "memind.retrieval.strategy";
            }
        },
        RESULT_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.result_count";
            }
        };
    }

    public static final class StrategyObservationContext extends MemoryObservationContext {

        private final QueryContext queryContext;
        private final String strategyName;
        private int itemCount;
        private int insightCount;
        private int rawDataCount;
        private int evidenceCount;
        private String resultStatus = "empty";

        public StrategyObservationContext(QueryContext queryContext, String strategyName) {
            this(queryContext, strategyName, null);
        }

        public StrategyObservationContext(
                QueryContext queryContext, String strategyName, ContextView reactorContext) {
            super(reactorContext);
            this.queryContext = queryContext;
            this.strategyName = strategyName;
        }

        public void recordResult(RetrievalResult result) {
            itemCount = result == null || result.items() == null ? 0 : result.items().size();
            insightCount =
                    result == null || result.insights() == null ? 0 : result.insights().size();
            rawDataCount = result == null || result.rawData() == null ? 0 : result.rawData().size();
            evidenceCount =
                    result == null || result.evidences() == null ? 0 : result.evidences().size();
            resultStatus =
                    result == null || result.status() == null
                            ? "empty"
                            : result.status().name().toLowerCase();
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(String.valueOf(itemCount)));
        }

        @Override
        public String status() {
            String terminalStatus = errorOrCancellationStatus();
            if (terminalStatus != null) {
                return terminalStatus;
            }
            return resultStatus;
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
    }

    public static final class StrategyConvention
            implements ObservationConvention<StrategyObservationContext> {

        public static final StrategyConvention INSTANCE = new StrategyConvention();

        @Override
        public String getName() {
            return StrategyDocument.RETRIEVE.getName();
        }

        @Override
        public String getContextualName(StrategyObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(StrategyObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.STRATEGY.withValue(context.strategyName));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof StrategyObservationContext;
        }
    }
}
