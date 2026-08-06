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
package com.openmemind.ai.memory.core.extraction.insight.generator.observation;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.PointOperation;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointOpsResponse;
import com.openmemind.ai.memory.core.observation.MemoryObservation;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.ObservationDocumentation;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Observation contracts for LlmInsightGenerator. */
public final class LlmInsightGeneratorObservation {

    private LlmInsightGeneratorObservation() {}

    public static Mono<InsightPointGenerateResponse> observeLeafPointGeneration(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            String groupName,
            Supplier<Mono<InsightPointGenerateResponse>> operation) {
        return observeLeafPointGeneration(
                observationRegistry, insightType, groupName, ignored -> operation.get());
    }

    public static Mono<InsightPointGenerateResponse> observeLeafPointGeneration(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            String groupName,
            Function<InsightGenerateObservationContext, Mono<InsightPointGenerateResponse>>
                    operation) {
        return observePointGeneration(
                observationRegistry,
                InsightGenerateObservationContext.leaf(insightType, groupName),
                operation);
    }

    public static Mono<InsightPointOpsResponse> observeLeafPointOperations(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            String groupName,
            Supplier<Mono<InsightPointOpsResponse>> operation) {
        return observeLeafPointOperations(
                observationRegistry, insightType, groupName, ignored -> operation.get());
    }

    public static Mono<InsightPointOpsResponse> observeLeafPointOperations(
            ObservationRegistry observationRegistry,
            MemoryInsightType insightType,
            String groupName,
            Function<InsightGenerateObservationContext, Mono<InsightPointOpsResponse>> operation) {
        return observePointOperations(
                observationRegistry,
                InsightGenerateObservationContext.leaf(insightType, groupName),
                operation);
    }

    public static Mono<InsightPointGenerateResponse> observeAggregatePointGeneration(
            ObservationRegistry observationRegistry,
            InsightGenerateDocument document,
            MemoryInsightType insightType,
            List<MemoryInsight> insights,
            Supplier<Mono<InsightPointGenerateResponse>> operation) {
        return observeAggregatePointGeneration(
                observationRegistry, document, insightType, insights, ignored -> operation.get());
    }

    public static Mono<InsightPointGenerateResponse> observeAggregatePointGeneration(
            ObservationRegistry observationRegistry,
            InsightGenerateDocument document,
            MemoryInsightType insightType,
            List<MemoryInsight> insights,
            Function<InsightGenerateObservationContext, Mono<InsightPointGenerateResponse>>
                    operation) {
        return observePointGeneration(
                observationRegistry,
                InsightGenerateObservationContext.aggregate(document, insightType, insights),
                operation);
    }

    public static Mono<InsightPointOpsResponse> observeAggregatePointOperations(
            ObservationRegistry observationRegistry,
            InsightGenerateDocument document,
            MemoryInsightType insightType,
            List<MemoryInsight> insights,
            Supplier<Mono<InsightPointOpsResponse>> operation) {
        return observeAggregatePointOperations(
                observationRegistry, document, insightType, insights, ignored -> operation.get());
    }

    public static Mono<InsightPointOpsResponse> observeAggregatePointOperations(
            ObservationRegistry observationRegistry,
            InsightGenerateDocument document,
            MemoryInsightType insightType,
            List<MemoryInsight> insights,
            Function<InsightGenerateObservationContext, Mono<InsightPointOpsResponse>> operation) {
        return observePointOperations(
                observationRegistry,
                InsightGenerateObservationContext.aggregate(document, insightType, insights),
                operation);
    }

    private static Mono<InsightPointGenerateResponse> observePointGeneration(
            ObservationRegistry observationRegistry,
            InsightGenerateObservationContext context,
            Function<InsightGenerateObservationContext, Mono<InsightPointGenerateResponse>>
                    operation) {
        return MemoryObservation.mono(
                observationRegistry,
                context.document,
                InsightGenerateConvention.of(context.document),
                () -> context,
                ignored -> operation.apply(context).doOnNext(context::recordPointResponse));
    }

    private static Mono<InsightPointOpsResponse> observePointOperations(
            ObservationRegistry observationRegistry,
            InsightGenerateObservationContext context,
            Function<InsightGenerateObservationContext, Mono<InsightPointOpsResponse>> operation) {
        return MemoryObservation.mono(
                observationRegistry,
                context.document,
                InsightGenerateConvention.of(context.document),
                () -> context,
                ignored -> operation.apply(context).doOnNext(context::recordOpsResponse));
    }

    public enum InsightGenerateDocument implements ObservationDocumentation {
        LEAF("memind.extraction.insight.generate.leaf"),
        BRANCH("memind.extraction.insight.generate.branch"),
        ROOT("memind.extraction.insight.generate.root");

        private final String name;

        InsightGenerateDocument(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return InsightGenerateConvention.class;
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return switch (this) {
                case LEAF ->
                        new KeyName[] {
                            HighCardinalityKeyNames.INSIGHT_TYPE,
                            HighCardinalityKeyNames.GROUP_NAME,
                            HighCardinalityKeyNames.POINT_COUNT,
                            HighCardinalityKeyNames.ADD_COUNT,
                            HighCardinalityKeyNames.UPDATE_COUNT,
                            HighCardinalityKeyNames.DELETE_COUNT
                        };
                case BRANCH ->
                        new KeyName[] {
                            HighCardinalityKeyNames.INSIGHT_TYPE,
                            HighCardinalityKeyNames.LEAF_COUNT,
                            HighCardinalityKeyNames.POINT_COUNT,
                            HighCardinalityKeyNames.ADD_COUNT,
                            HighCardinalityKeyNames.UPDATE_COUNT,
                            HighCardinalityKeyNames.DELETE_COUNT
                        };
                case ROOT ->
                        new KeyName[] {
                            HighCardinalityKeyNames.INSIGHT_TYPE,
                            HighCardinalityKeyNames.LEAF_COUNT,
                            HighCardinalityKeyNames.POINT_COUNT
                        };
            };
        }
    }

