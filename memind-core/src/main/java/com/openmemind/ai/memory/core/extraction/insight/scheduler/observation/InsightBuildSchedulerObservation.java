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
package com.openmemind.ai.memory.core.extraction.insight.scheduler.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import reactor.core.publisher.Mono;

/** Observation contracts for InsightBuildScheduler. */
public final class InsightBuildSchedulerObservation {

    private InsightBuildSchedulerObservation() {}

    public static Mono<Void> observePipeline(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            String insightTypeName,
            Runnable operation) {
        return MemoryObservation.mono(
                observationRegistry,
                PipelineDocument.RUN,
                PipelineConvention.INSTANCE,
                () -> new PipelineObservationContext(memoryId, insightTypeName),
                ignored -> Mono.fromRunnable(operation).then());
    }

    public static Mono<Void> observeTreeReorganize(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            String insightTypeName,
            int leafCount,
            Runnable operation) {
        return MemoryObservation.mono(
                observationRegistry,
                TreeReorganizeDocument.RUN,
                TreeReorganizeConvention.INSTANCE,
                () -> new TreeReorganizeObservationContext(memoryId, insightTypeName, leafCount),
                ignored -> Mono.fromRunnable(operation).then());
    }

    public enum PipelineDocument implements ObservationDocumentation {
        RUN("memind.extraction.insight.pipeline");

        private final String name;

        PipelineDocument(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return PipelineConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[] {
                HighCardinalityKeyNames.MEMORY_ID, HighCardinalityKeyNames.INSIGHT_TYPE
            };
        }
    }

    public enum TreeReorganizeDocument implements ObservationDocumentation {
        RUN("memind.extraction.insight.tree.reorganize");

        private final String name;

        TreeReorganizeDocument(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return TreeReorganizeConvention.class;
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
        INSIGHT_TYPE {
            @Override
            public String asString() {
                return "memind.extraction.insight_type";
            }
        },
        LEAF_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_leaf_count";
            }
        };
    }

    public static final class PipelineObservationContext extends Observation.Context {

        private final String memoryId;
        private final String insightTypeName;

        public PipelineObservationContext(MemoryId memoryId, String insightTypeName) {
            this.memoryId = memoryId == null ? "" : memoryId.toIdentifier();
            this.insightTypeName = insightTypeName == null ? "" : insightTypeName;
        }
    }

    public static final class TreeReorganizeObservationContext extends Observation.Context {

        private final String memoryId;
        private final String insightTypeName;
        private final int leafCount;

        public TreeReorganizeObservationContext(
                MemoryId memoryId, String insightTypeName, int leafCount) {
            this.memoryId = memoryId == null ? "" : memoryId.toIdentifier();
            this.insightTypeName = insightTypeName == null ? "" : insightTypeName;
            this.leafCount = leafCount;
        }
    }

    public static final class PipelineConvention
            implements ObservationConvention<PipelineObservationContext> {

        public static final PipelineConvention INSTANCE = new PipelineConvention();

        @Override
        public String getName() {
            return PipelineDocument.RUN.getName();
        }

        @Override
        public String getContextualName(PipelineObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(PipelineObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId),
                    HighCardinalityKeyNames.INSIGHT_TYPE.withValue(context.insightTypeName));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof PipelineObservationContext;
        }
    }

    public static final class TreeReorganizeConvention
            implements ObservationConvention<TreeReorganizeObservationContext> {

        public static final TreeReorganizeConvention INSTANCE = new TreeReorganizeConvention();

        @Override
        public String getName() {
            return TreeReorganizeDocument.RUN.getName();
        }

        @Override
        public String getContextualName(TreeReorganizeObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(TreeReorganizeObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId),
                    HighCardinalityKeyNames.INSIGHT_TYPE.withValue(context.insightTypeName),
                    HighCardinalityKeyNames.LEAF_COUNT.withValue(
                            String.valueOf(context.leafCount)));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TreeReorganizeObservationContext;
        }
    }
}
