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
package com.openmemind.ai.memory.core.retrieval.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for DefaultMemoryRetriever. */
public final class DefaultMemoryRetrieverObservation {

    private DefaultMemoryRetrieverObservation() {}

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<RetrievalResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<RetrievalResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<RetrievalObservationContext, Mono<RetrievalResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                RetrievalDocument.RETRIEVAL,
                RetrievalObservationConvention.INSTANCE,
                () -> new RetrievalObservationContext(memoryId),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum RetrievalDocument implements ObservationDocumentation {
        RETRIEVAL;

        @Override
        public String getName() {
            return "memind.retrieval";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return RetrievalObservationConvention.class;
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
        };
    }

    public static final class RetrievalObservationContext extends Observation.Context {

        private final MemoryId memoryId;

        public RetrievalObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }

        public void recordResult(RetrievalResult result) {
            addHighCardinalityKeyValue(
                    HighCardinalityKeyNames.RESULT_COUNT.withValue(
                            String.valueOf(result.items().size())));
        }
    }

    public static final class RetrievalObservationConvention
            implements ObservationConvention<RetrievalObservationContext> {

        public static final RetrievalObservationConvention INSTANCE =
                new RetrievalObservationConvention();

        @Override
        public String getName() {
            return RetrievalDocument.RETRIEVAL.getName();
        }

        @Override
        public String getContextualName(RetrievalObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RetrievalObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RetrievalObservationContext;
        }
    }
}
