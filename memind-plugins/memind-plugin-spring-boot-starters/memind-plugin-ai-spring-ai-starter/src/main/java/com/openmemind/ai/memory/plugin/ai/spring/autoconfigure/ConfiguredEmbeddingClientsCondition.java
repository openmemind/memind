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

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches only when Memind-specific embedding selection is configured.
 *
 * <p>Without {@code memind.ai.embedding.default}, Memind does not create the primary
 * {@code memindEmbeddingModel} alias. The vector auto-configuration therefore uses the default
 * {@code EmbeddingModel} bean already provided by Spring AI.
 */
final class ConfiguredEmbeddingClientsCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
            ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean configured =
                StringUtils.hasText(
                                context.getEnvironment().getProperty("memind.ai.embedding.default"))
                        || StringUtils.hasText(
                                context.getEnvironment().getProperty("memind.ai.embedding.client"));
        ConditionMessage message =
                ConditionMessage.forCondition("configured memind AI embedding default")
                        .because(
                                configured
                                        ? "memind.ai.embedding.default is configured"
                                        : "memind.ai.embedding.default is not configured");
        return configured ? ConditionOutcome.match(message) : ConditionOutcome.noMatch(message);
    }
}
