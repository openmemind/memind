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
package com.openmemind.ai.memory.plugin.tracing.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.BufferEntry;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer.UngroupedContext;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGenerator;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGroupClassifier;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@DisplayName("InsightPipelineTracingTest")
class InsightPipelineTracingTest {

    private static InMemorySpanExporter spanExporter;
    private static OpenTelemetryMemoryObserver observer;

    @BeforeAll
    static void setUpOtel() {
        ContextPropagationOperator.builder().build().registerOnEachOperator();
        spanExporter = InMemorySpanExporter.create();
        var tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                        .build();
        var tracer = tracerProvider.get("memind-test");
        var meter = SdkMeterProvider.builder().build().get("memind-test");
        observer = new OpenTelemetryMemoryObserver(tracer, meter);
    }

    @BeforeEach
    void resetExporter() {
        spanExporter.reset();
    }

    @FunctionalInterface
    private interface MethodHandler {
        Object handle(String methodName, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MethodHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            var actualArgs = args != null ? args : new Object[0];
                            return switch (method.getName()) {
                                case "toString" -> type.getSimpleName() + "Proxy";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == actualArgs[0];
                                default -> handler.handle(method.getName(), actualArgs);
                            };
                        });
    }

    private static Object unsupported(String typeName, String methodName) {
        throw new UnsupportedOperationException(
                "Unexpected " + typeName + " method: " + methodName);
    }

    private Optional<SpanData> findSpan(String name) {
        return spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst();
    }

    private static MemoryId testMemoryId() {
        return () -> "test-memory-1";
    }

    private static MemoryInsightType testInsightType(String name) {
        return new MemoryInsightType(
                1L,
                name,
                "Test insight type",
                null,
                List.of(),
                500,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.USER,
                null);
    }

    private static MemoryItem testItem(long id) {
        return new MemoryItem(
                id,
                "test-memory-1",
                "test content " + id,
                MemoryScope.USER,
                null,
                "conversation",
                null,
                null,
                null,
                Instant.now(),
                null,
                Instant.now(),
                null);
    }

    private InsightBuildScheduler buildScheduler(
            InsightBuffer bufferStore,
            MemoryStore store,
            InsightGenerator generator,
            InsightGroupClassifier classifier,
            InsightTreeReorganizer treeReorganizer) {
        var tracingClassifier = new TracingInsightGroupClassifier(classifier, observer);
        var tracingGenerator = new TracingInsightGenerator(generator, observer);
        var tracingRouter = new InsightGroupRouter(tracingClassifier);
        var config = new InsightBuildConfig(1, 1, 4, 2);
        var idGen = IdUtils.snowflake();
        return new InsightBuildScheduler(
                bufferStore,
                store,
                tracingGenerator,
                tracingClassifier,
                tracingRouter,
                treeReorganizer,
                null,
                idGen,
                config,
                observer);
    }

    private InsightBuffer createBufferStore(BufferEntry ungroupedEntry, BufferEntry groupedEntry) {
        var ungroupedContext = new UngroupedContext(List.of(ungroupedEntry), Set.of());
        var unbuiltByGroup = Map.of("general", List.of(groupedEntry));
        return proxy(
                InsightBuffer.class,
                (methodName, args) ->
                        switch (methodName) {
                            case "hasWork" -> true;
                            case "getUngroupedContext" -> ungroupedContext;
                            case "getUnbuiltByGroup" -> unbuiltByGroup;
                            case "assignGroup", "markBuilt", "append" -> null;
                            case "getUnGrouped" -> ungroupedContext.ungroupedEntries();
                            case "countUnGrouped" -> ungroupedContext.ungroupedEntries().size();
                            case "listGroups" -> Set.of("general");
                            case "countGroupUnbuilt" -> groupedEntry == null ? 0 : 1;
                            case "getGroupUnbuilt" -> List.of(groupedEntry);
                            default -> unsupported("InsightBuffer", methodName);
                        });
    }

    private MemoryStore createStore(MemoryInsightType insightType, MemoryItem item) {
        Map<String, MemoryInsight> leafsByGroup = new HashMap<>();

        MethodHandler insightHandler =
                (methodName, args) ->
                        switch (methodName) {
                            case "getInsightType" -> Optional.of(insightType);
                            case "getLeafByGroup" ->
                                    Optional.ofNullable(leafsByGroup.get((String) args[2]));
                            case "upsertInsights" -> {
                                @SuppressWarnings("unchecked")
                                List<MemoryInsight> insights = (List<MemoryInsight>) args[1];
                                insights.forEach(
                                        insight -> leafsByGroup.put(insight.group(), insight));
                                yield null;
                            }
                            default -> unsupported("InsightOperations", methodName);
                        };

        MethodHandler itemHandler =
                (methodName, args) ->
                        switch (methodName) {
                            case "getItemsByIds" -> List.of(item);
                            default -> unsupported("ItemOperations", methodName);
                        };

        var insightOps =
                proxy(
                        com.openmemind.ai.memory.core.store.insight.InsightOperations.class,
                        insightHandler);
        var itemOps =
                proxy(com.openmemind.ai.memory.core.store.item.ItemOperations.class, itemHandler);

        return proxy(
                MemoryStore.class,
                (methodName, args) ->
                        switch (methodName) {
                            case "insightOperations" -> insightOps;
                            case "itemOperations" -> itemOps;
                            default -> unsupported("MemoryStore", methodName);
                        });
    }

    private InsightGroupClassifier createClassifier(MemoryItem item) {
        return (insightType, items, existingGroupNames) ->
                Mono.just(Map.of("general", List.of(item)));
    }

    private InsightGenerator createGenerator(long itemId) {
        var point =
                new InsightPoint(
                        InsightPoint.PointType.SUMMARY,
                        "summary text",
                        0.9f,
                        List.of(String.valueOf(itemId)));
        var response = new InsightPointGenerateResponse(List.of(point));
        return new InsightGenerator() {
            @Override
            public Mono<InsightPointGenerateResponse> generatePoints(
                    MemoryInsightType insightType,
                    String groupName,
                    List<InsightPoint> existingPoints,
                    List<MemoryItem> newItems,
                    int targetTokens,
                    String additionalContext,
                    String language) {
                return Mono.just(response);
            }

            @Override
            public Mono<InsightPointGenerateResponse> generateBranchSummary(
                    MemoryInsightType insightType,
                    List<InsightPoint> existingPoints,
                    List<MemoryInsight> leafInsights,
                    int targetTokens,
                    String language) {
                return Mono.empty();
            }

            @Override
            public Mono<InsightPointGenerateResponse> generateRootSynthesis(
                    MemoryInsightType rootInsightType,
                    String existingSummary,
                    List<MemoryInsight> branchInsights,
                    int targetTokens,
                    String language) {
                return Mono.empty();
            }
        };
    }

    @Nested
    @DisplayName("PipelineWithoutTree")
    class PipelineWithoutTree {

        @Test
        @DisplayName("flushSync produces pipeline, classify, and generate.leaf spans")
        void pipelineSpanHierarchy() {
            var memoryId = testMemoryId();
            var insightTypeName = "profile";
            var insightType = testInsightType(insightTypeName);
            var item = testItem(101L);

            var bufferStore =
                    createBufferStore(
                            BufferEntry.ungrouped(101L), new BufferEntry(101L, "general", false));
            var store = createStore(insightType, item);
            var classifier = createClassifier(item);
            var generator = createGenerator(101L);

            var scheduler = buildScheduler(bufferStore, store, generator, classifier, null);

            scheduler.flushSync(memoryId, insightTypeName);

            var pipelineSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_PIPELINE);
            assertThat(pipelineSpan).isPresent();

            var classifySpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY);
            assertThat(classifySpan).isPresent();

            var leafSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_LEAF);
            assertThat(leafSpan).isPresent();
        }
    }

    @Nested
    @DisplayName("PipelineWithTree")
    class PipelineWithTree {

        @Test
        @DisplayName("flushSync produces tree.reorganize span when tree reorganizer is enabled")
        void treeReorganizeSpanIsChildOfPipeline() {
            var memoryId = testMemoryId();
            var insightTypeName = "profile";
            var insightType = testInsightType(insightTypeName);
            var item = testItem(202L);

            var bufferStore =
                    createBufferStore(
                            BufferEntry.ungrouped(202L), new BufferEntry(202L, "general", false));
            var store = createStore(insightType, item);
            var classifier = createClassifier(item);
            var generator = createGenerator(202L);
            var treeReorganizer = new RecordingTreeReorganizer();

            var scheduler =
                    buildScheduler(bufferStore, store, generator, classifier, treeReorganizer);

            scheduler.flushSync(memoryId, insightTypeName);

            var treeSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_TREE_REORGANIZE);
            assertThat(treeSpan).isPresent();
            assertThat(treeReorganizer.wasCalled()).isTrue();
        }
    }

    private static final class RecordingTreeReorganizer extends InsightTreeReorganizer {

        private boolean called;

        RecordingTreeReorganizer() {
            super(
                    new NoopInsightGenerator(),
                    proxy(
                            MemoryVector.class,
                            (methodName, args) -> unsupported("MemoryVector", methodName)),
                    proxy(
                            MemoryStore.class,
                            (methodName, args) -> unsupported("MemoryStore", methodName)),
                    proxy(
                            BubbleTrackerStore.class,
                            (methodName, args) ->
                                    switch (methodName) {
                                        case "markDirty", "reset" -> null;
                                        case "shouldResummarize" -> false;
                                        case "getDirtyCount" -> 0;
                                        default -> unsupported("BubbleTrackerStore", methodName);
                                    }),
                    IdUtils.snowflake());
        }

        @Override
        public void onLeafsUpdated(
                MemoryId memoryId,
                String insightTypeName,
                MemoryInsightType insightType,
                List<MemoryInsight> builtLeafs,
                InsightTreeConfig config,
                String language) {
            this.called = true;
        }

        boolean wasCalled() {
            return called;
        }
    }

    private static final class NoopInsightGenerator implements InsightGenerator {

        @Override
        public Mono<InsightPointGenerateResponse> generatePoints(
                MemoryInsightType insightType,
                String groupName,
                List<InsightPoint> existingPoints,
                List<MemoryItem> newItems,
                int targetTokens,
                String additionalContext,
                String language) {
            return Mono.empty();
        }

        @Override
        public Mono<InsightPointGenerateResponse> generateBranchSummary(
                MemoryInsightType insightType,
                List<InsightPoint> existingPoints,
                List<MemoryInsight> leafInsights,
                int targetTokens,
                String language) {
            return Mono.empty();
        }

        @Override
        public Mono<InsightPointGenerateResponse> generateRootSynthesis(
                MemoryInsightType rootInsightType,
                String existingSummary,
                List<MemoryInsight> branchInsights,
                int targetTokens,
                String language) {
            return Mono.empty();
        }
    }
}
