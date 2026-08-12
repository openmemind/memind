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
package com.openmemind.ai.memory.core.extraction.item.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
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

/** Observation contracts for MemoryItemLayer. */
public final class MemoryItemLayerObservation {

    private MemoryItemLayerObservation() {}

    public static Mono<MemoryItemResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<MemoryItemResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<MemoryItemResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<MemoryItemObservationContext, Mono<MemoryItemResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                MemoryItemDocument.EXTRACT,
                MemoryItemConvention.INSTANCE,
                () -> new MemoryItemObservationContext(memoryId),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum MemoryItemDocument implements ObservationDocumentation {
        EXTRACT;

        @Override
        public String getName() {
            return "memind.extraction.item";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return MemoryItemConvention.class;
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
        ITEM_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.item_count";
            }
        },
        NEW_ITEM_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.new_item_count";
            }
        };
    }

    public static final class MemoryItemObservationContext extends Observation.Context {

        private final MemoryId memoryId;
        private Integer itemCount;
        private Integer newItemCount;

        public MemoryItemObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }

        public void recordResult(MemoryItemResult result) {
            this.itemCount = result.newCount();
            this.newItemCount = result.newItems().size();
        }
    }

    public static final class MemoryItemConvention
            implements ObservationConvention<MemoryItemObservationContext> {

        public static final MemoryItemConvention INSTANCE = new MemoryItemConvention();

        @Override
        public String getName() {
            return MemoryItemDocument.EXTRACT.getName();
        }

        @Override
        public String getContextualName(MemoryItemObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(MemoryItemObservationContext context) {
            var values =
                    KeyValues.of(
                            HighCardinalityKeyNames.MEMORY_ID.withValue(
                                    context.memoryId.toIdentifier()));
            if (context.itemCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.ITEM_COUNT.withValue(
                                        String.valueOf(context.itemCount)));
            }
            if (context.newItemCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.NEW_ITEM_COUNT.withValue(
                                        String.valueOf(context.newItemCount)));
            }
            return values;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof MemoryItemObservationContext;
        }
    }
}
