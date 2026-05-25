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
package com.openmemind.ai.memory.plugin.rawdata.agent.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.agent.AgentRawContentTypeRegistrar;
import com.openmemind.ai.memory.plugin.rawdata.agent.processor.AgentTimelineContentProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentRawDataPluginTest {

    @Test
    void pluginShouldExposeStableIdSubtypeRegistrarAndProcessor() {
        RawDataPlugin plugin = new AgentRawDataPlugin();

        assertThat(plugin.pluginId()).isEqualTo("rawdata-agent");
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .isInstanceOf(AgentRawContentTypeRegistrar.class);
        assertThat(plugin.typeRegistrars())
                .extracting(registrar -> registrar.subtypes())
                .anySatisfy(map -> assertThat(map).containsKey("agent_timeline"));
        assertThat(plugin.processors(pluginContext()))
                .singleElement()
                .isInstanceOf(AgentTimelineContentProcessor.class);
    }

    private static RawDataPluginContext pluginContext() {
        return new RawDataPluginContext(
                new ChatClientRegistry(noopClient(), Map.of()),
                PromptRegistry.EMPTY,
                MemoryBuildOptions.defaults());
    }

    private static StructuredChatClient noopClient() {
        return new StructuredChatClient() {
            @Override
            public Mono<String> call(List<ChatMessage> messages) {
                return Mono.error(new UnsupportedOperationException("not used by this test"));
            }

            @Override
            public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
                return Mono.error(new UnsupportedOperationException("not used by this test"));
            }
        };
    }
}
