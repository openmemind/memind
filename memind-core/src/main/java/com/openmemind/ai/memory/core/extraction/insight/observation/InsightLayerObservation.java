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
package com.openmemind.ai.memory.core.extraction.insight.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for InsightLayer. */
public final class InsightLayerObservation {

    private InsightLayerObservation() {}

    public static Mono<InsightResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<InsightResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<InsightResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<InsightObservationContext, Mono<InsightResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                InsightDocument.EXTRACT,
                InsightConvention.INSTANCE,
                () -> new InsightObservationContext(memoryId),
                operation);
    }

    public enum InsightDocument implements ObservationDocumentation {
        EXTRACT;

        @Override
        public String getName() {
            return "memind.extraction.insight";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return InsightConvention.class;
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
        };
    }

    public static final class InsightObservationContext extends Observation.Context {

        private final MemoryId memoryId;

        public InsightObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }
    }

    public static final class InsightConvention
            implements ObservationConvention<InsightObservationContext> {

        public static final InsightConvention INSTANCE = new InsightConvention();

        @Override
        public String getName() {
            return InsightDocument.EXTRACT.getName();
        }

        @Override
        public String getContextualName(InsightObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(InsightObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof InsightObservationContext;
        }
    }
}
