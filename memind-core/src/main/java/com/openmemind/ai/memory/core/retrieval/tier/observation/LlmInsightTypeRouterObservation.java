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
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for LlmInsightTypeRouter. */
public final class LlmInsightTypeRouterObservation {

    private LlmInsightTypeRouterObservation() {}

    public static Mono<List<String>> observe(
            ObservationRegistry observationRegistry, Supplier<Mono<List<String>>> operation) {
        return observe(observationRegistry, ignored -> operation.get());
    }

    public static Mono<List<String>> observe(
            ObservationRegistry observationRegistry,
            Function<InsightTypeRoutingObservationContext, Mono<List<String>>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                InsightTypeRoutingDocument.ROUTING,
                InsightTypeRoutingConvention.INSTANCE,
                InsightTypeRoutingObservationContext::new,
                operation);
    }

    public enum InsightTypeRoutingDocument implements ObservationDocumentation {
        ROUTING;

        @Override
        public String getName() {
            return "memind.retrieval.insight_type_routing";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return InsightTypeRoutingConvention.class;
        }
    }

    public static final class InsightTypeRoutingObservationContext extends Observation.Context {}

    public static final class InsightTypeRoutingConvention
            implements ObservationConvention<InsightTypeRoutingObservationContext> {

        public static final InsightTypeRoutingConvention INSTANCE =
                new InsightTypeRoutingConvention();

        @Override
        public String getName() {
            return InsightTypeRoutingDocument.ROUTING.getName();
        }

        @Override
        public String getContextualName(InsightTypeRoutingObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(InsightTypeRoutingObservationContext context) {
            return KeyValues.empty();
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof InsightTypeRoutingObservationContext;
        }
    }
}
