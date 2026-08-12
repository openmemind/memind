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
package com.openmemind.ai.memory.core.retrieval.thread.observation;

import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.RetrievalMemoryThreadSettings;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for DefaultMemoryThreadAssistant. */
public final class DefaultMemoryThreadAssistantObservation {

    private DefaultMemoryThreadAssistantObservation() {}

    public static Mono<MemoryThreadAssistResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalMemoryThreadSettings settings,
            Supplier<Mono<MemoryThreadAssistResult>> operation) {
        return observe(observationRegistry, queryContext, settings, ignored -> operation.get());
    }

    public static Mono<MemoryThreadAssistResult> observe(
            ObservationRegistry observationRegistry,
            QueryContext queryContext,
            RetrievalMemoryThreadSettings settings,
            Function<MemoryThreadAssistObservationContext, Mono<MemoryThreadAssistResult>>
                    operation) {
        return MemoryObservation.mono(
                observationRegistry,
                MemoryThreadAssistDocument.ASSIST,
                MemoryThreadAssistConvention.INSTANCE,
                () -> new MemoryThreadAssistObservationContext(queryContext, settings),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum MemoryThreadAssistDocument implements ObservationDocumentation {
        ASSIST;

        @Override
        public String getName() {
            return "memind.retrieval.memory_thread.assist";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return MemoryThreadAssistConvention.class;
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
        ENABLED {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.enabled";
            }
        },
        SEED_THREAD_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.seed_thread_count";
            }
        },
        CANDIDATE_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.candidate_count";
            }
        },
        ADMITTED_COUNT {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.admitted_count";
            }
        },
        CLAMPED {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.clamped";
            }
        },
        DEGRADED {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.degraded";
            }
        },
        TIMEOUT {
            @Override
            public String asString() {
                return "memind.retrieval.memory_thread.timeout";
            }
        };
    }

    public static final class MemoryThreadAssistObservationContext extends Observation.Context {

        private final QueryContext queryContext;
        private final RetrievalMemoryThreadSettings settings;

        public MemoryThreadAssistObservationContext(
                QueryContext queryContext, RetrievalMemoryThreadSettings settings) {
            this.queryContext = queryContext;
            this.settings = settings;
        }

        public void recordResult(MemoryThreadAssistResult result) {
            var stats = result.stats();
            add(HighCardinalityKeyNames.SEED_THREAD_COUNT, stats.seedThreadCount());
            add(HighCardinalityKeyNames.CANDIDATE_COUNT, stats.candidateCount());
            add(HighCardinalityKeyNames.ADMITTED_COUNT, stats.admittedMemberCount());
            add(HighCardinalityKeyNames.CLAMPED, stats.clamped());
            add(HighCardinalityKeyNames.DEGRADED, stats.degraded());
            add(HighCardinalityKeyNames.TIMEOUT, stats.timedOut());
        }

        private void add(HighCardinalityKeyNames key, Object value) {
            if (value != null) {
                addHighCardinalityKeyValue(key.withValue(String.valueOf(value)));
            }
        }
    }

    public static final class MemoryThreadAssistConvention
            implements ObservationConvention<MemoryThreadAssistObservationContext> {

        public static final MemoryThreadAssistConvention INSTANCE =
                new MemoryThreadAssistConvention();

        @Override
        public String getName() {
            return MemoryThreadAssistDocument.ASSIST.getName();
        }

        @Override
        public String getContextualName(MemoryThreadAssistObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(MemoryThreadAssistObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(
                            context.queryContext.memoryId().toIdentifier()),
                    HighCardinalityKeyNames.ENABLED.withValue(
                            String.valueOf(
                                    context.settings != null && context.settings.enabled())));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof MemoryThreadAssistObservationContext;
        }
    }
}
