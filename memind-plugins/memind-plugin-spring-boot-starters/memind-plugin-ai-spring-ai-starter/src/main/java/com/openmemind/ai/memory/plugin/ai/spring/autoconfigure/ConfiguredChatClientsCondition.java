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
 * Matches only when Memind-specific chat routing is configured.
 *
 * <p>Without {@code memind.ai.chat.default}, Memind backs off and lets the normal Spring AI
 * {@code ChatClient.Builder} path create the default {@code StructuredChatClient}. This condition
 * gates only the extra {@code MemindChatClients} routing bean used by slot-specific configuration.
 */
final class ConfiguredChatClientsCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
            ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean configured =
                StringUtils.hasText(context.getEnvironment().getProperty("memind.ai.chat.default"))
                        || StringUtils.hasText(
                                context.getEnvironment()
                                        .getProperty("memind.ai.chat.default-client"));
        ConditionMessage message =
                ConditionMessage.forCondition("configured memind AI chat default")
                        .because(
                                configured
                                        ? "memind.ai.chat.default is configured"
                                        : "memind.ai.chat.default is not configured");
        return configured ? ConditionOutcome.match(message) : ConditionOutcome.noMatch(message);
    }
}