    public enum HighCardinalityKeyNames implements KeyName {
        INSIGHT_TYPE {
            @Override
            public String asString() {
                return "memind.extraction.insight_type";
            }
        },
        GROUP_NAME {
            @Override
            public String asString() {
                return "memind.extraction.insight_group_name";
            }
        },
        LEAF_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_leaf_count";
            }
        },
        POINT_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_point_count";
            }
        },
        ADD_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_add_count";
            }
        },
        UPDATE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_update_count";
            }
        },
        DELETE_COUNT {
            @Override
            public String asString() {
                return "memind.extraction.insight_delete_count";
            }
        };
    }

    public static final class InsightGenerateObservationContext extends Observation.Context {

        private final InsightGenerateDocument document;
        private final String insightTypeName;
        private final String groupName;
        private final Integer leafCount;
        private Integer pointCount;
        private Integer addCount;
        private Integer updateCount;
        private Integer deleteCount;

        public InsightGenerateObservationContext(
                InsightGenerateDocument document,
                String insightTypeName,
                String groupName,
                Integer leafCount) {
            this.document = document;
            this.insightTypeName = insightTypeName;
            this.groupName = groupName;
            this.leafCount = leafCount;
        }

        public static InsightGenerateObservationContext leaf(
                MemoryInsightType insightType, String groupName) {
            return new InsightGenerateObservationContext(
                    InsightGenerateDocument.LEAF,
                    insightType == null ? "" : insightType.name(),
                    groupName == null ? "" : groupName,
                    null);
        }

        public static InsightGenerateObservationContext aggregate(
                InsightGenerateDocument document,
                MemoryInsightType insightType,
                List<MemoryInsight> insights) {
            return new InsightGenerateObservationContext(
                    document,
                    insightType == null ? "" : insightType.name(),
                    null,
                    insights == null ? 0 : insights.size());
        }

        public void recordPointResponse(InsightPointGenerateResponse response) {
            this.pointCount =
                    response == null || response.points() == null ? 0 : response.points().size();
        }

        public void recordOpsResponse(InsightPointOpsResponse response) {
            var operations = response == null ? List.<PointOperation>of() : response.operations();
            this.addCount = countOperations(operations, PointOperation.OpType.ADD);
            this.updateCount = countOperations(operations, PointOperation.OpType.UPDATE);
            this.deleteCount = countOperations(operations, PointOperation.OpType.DELETE);
        }

        private static int countOperations(
                List<PointOperation> operations, PointOperation.OpType opType) {
            return (int) operations.stream().filter(operation -> operation.op() == opType).count();
        }
    }

    public static final class InsightGenerateConvention
            implements ObservationConvention<InsightGenerateObservationContext> {

        public static final InsightGenerateConvention LEAF =
                new InsightGenerateConvention(InsightGenerateDocument.LEAF);
        public static final InsightGenerateConvention BRANCH =
                new InsightGenerateConvention(InsightGenerateDocument.BRANCH);
        public static final InsightGenerateConvention ROOT =
                new InsightGenerateConvention(InsightGenerateDocument.ROOT);

        private final InsightGenerateDocument document;

        public InsightGenerateConvention(InsightGenerateDocument document) {
            this.document = document;
        }

        public static InsightGenerateConvention of(InsightGenerateDocument document) {
            return switch (document) {
                case LEAF -> LEAF;
                case BRANCH -> BRANCH;
                case ROOT -> ROOT;
            };
        }

        @Override
        public String getName() {
            return document.getName();
        }

        @Override
        public String getContextualName(InsightGenerateObservationContext context) {
            return context.document.getName();
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(InsightGenerateObservationContext context) {
            var values =
                    KeyValues.of(
                            HighCardinalityKeyNames.INSIGHT_TYPE.withValue(
                                    context.insightTypeName));
            if (context.groupName != null) {
                values =
                        values.and(HighCardinalityKeyNames.GROUP_NAME.withValue(context.groupName));
            }
            if (context.leafCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.LEAF_COUNT.withValue(
                                        String.valueOf(context.leafCount)));
            }
            if (context.pointCount != null) {
                values =
                        values.and(
                                HighCardinalityKeyNames.POINT_COUNT.withValue(
                                        String.valueOf(context.pointCount)));
            }
            if (context.addCount != null) {
                values =
                        values.and(
                                        HighCardinalityKeyNames.ADD_COUNT.withValue(
                                                String.valueOf(context.addCount)))
                                .and(
                                        HighCardinalityKeyNames.UPDATE_COUNT.withValue(
                                                String.valueOf(context.updateCount)))
                                .and(
                                        HighCardinalityKeyNames.DELETE_COUNT.withValue(
                                                String.valueOf(context.deleteCount)));
            }
            return values;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof InsightGenerateObservationContext generateContext
                    && generateContext.document == document;
        }
    }
}
