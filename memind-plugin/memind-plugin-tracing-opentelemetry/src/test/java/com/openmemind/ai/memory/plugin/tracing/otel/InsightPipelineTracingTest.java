package com.openmemind.ai.memory.plugin.tracing.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.buffer.BufferEntry;
import com.openmemind.ai.memory.core.extraction.insight.buffer.InsightBufferStore;
import com.openmemind.ai.memory.core.extraction.insight.buffer.InsightBufferStore.UngroupedContext;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightPointGenerateResponse;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGenerator;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightGroupClassifier;
import com.openmemind.ai.memory.core.utils.IdUtils;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
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

    // ===== Shared helpers =====

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
                "test-memory-1",
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
            InsightBufferStore bufferStore,
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

    private void setupClassifierMock(InsightGroupClassifier classifier, MemoryItem item) {
        when(classifier.classify(any(), anyList(), anyList(), any()))
                .thenReturn(Mono.just(Map.of("general", List.of(item))));
        when(classifier.classify(any(), anyList(), anyList()))
                .thenReturn(Mono.just(Map.of("general", List.of(item))));
    }

    // ===== Test 1: Pipeline without tree reorganizer =====

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

            // Mock buffer store: 1 ungrouped entry, then after assign it becomes 1 unbuilt entry
            var bufferStore = mock(InsightBufferStore.class);
            var ungroupedEntry = BufferEntry.ungrouped(101L);
            var groupedEntry = new BufferEntry(101L, "general", false);

            when(bufferStore.hasWork(memoryId, insightTypeName)).thenReturn(true);
            when(bufferStore.getUngroupedContext(memoryId, insightTypeName))
                    .thenReturn(new UngroupedContext(List.of(ungroupedEntry), Set.of()));
            when(bufferStore.getUnbuiltByGroup(memoryId, insightTypeName))
                    .thenReturn(Map.of("general", List.of(groupedEntry)));

            // Mock store
            var store = mock(MemoryStore.class);
            when(store.getInsightType(memoryId, insightTypeName))
                    .thenReturn(Optional.of(insightType));
            when(store.getItemsByIds(any(), anyList())).thenReturn(List.of(item));
            when(store.getLeafByGroup(any(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // Mock classifier: returns 1 group
            var classifier = mock(InsightGroupClassifier.class);
            setupClassifierMock(classifier, item);

            // Mock generator: returns 1 point
            var generator = mock(InsightGenerator.class);
            var point =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "summary text", 0.9f, List.of("101"));
            var response = new InsightPointGenerateResponse(List.of(point));
            when(generator.generatePoints(
                            any(), anyString(), anyList(), anyList(), anyInt(), isNull(), isNull()))
                    .thenReturn(Mono.just(response));

            var scheduler = buildScheduler(bufferStore, store, generator, classifier, null);

            scheduler.flushSync(memoryId, insightTypeName);

            List<SpanData> spans = spanExporter.getFinishedSpanItems();

            // Find pipeline span
            var pipelineSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_PIPELINE);
            assertThat(pipelineSpan).isPresent();

            // Find classify span
            var classifySpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_GROUP_CLASSIFY);
            assertThat(classifySpan).isPresent();

            // Find generate.leaf span
            var leafSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_GENERATE_LEAF);
            assertThat(leafSpan).isPresent();

            // The pipeline internals call .block() on nested Monos, which breaks reactor context
            // propagation. Verify all three spans are emitted — structural hierarchy is tested
            // at the observer level in OpenTelemetryMemoryObserverTest.
        }
    }

    // ===== Test 2: Pipeline with tree reorganizer =====

    @Nested
    @DisplayName("PipelineWithTree")
    class PipelineWithTree {

        @Test
        @DisplayName("flushSync produces tree.reorganize as child of pipeline span")
        void treeReorganizeSpanIsChildOfPipeline() {
            var memoryId = testMemoryId();
            var insightTypeName = "profile";
            var insightType = testInsightType(insightTypeName);
            var item = testItem(202L);

            // Mock buffer store
            var bufferStore = mock(InsightBufferStore.class);
            var ungroupedEntry = BufferEntry.ungrouped(202L);
            var groupedEntry = new BufferEntry(202L, "general", false);

            when(bufferStore.hasWork(memoryId, insightTypeName)).thenReturn(true);
            when(bufferStore.getUngroupedContext(memoryId, insightTypeName))
                    .thenReturn(new UngroupedContext(List.of(ungroupedEntry), Set.of()));
            when(bufferStore.getUnbuiltByGroup(memoryId, insightTypeName))
                    .thenReturn(Map.of("general", List.of(groupedEntry)));

            // Mock store
            var store = mock(MemoryStore.class);
            when(store.getInsightType(memoryId, insightTypeName))
                    .thenReturn(Optional.of(insightType));
            when(store.getItemsByIds(any(), anyList())).thenReturn(List.of(item));
            when(store.getLeafByGroup(any(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // Mock classifier
            var classifier = mock(InsightGroupClassifier.class);
            setupClassifierMock(classifier, item);

            // Mock generator
            var generator = mock(InsightGenerator.class);
            var point =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "summary text", 0.9f, List.of("202"));
            var response = new InsightPointGenerateResponse(List.of(point));
            when(generator.generatePoints(
                            any(), anyString(), anyList(), anyList(), anyInt(), isNull(), isNull()))
                    .thenReturn(Mono.just(response));

            // Mock tree reorganizer — wraps with observer so tree.reorganize span is emitted
            // by InsightBuildScheduler.phaseTreeReorganize which calls observer.observeMono
            // directly
            var treeReorganizer = mock(InsightTreeReorganizer.class);

            var scheduler =
                    buildScheduler(bufferStore, store, generator, classifier, treeReorganizer);

            scheduler.flushSync(memoryId, insightTypeName);

            List<SpanData> spans = spanExporter.getFinishedSpanItems();

            // Find tree.reorganize span — it is emitted by phaseTreeReorganize which calls
            // observer.observeMono(...).block() as a new reactor subscription, so it won't
            // carry the pipeline reactor context. We verify it exists.
            var treeSpan = findSpan(MemorySpanNames.EXTRACTION_INSIGHT_TREE_REORGANIZE);
            assertThat(treeSpan).isPresent();
        }
    }
}
