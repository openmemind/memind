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
package com.openmemind.ai.memory.core.extraction.item.dedup.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
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

/** Observation contracts for CompositeDeduplicator. */
public final class CompositeDeduplicatorObservation {

    private CompositeDeduplicatorObservation() {}

    public static Mono<DeduplicationResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<DeduplicationResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<DeduplicationResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<DeduplicationObservationContext, Mono<DeduplicationResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                DeduplicationDocument.DEDUPLICATE,
                DeduplicationConvention.INSTANCE,
                () -> new DeduplicationObservationContext(memoryId),
                operation);
    }

    public enum DeduplicationDocument implements ObservationDocumentation {
        DEDUPLICATE;

        @Override
        public String getName() {
            return "memind.extraction.item.dedup";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return DeduplicationConvention.class;
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

    public static final class DeduplicationObservationContext extends Observation.Context {

        private final MemoryId memoryId;

        public DeduplicationObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }
    }

    public static final class DeduplicationConvention
            implements ObservationConvention<DeduplicationObservationContext> {

        public static final DeduplicationConvention INSTANCE = new DeduplicationConvention();

        @Override
        public String getName() {
            return DeduplicationDocument.DEDUPLICATE.getName();
        }

        @Override
        public String getContextualName(DeduplicationObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(DeduplicationObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof DeduplicationObservationContext;
        }
    }
}
