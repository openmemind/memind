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

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat.MultiChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore({SpringAiLlmAutoConfiguration.class, SpringAiVectorAutoConfiguration.class})
@EnableConfigurationProperties(MemindAiProperties.class)
public class MemindAiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MemindAiClientFactory memindAiClientFactory(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<MultiChatModel> multiChatModelProvider) {
        return new MemindAiClientFactory(chatModelProvider, multiChatModelProvider);
    }
}
