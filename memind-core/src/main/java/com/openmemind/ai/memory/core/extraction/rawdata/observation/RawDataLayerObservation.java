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
package com.openmemind.ai.memory.core.extraction.rawdata.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
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

/** Observation contracts for RawDataLayer. */
public final class RawDataLayerObservation {

    private RawDataLayerObservation() {}

    public static Mono<RawDataResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<RawDataResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<RawDataResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<RawDataObservationContext, Mono<RawDataResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                RawDataDocument.EXTRACT,
                RawDataConvention.INSTANCE,
                () -> new RawDataObservationContext(memoryId),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum RawDataDocument implements ObservationDocumentation {
        EXTRACT;

        @Override
        public String getName() {
            return "memind.extraction.rawdata";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return RawDataConvention.class;
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
        SEGMENT_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.segment_count";
            }
        };
    }

    public static final class RawDataObservationContext extends Observation.Context {

        private final MemoryId memoryId;
        private Integer segmentCount;

        public RawDataObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }

        public void recordResult(RawDataResult result) {
            this.segmentCount = result.segments().size();
        }
    }

    public static final class RawDataConvention
            implements ObservationConvention<RawDataObservationContext> {

        public static final RawDataConvention INSTANCE = new RawDataConvention();

        @Override
        public String getName() {
            return RawDataDocument.EXTRACT.getName();
        }

        @Override
        public String getContextualName(RawDataObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RawDataObservationContext context) {
            var values =
                    KeyValues.of(
                            HighCardinalityKeyNames.MEMORY_ID.withValue(
                                    context.memoryId.toIdentifier()));
            if (context.segmentCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.SEGMENT_COUNT.withValue(
                                        String.valueOf(context.segmentCount)));
            }
            return values;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RawDataObservationContext;
        }
    }
}
