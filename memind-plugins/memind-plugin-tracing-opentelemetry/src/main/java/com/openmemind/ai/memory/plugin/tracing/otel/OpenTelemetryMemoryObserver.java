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

import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.Map;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenTelemetry implementation of {@link MemoryObserver}, providing dual observation of Span + Metrics.
 *
 * <p>Each operation generates an INTERNAL span, containing request attributes and (in the case of Mono) result attributes backfill.
 * At the same time, it produces two metrics: {@code memind.operation.errors} (Counter) and
 * {@code memind.operation.duration} (Histogram), distinguished by the {@code operation} label.
 *
 * <p>Context propagation is completed through {@link ContextPropagationOperator} in the Reactor Context,
 * nested observeMono/observeFlux calls automatically generate the correct parent-child relationship.
 *
 * <p><b>Note:</b> The Flux scenario does not support result attribute backfill ({@code resultExtractor} is ignored),
 * because Flux emits elements one by one, making it impossible to obtain the complete result set in a single callback.
 * For Flux result statistics, it is recommended to wrap with {@code observeMono} after {@code collectList()} at the business layer.
 */
public class OpenTelemetryMemoryObserver implements MemoryObserver {

    private final Tracer tracer;
    private final LongCounter errorCounter;
    private final DoubleHistogram durationHistogram;

    public OpenTelemetryMemoryObserver(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        this.errorCounter =
                meter.counterBuilder("memind.operation.errors")
                        .setDescription("Error count per operation")
                        .build();
        this.durationHistogram =
                meter.histogramBuilder("memind.operation.duration")
                        .setDescription("Operation duration")
                        .setUnit("ms")
                        .build();
    }

    @Override
    public <T> Mono<T> observeMono(ObservationContext<T> ctx, Supplier<Mono<T>> operation) {
        return Mono.deferContextual(
                ctxView -> {
                    long startNano = System.nanoTime();

                    Context parentOtelCtx =
                            ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                                    ctxView, Context.current());

                    Span span =
                            tracer.spanBuilder(ctx.spanName())
                                    .setParent(parentOtelCtx)
                                    .setSpanKind(SpanKind.INTERNAL)
                                    .setAllAttributes(toOtelAttributes(ctx.requestAttributes()))
                                    .startSpan();
                    Context newOtelCtx = span.storeInContext(parentOtelCtx);

                    Attributes metricAttrs =
                            Attributes.of(AttributeKey.stringKey("operation"), ctx.spanName());

                    return operation
                            .get()
                            .doOnSuccess(
                                    result -> {
                                        if (result != null) {
                                            Map<String, Object> resultAttrs =
                                                    ctx.resultExtractor().extract(result);
                                            span.setAllAttributes(toOtelAttributes(resultAttrs));
                                        }
                                    })
                            .doOnError(
                                    error -> {
                                        span.setStatus(StatusCode.ERROR, error.getMessage());
                                        span.recordException(error);
                                        errorCounter.add(1, metricAttrs);
                                    })
                            .doFinally(
                                    signal -> {
                                        span.end();
                                        double durationMs =
                                                (System.nanoTime() - startNano) / 1_000_000.0;
                                        durationHistogram.record(durationMs, metricAttrs);
                                    })
                            .contextWrite(
                                    reactorCtx ->
                                            ContextPropagationOperator.storeOpenTelemetryContext(
                                                    reactorCtx, newOtelCtx));
                });
    }

    @Override
    public <T> Flux<T> observeFlux(ObservationContext<T> ctx, Supplier<Flux<T>> operation) {
        return Flux.deferContextual(
                ctxView -> {
                    long startNano = System.nanoTime();

                    Context parentOtelCtx =
                            ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                                    ctxView, Context.current());

                    Span span =
                            tracer.spanBuilder(ctx.spanName())
                                    .setParent(parentOtelCtx)
                                    .setSpanKind(SpanKind.INTERNAL)
                                    .setAllAttributes(toOtelAttributes(ctx.requestAttributes()))
                                    .startSpan();
                    Context newOtelCtx = span.storeInContext(parentOtelCtx);

                    Attributes metricAttrs =
                            Attributes.of(AttributeKey.stringKey("operation"), ctx.spanName());

                    return operation
                            .get()
                            .doOnError(
                                    error -> {
                                        span.setStatus(StatusCode.ERROR, error.getMessage());
                                        span.recordException(error);
                                        errorCounter.add(1, metricAttrs);
                                    })
                            .doFinally(
                                    signal -> {
                                        span.end();
                                        double durationMs =
                                                (System.nanoTime() - startNano) / 1_000_000.0;
                                        durationHistogram.record(durationMs, metricAttrs);
                                    })
                            .contextWrite(
                                    reactorCtx ->
                                            ContextPropagationOperator.storeOpenTelemetryContext(
                                                    reactorCtx, newOtelCtx));
                });
    }

    private static Attributes toOtelAttributes(Map<String, Object> attrs) {
        var builder = Attributes.builder();
        attrs.forEach(
                (key, value) -> {
                    switch (value) {
                        case String s -> builder.put(AttributeKey.stringKey(key), s);
                        case Long l -> builder.put(AttributeKey.longKey(key), l);
                        case Integer i -> builder.put(AttributeKey.longKey(key), i.longValue());
                        case Double d -> builder.put(AttributeKey.doubleKey(key), d);
                        case Boolean b -> builder.put(AttributeKey.booleanKey(key), b);
                        default -> builder.put(AttributeKey.stringKey(key), value.toString());
                    }
                });
        return builder.build();
    }
}
