package com.openmemind.ai.memory.plugin.tracing.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.tracing.ObservationContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OpenTelemetryMemoryObserver")
class OpenTelemetryMemoryObserverTest {

    private static InMemorySpanExporter spanExporter;
    private static Tracer tracer;
    private OpenTelemetryMemoryObserver observer;

    @BeforeAll
    static void setUpOtel() {
        ContextPropagationOperator.builder().build().registerOnEachOperator();
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                        .build();
        tracer = tracerProvider.get("memind-test");
    }

    @BeforeEach
    void setUp() {
        spanExporter.reset();
        var meter = SdkMeterProvider.builder().build().get("memind-test");
        observer = new OpenTelemetryMemoryObserver(tracer, meter);
    }

    @Nested
    @DisplayName("observeMono")
    class ObserveMonoTests {

        @Test
        @DisplayName("produces span with correct name and kind")
        void producesSpanWithCorrectNameAndKind() {
            ObservationContext<String> ctx = ObservationContext.of("test.operation");

            StepVerifier.create(observer.observeMono(ctx, () -> Mono.just("result")))
                    .expectNext("result")
                    .verifyComplete();

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            SpanData span = spans.get(0);
            assertThat(span.getName()).isEqualTo("test.operation");
            assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        }

        @Test
        @DisplayName("sets request attributes on span")
        void setsRequestAttributesOnSpan() {
            Map<String, Object> attrs =
                    Map.of(
                            "str.attr",
                            "hello",
                            "int.attr",
                            42,
                            "long.attr",
                            100L,
                            "double.attr",
                            3.14,
                            "bool.attr",
                            true);
            ObservationContext<String> ctx = ObservationContext.of("test.attrs", attrs);

            StepVerifier.create(observer.observeMono(ctx, () -> Mono.just("ok")))
                    .expectNext("ok")
                    .verifyComplete();

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.stringKey(
                                                    "str.attr")))
                    .isEqualTo("hello");
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.longKey(
                                                    "int.attr")))
                    .isEqualTo(42L);
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.longKey(
                                                    "long.attr")))
                    .isEqualTo(100L);
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.doubleKey(
                                                    "double.attr")))
                    .isEqualTo(3.14);
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.booleanKey(
                                                    "bool.attr")))
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("backfills result attributes via resultExtractor")
        void backfillsResultAttributesViaExtractor() {
            ObservationContext<String> ctx =
                    ObservationContext.<String>of("test.result")
                            .withResultExtractor(
                                    result -> Map.of("result.length", result.length()));

            StepVerifier.create(observer.observeMono(ctx, () -> Mono.just("hello")))
                    .expectNext("hello")
                    .verifyComplete();

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(
                            span.getAttributes()
                                    .get(
                                            io.opentelemetry.api.common.AttributeKey.longKey(
                                                    "result.length")))
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("records error status and exception on failure")
        void recordsErrorStatusAndExceptionEvent() {
            ObservationContext<String> ctx = ObservationContext.of("test.error");
            RuntimeException ex = new RuntimeException("something went wrong");

            StepVerifier.create(observer.observeMono(ctx, () -> Mono.error(ex)))
                    .expectError(RuntimeException.class)
                    .verify();

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getStatus().getDescription()).isEqualTo("something went wrong");
            assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
        }

        @Test
        @DisplayName("nested observeMono produces parent-child spans")
        void nestedCallsProduceParentChildSpans() {
            ObservationContext<String> outerCtx = ObservationContext.of("outer.span");
            ObservationContext<String> innerCtx = ObservationContext.of("inner.span");

            Mono<String> nested =
                    observer.observeMono(
                            outerCtx,
                            () -> observer.observeMono(innerCtx, () -> Mono.just("nested")));

            StepVerifier.create(nested).expectNext("nested").verifyComplete();

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(2);

            SpanData inner =
                    spans.stream()
                            .filter(s -> s.getName().equals("inner.span"))
                            .findFirst()
                            .orElseThrow();
            SpanData outer =
                    spans.stream()
                            .filter(s -> s.getName().equals("outer.span"))
                            .findFirst()
                            .orElseThrow();

            assertThat(inner.getParentSpanId()).isEqualTo(outer.getSpanId());
        }

        @Test
        @DisplayName("handles null result without error (empty Mono)")
        void handlesEmptyMonoWithoutError() {
            ObservationContext<String> ctx = ObservationContext.of("test.empty");

            StepVerifier.create(observer.observeMono(ctx, Mono::empty)).verifyComplete();

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            assertThat(spans.get(0).getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        }
    }

    @Nested
    @DisplayName("observeFlux")
    class ObserveFluxTests {

        @Test
        @DisplayName("produces span wrapping flux emission")
        void producesSpanWrappingFluxEmission() {
            ObservationContext<String> ctx = ObservationContext.of("test.flux");

            StepVerifier.create(observer.observeFlux(ctx, () -> Flux.just("a", "b", "c")))
                    .expectNext("a", "b", "c")
                    .verifyComplete();

            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            SpanData span = spans.get(0);
            assertThat(span.getName()).isEqualTo("test.flux");
            assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        }

        @Test
        @DisplayName("records error on flux failure")
        void recordsErrorOnFluxFailure() {
            ObservationContext<String> ctx = ObservationContext.of("test.flux.error");

            StepVerifier.create(
                            observer.observeFlux(
                                    ctx,
                                    () ->
                                            Flux.concat(
                                                    Flux.just("a"),
                                                    Flux.error(new RuntimeException("flux-boom")))))
                    .expectNext("a")
                    .expectError(RuntimeException.class)
                    .verify();

            SpanData span = spanExporter.getFinishedSpanItems().get(0);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
        }
    }
}
