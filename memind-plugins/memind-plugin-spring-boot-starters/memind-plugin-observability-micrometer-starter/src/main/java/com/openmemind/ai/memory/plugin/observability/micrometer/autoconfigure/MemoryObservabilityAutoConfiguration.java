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

import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceObservationHandler;
import com.openmemind.ai.memory.plugin.observability.micrometer.MemoryMeterObservationHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnProperty(
        prefix = "memind.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MemoryObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObservationRegistry.class)
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalTraceObservationHandler.class)
    public RetrievalTraceObservationHandler retrievalTraceObservationHandler() {
        return new RetrievalTraceObservationHandler();
    }

    @Bean
    public RetrievalTraceObservationHandlerRegistrar retrievalTraceObservationHandlerRegistrar(
            ObservationRegistry observationRegistry, RetrievalTraceObservationHandler handler) {
        observationRegistry.observationConfig().observationHandler(handler);
        return new RetrievalTraceObservationHandlerRegistrar();
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MemoryMeterObservationHandler.class)
    public MemoryMeterObservationHandler memoryMeterObservationHandler(
            MeterRegistry meterRegistry) {
        return new MemoryMeterObservationHandler(meterRegistry);
    }

    @Bean
    @ConditionalOnBean(MemoryMeterObservationHandler.class)
    public MemoryMeterObservationHandlerRegistrar memoryMeterObservationHandlerRegistrar(
            ObservationRegistry observationRegistry, MemoryMeterObservationHandler handler) {
        observationRegistry.observationConfig().observationHandler(handler);
        return new MemoryMeterObservationHandlerRegistrar();
    }

    static final class RetrievalTraceObservationHandlerRegistrar {}

    static final class MemoryMeterObservationHandlerRegistrar {}
}
