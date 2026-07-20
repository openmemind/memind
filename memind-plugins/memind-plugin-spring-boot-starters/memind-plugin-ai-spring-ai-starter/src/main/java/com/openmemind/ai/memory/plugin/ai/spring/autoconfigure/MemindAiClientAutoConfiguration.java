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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;

@AutoConfiguration
@AutoConfigureBefore({SpringAiLlmAutoConfiguration.class, SpringAiVectorAutoConfiguration.class})
@EnableConfigurationProperties(MemindAiProperties.class)
public class MemindAiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MemindAiClientFactory memindAiClientFactory(
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<ChatModelObservationConvention> chatModelObservationConventionProvider,
            ObjectProvider<EmbeddingModelObservationConvention>
                    embeddingModelObservationConventionProvider,
            ObjectProvider<OpenAiHttpClientBuilderCustomizer> openAiHttpClientBuilderCustomizers,
            ObjectProvider<AnthropicHttpClientBuilderCustomizer>
                    anthropicHttpClientBuilderCustomizers,
            Environment environment) {
        ObservationRegistry observationRegistry =
                observationRegistryProvider.getIfAvailable(
                        MemindAiClientFactory::defaultObservationRegistry);
        return new MemindAiClientFactory(
                retryTemplateProvider.getIfAvailable(MemindAiClientFactory::defaultRetryTemplate),
                observationRegistry,
                toolCallingManagerProvider.getIfAvailable(
                        () -> MemindAiClientFactory.defaultToolCallingManager(observationRegistry)),
                meterRegistryProvider.getIfAvailable(),
                chatModelObservationConventionProvider.getIfAvailable(),
                embeddingModelObservationConventionProvider.getIfAvailable(),
                openAiHttpClientBuilderCustomizers.orderedStream().toList(),
                anthropicHttpClientBuilderCustomizers.orderedStream().toList(),
                environment);
    }
}
