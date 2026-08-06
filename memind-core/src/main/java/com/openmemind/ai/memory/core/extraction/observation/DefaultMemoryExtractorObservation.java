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
package com.openmemind.ai.memory.core.extraction.observation;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.ExtractionStatus;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for DefaultMemoryExtractor. */
public final class DefaultMemoryExtractorObservation {

    private DefaultMemoryExtractorObservation() {}

    public static Mono<ExtractionResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Supplier<Mono<ExtractionResult>> operation) {
        return observe(observationRegistry, memoryId, ignored -> operation.get());
    }

    public static Mono<ExtractionResult> observe(
            ObservationRegistry observationRegistry,
            MemoryId memoryId,
            Function<ExtractionObservationContext, Mono<ExtractionResult>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                ExtractionDocument.EXTRACTION,
                ExtractionObservationConvention.INSTANCE,
                () -> new ExtractionObservationContext(memoryId),
                context -> operation.apply(context).doOnNext(context::recordResult));
    }

    public enum ExtractionDocument implements ObservationDocumentation {
        EXTRACTION;

        @Override
        public String getName() {
            return "memind.extraction";
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return ExtractionObservationConvention.class;
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

    public static final class ExtractionObservationContext extends MemoryObservationContext {

        private final MemoryId memoryId;
        private boolean hasResult;
        private String resultStatus = "unknown";
        private int rawDataCount;
        private Integer segmentCount;
        private int newItemCount;
        private Integer reinforcedItemCount;
        private int insightCount;
        private Integer graphEntityCount;
        private Integer graphMentionCount;
        private Integer graphRelationCount;
        private String source = "core";

        public ExtractionObservationContext(MemoryId memoryId) {
            this.memoryId = memoryId;
        }

        public void recordResult(ExtractionResult result) {
            hasResult = true;
            if (result == null) {
                return;
            }
            resultStatus = status(result.status());
            rawDataCount =
                    result.rawDataResult() == null || result.rawDataResult().rawDataList() == null
                            ? 0
                            : result.rawDataResult().rawDataList().size();
            segmentCount =
                    result.rawDataResult() == null || result.rawDataResult().segments() == null
                            ? null
                            : result.rawDataResult().segments().size();

            MemoryItemResult itemResult = result.memoryItemResult();
            newItemCount = itemResult == null ? 0 : itemResult.newCount();
            insightCount = result.totalInsights();

            ItemGraphMaterializationResult graph =
                    itemResult == null ? null : itemResult.graphMaterializationResult();
            ItemGraphMaterializationResult.Stats stats = graph == null ? null : graph.stats();
            graphEntityCount = stats == null ? null : stats.entityCount();
            graphMentionCount = stats == null ? null : stats.mentionCount();
            graphRelationCount = finalRelationCount(stats);
        }

        public boolean hasResult() {
            return hasResult;
        }

        @Override
        public String status() {
            if (getError() != null) {
                return "error";
            }
            return resultStatus;
        }

        public int rawDataCount() {
            return rawDataCount;
        }

        public Integer segmentCount() {
            return segmentCount;
        }

        public int newItemCount() {
            return newItemCount;
        }

        public Integer reinforcedItemCount() {
            return reinforcedItemCount;
        }

        public int insightCount() {
            return insightCount;
        }

        public Integer graphEntityCount() {
            return graphEntityCount;
        }

        public Integer graphMentionCount() {
            return graphMentionCount;
        }

        public Integer graphRelationCount() {
            return graphRelationCount;
        }

        public String source() {
            return source;
        }

        private static Integer finalRelationCount(ItemGraphMaterializationResult.Stats stats) {
            if (stats == null || stats.finalRelationStats() == null) {
                return null;
            }
            var finalStats = stats.finalRelationStats();
            return finalStats.semanticRelationCount()
                    + finalStats.temporalRelationCount()
                    + finalStats.causalRelationCount()
                    + finalStats.itemLinkCount();
        }

        private static String status(ExtractionStatus status) {
            return status == null ? "unknown" : status.name().toLowerCase();
        }
    }

    public static final class ExtractionObservationConvention
            implements ObservationConvention<ExtractionObservationContext> {

        public static final ExtractionObservationConvention INSTANCE =
                new ExtractionObservationConvention();

        @Override
        public String getName() {
            return ExtractionDocument.EXTRACTION.getName();
        }

        @Override
        public String getContextualName(ExtractionObservationContext context) {
            return getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ExtractionObservationContext context) {
            return KeyValues.of(
                    HighCardinalityKeyNames.MEMORY_ID.withValue(context.memoryId.toIdentifier()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ExtractionObservationContext;
        }
    }
}
