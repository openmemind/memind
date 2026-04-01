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
package com.openmemind.ai.memory.server.configuration;

import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemindServerRerankProperties.class)
public class MemindServerRerankConfiguration {

    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    @Conditional(RerankPropertiesConfiguredCondition.class)
    Reranker reranker(MemindServerRerankProperties properties) {
        if (StringUtils.hasText(properties.getModel())) {
            return new LlmReranker(
                    properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
        }
        return new LlmReranker(properties.getBaseUrl(), properties.getApiKey());
    }

    static final class RerankPropertiesConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(
                            context.getEnvironment().getProperty("memind.rerank.base-url"))
                    && StringUtils.hasText(
                            context.getEnvironment().getProperty("memind.rerank.api-key"));
        }
    }
}
