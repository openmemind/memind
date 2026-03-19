package com.openmemind.ai.memory.plugin.tracing.otel;

import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Tracer.class)
@ConditionalOnProperty(
        prefix = "memind.tracing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MemoryObserverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryObserver.class)
    public MemoryObserver openTelemetryMemoryObserver(Tracer tracer, Meter meter) {
        return new OpenTelemetryMemoryObserver(tracer, meter);
    }

    @Bean
    public TracingBeanPostProcessor tracingBeanPostProcessor(
            ObjectProvider<MemoryObserver> observerProvider) {
        return new TracingBeanPostProcessor(observerProvider);
    }
}
