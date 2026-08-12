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
package com.openmemind.ai.memory.plugin.observability.micrometer.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.observation.MemoryObservationContext;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalStatus;
import com.openmemind.ai.memory.core.retrieval.admission.DefaultRetrievalAdmissionPolicy;
import com.openmemind.ai.memory.core.retrieval.admission.RetrievalAdmissionOptions;
import com.openmemind.ai.memory.core.retrieval.observation.DefaultMemoryRetrieverObservation.RetrievalObservationContext;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.trace.BoundedRetrievalTraceRecorder;
import com.openmemind.ai.memory.core.retrieval.trace.ObservationTiming;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceEvent;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceEventSource;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceObservationHandler;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceRecorder;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.plugin.observability.micrometer.MemoryMeterObservationHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@DisplayName("MemoryObservabilityAutoConfiguration Test")
class MemoryObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    ObservationAutoConfiguration.class,
                                    MemoryObservabilityAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName("Register Micrometer meter observation handler when MeterRegistry exists")
        void registersMeterObservationHandlerWhenMeterRegistryExists() {
            contextRunner
                    .withUserConfiguration(MicrometerProviderConfig.class)
                    .run(
                            context -> {
                                assertThat(context)
                                        .hasSingleBean(MemoryMeterObservationHandler.class);

                                var observationContext =
                                        new RetrievalObservationContext(() -> "memory-1");
                                Observation.createNotStarted(
                                                "memind.test",
                                                () -> observationContext,
                                                context.getBean(ObservationRegistry.class))
                                        .observe(
                                                () ->
                                                        observationContext.recordResult(
                                                                RetrievalResult.empty(
                                                                        "simple", "query")));

                                assertThat(
                                                context.getBean(MeterRegistry.class)
                                                        .find("memind.retrieval.empty_results")
                                                        .counter()
                                                        .count())
                                        .isEqualTo(1.0);
                            });
        }

        @Test
        @DisplayName(
                "Register ObservationRegistry without meter handler when MeterRegistry missing")
        void registersObservationRegistryWithoutMeterHandlerWhenMeterRegistryMissing() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(ObservationRegistry.class);
                        assertThat(context).doesNotHaveBean(MemoryMeterObservationHandler.class);
                    });
        }

        @Test
        @DisplayName("Register retrieval trace observation handler in ObservationRegistry")
        void registersRetrievalTraceObservationHandler() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(RetrievalTraceObservationHandler.class);
                        var recorder =
                                new BoundedRetrievalTraceRecorder(
                                        new RetrievalTraceOptions(8, 4, 16));
                        var observationContext = new TestTraceContext(recorder);

                        Observation.createNotStarted(
                                        "memind.test",
                                        () -> observationContext,
                                        context.getBean(ObservationRegistry.class))
                                .observe(() -> {});

                        assertThat(recorder.snapshot().orElseThrow().stages()).hasSize(1);
                    });
        }

        @Test
        @DisplayName("Record timeout fallback consistently across API, trace, and metrics")
        void recordsTimeoutFallbackConsistently() {
            contextRunner
                    .withUserConfiguration(MicrometerProviderConfig.class)
                    .run(
                            context -> {
                                var memoryId =
                                        (com.openmemind.ai.memory.core.data.MemoryId)
                                                () -> "memory-1";
                                var store = mock(MemoryStore.class);
                                var itemOperations = mock(ItemOperations.class);
                                var strategy = mock(RetrievalStrategy.class);
                                when(store.itemOperations()).thenReturn(itemOperations);
                                when(itemOperations.hasItems(memoryId)).thenReturn(true);
                                when(strategy.name()).thenReturn("simple");
                                when(strategy.retrieve(any(), any())).thenReturn(Mono.never());

                                var options = RetrievalAdmissionOptions.defaults();
                                var retriever =
                                        new DefaultMemoryRetriever(
                                                store,
                                                null,
                                                null,
                                                new DefaultRetrievalAdmissionPolicy(options),
                                                options,
                                                null,
                                                context.getBean(ObservationRegistry.class));
                                retriever.registerStrategy(strategy);
                                var config =
                                        RetrievalConfig.simple().withTimeout(Duration.ofMillis(10));
                                var request =
                                        new RetrievalRequest(
                                                memoryId, "query", List.of(), config, Map.of(),
                                                null, null);
                                var recorder =
                                        new BoundedRetrievalTraceRecorder(
                                                RetrievalTraceOptions.defaults());

                                var result =
                                        retriever
                                                .retrieve(request)
                                                .contextWrite(
                                                        reactorContext ->
                                                                reactorContext.put(
                                                                        RetrievalTraceRecorder
                                                                                .class,
                                                                        recorder))
                                                .block(Duration.ofSeconds(1));

                                assertThat(result).isNotNull();
                                assertThat(result.status()).isEqualTo(RetrievalStatus.DEGRADED);
                                assertThat(recorder.snapshot().orElseThrow().finalResults())
                                        .isNotNull()
                                        .extracting(finalTrace -> finalTrace.status())
                                        .isEqualTo("degraded");

                                var meterRegistry = context.getBean(MeterRegistry.class);
                                assertThat(
                                                meterRegistry
                                                        .find("memind.retrieval.results")
                                                        .tags(
                                                                "status",
                                                                "degraded",
                                                                "result_type",
                                                                "item")
                                                        .summary())
                                        .isNotNull();
                                assertThat(
                                                meterRegistry
                                                        .find("memind.retrieval.empty_results")
                                                        .counter())
                                        .isNull();
                            });
        }

        @Test
        @DisplayName("Back off cleanly when observability is disabled")
        void backsOffWhenObservabilityDisabled() {
            contextRunner
                    .withUserConfiguration(MicrometerProviderConfig.class)
                    .withPropertyValues("memind.observability.enabled=false")
                    .run(
                            context -> {
                                assertThat(context)
                                        .doesNotHaveBean(MemoryMeterObservationHandler.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default meter handler when user defines one")
        void userMemoryMeterObservationHandlerTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(
                            MicrometerProviderConfig.class,
                            CustomMeterObservationHandlerConfig.class)
                    .run(
                            context -> {
                                assertThat(context)
                                        .hasSingleBean(MemoryMeterObservationHandler.class);
                                assertThat(context.getBean(MemoryMeterObservationHandler.class))
                                        .isSameAs(
                                                context.getBean(
                                                                CustomMeterObservationHandlerConfig
                                                                        .class)
                                                        .handler);
                            });
        }

        @Test
        @DisplayName("Do not register default observation registry when user defines one")
        void userObservationRegistryTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(ObservationOnlyConfig.class)
                    .run(
                            context ->
                                    assertThat(
                                                    context.getBean(ObservationOnlyConfig.class)
                                                            .registry)
                                            .isSameAs(context.getBean(ObservationRegistry.class)));
        }
    }

    @Configuration
    static class MicrometerProviderConfig {

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    static class ObservationOnlyConfig {

        private final ObservationRegistry registry = ObservationRegistry.create();

        @Bean
        ObservationRegistry observationRegistry() {
            return registry;
        }
    }

    @Configuration
    static class CustomMeterObservationHandlerConfig {

        private final MemoryMeterObservationHandler handler =
                new MemoryMeterObservationHandler(new SimpleMeterRegistry());

        @Bean
        MemoryMeterObservationHandler memoryMeterObservationHandler() {
            return handler;
        }
    }

    private static QueryContext queryContext() {
        return new QueryContext(
                () -> "memory-1", "query", null, List.of(), Map.of(), null, Set.of());
    }

    private static final class TestTraceContext extends MemoryObservationContext
            implements RetrievalTraceEventSource {

        private TestTraceContext(RetrievalTraceRecorder recorder) {
            put(RetrievalTraceRecorder.class, recorder);
        }

        @Override
        public Optional<RetrievalTraceEvent> toRetrievalTraceEvent(
                ObservationTiming timing, RetrievalTraceOptions options) {
            return Optional.of(
                    new RetrievalTraceEvent(
                            "memind.test",
                            "memind.test",
                            status(),
                            timing.startedAt(),
                            timing.completedAt(),
                            timing.durationMillis(),
                            Map.of("operation", "retrieval", "stage", "tier"),
                            Map.of(),
                            new RetrievalTraceEvent.StagePayload(
                                    "tier", "item", "vector", null, null, 1, false, false, Map.of(),
                                    List.of())));
        }
    }
}
