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
package com.openmemind.ai.memory.plugin.tracing.otel.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.plugin.tracing.otel.OpenTelemetryMemoryObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("MemoryObserverAutoConfiguration Test")
class MemoryObserverAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MemoryObserverAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName(
                "Register OpenTelemetryMemoryObserver and TracingBeanPostProcessor when OTel beans"
                        + " exist")
        void registersObserverAndBeanPostProcessorWhenOtelBeansExist() {
            contextRunner
                    .withUserConfiguration(OtelProviderConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(MemoryObserver.class);
                                assertThat(context.getBean(MemoryObserver.class))
                                        .isInstanceOf(OpenTelemetryMemoryObserver.class);
                                assertThat(context).hasSingleBean(TracingBeanPostProcessor.class);
                            });
        }

        @Test
        @DisplayName("Back off cleanly when tracing is disabled")
        void backsOffWhenTracingDisabled() {
            contextRunner
                    .withUserConfiguration(OtelProviderConfig.class)
                    .withPropertyValues("memind.tracing.enabled=false")
                    .run(
                            context -> {
                                assertThat(context).doesNotHaveBean(MemoryObserver.class);
                                assertThat(context).doesNotHaveBean(TracingBeanPostProcessor.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default observer when user defines MemoryObserver")
        void userMemoryObserverTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(OtelProviderConfig.class, CustomObserverConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(MemoryObserver.class);
                                assertThat(context.getBean(MemoryObserver.class))
                                        .isSameAs(
                                                context.getBean(CustomObserverConfig.class)
                                                        .memoryObserver);
                                assertThat(context).hasSingleBean(TracingBeanPostProcessor.class);
                            });
        }
    }

    @Configuration
    static class OtelProviderConfig {

        private static final OpenTelemetry OPEN_TELEMETRY = OpenTelemetry.noop();

        @Bean
        Tracer tracer() {
            return OPEN_TELEMETRY.getTracer("test");
        }

        @Bean
        Meter meter() {
            return OPEN_TELEMETRY.getMeter("test");
        }
    }

    @Configuration
    static class CustomObserverConfig {

        private final MemoryObserver memoryObserver = new TestMemoryObserver();

        @Bean
        MemoryObserver memoryObserver() {
            return memoryObserver;
        }
    }

    private static final class TestMemoryObserver implements MemoryObserver {

        @Override
        public <T> reactor.core.publisher.Mono<T> observeMono(
                com.openmemind.ai.memory.core.tracing.ObservationContext<T> ctx,
                java.util.function.Supplier<reactor.core.publisher.Mono<T>> operation) {
            return operation.get();
        }

        @Override
        public <T> reactor.core.publisher.Flux<T> observeFlux(
                com.openmemind.ai.memory.core.tracing.ObservationContext<T> ctx,
                java.util.function.Supplier<reactor.core.publisher.Flux<T>> operation) {
            return operation.get();
        }
    }
}
