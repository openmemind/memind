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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.ToolCallRawContentTypeRegistrar;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.config.ToolCallChunkingOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ToolCallRawDataPluginTest {

    @Test
    void pluginExposesStableIdProcessorAndSubtypeRegistrar() {
        RawDataPlugin plugin = new ToolCallRawDataPlugin();
        StructuredChatClient client = new NoopStructuredChatClient();
        ChatClientRegistry registry = new ChatClientRegistry(client, Map.of());
        RawDataPluginContext context =
                new RawDataPluginContext(
                        registry, PromptRegistry.EMPTY, MemoryBuildOptions.defaults());

        assertThat(plugin.pluginId()).isEqualTo("rawdata-toolcall");
        assertThat(plugin.processors(context))
                .singleElement()
                .extracting(processor -> processor.contentType())
                .isEqualTo("TOOL_CALL");
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .isInstanceOf(ToolCallRawContentTypeRegistrar.class);
    }

    @Test
    void pluginUsesInjectedToolCallChunkingOptions() {
        var options = new ToolCallChunkingOptions(120, 160, Duration.ofMinutes(2));
        var plugin = new ToolCallRawDataPlugin(options);

        assertThat(readField(plugin, "options", ToolCallChunkingOptions.class).maxTimeWindow())
                .isEqualTo(Duration.ofMinutes(2));
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
