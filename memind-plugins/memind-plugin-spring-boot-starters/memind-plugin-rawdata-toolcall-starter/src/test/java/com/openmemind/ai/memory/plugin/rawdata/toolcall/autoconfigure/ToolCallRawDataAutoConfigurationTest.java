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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.config.ToolCallChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.plugin.ToolCallRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.stats.ToolCallStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ToolCallRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    ToolCallRawDataAutoConfiguration.class));

    @Test
    void registersToolCallRawDataPluginStatsBeanAndToolCallJsonBinding() {
        contextRunner
                .withBean(MemoryStore.class, () -> org.mockito.Mockito.mock(MemoryStore.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(RawDataPlugin.class);
                            assertThat(context).hasSingleBean(ToolCallStatsService.class);
                            ObjectMapper mapper = context.getBean(ObjectMapper.class);
                            assertThat(
                                            mapper.readValue(
                                                    """
                                                    {"type":"tool_call","calls":[{"toolName":"search","input":"{}","output":"ok","status":"SUCCESS","durationMs":1,"inputTokens":1,"outputTokens":1,"contentHash":"abc","calledAt":"2026-04-12T00:00:00Z"}]}
                                                    """,
                                                    RawContent.class))
                                    .isInstanceOf(ToolCallContent.class);
                        });
    }

    @Test
    void bindsToolCallChunkingOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.toolcall.chunking.hard-max-tokens=1800",
                        "memind.rawdata.toolcall.chunking.max-time-window=PT2M")
                .run(
                        context -> {
                            var plugin =
                                    (ToolCallRawDataPlugin)
                                            context.getBean("toolCallRawDataPlugin");

                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            ToolCallChunkingOptions.class)
                                                    .hardMaxTokens())
                                    .isEqualTo(1800);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            ToolCallChunkingOptions.class)
                                                    .maxTimeWindow())
                                    .hasMinutes(2);
                        });
    }

    @Test
    void disablingStarterRemovesPluginStatsBeanAndToolCallJsonBinding() {
        contextRunner
                .withBean(MemoryStore.class, () -> org.mockito.Mockito.mock(MemoryStore.class))
                .withPropertyValues("memind.rawdata.toolcall.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                            assertThat(context).doesNotHaveBean(ToolCallStatsService.class);

                            assertThatThrownBy(
                                            () ->
                                                    context.getBean(ObjectMapper.class)
                                                            .readValue(
                                                                    """
                                                                    {"type":"tool_call","calls":[]}
                                                                    """,
                                                                    RawContent.class))
                                    .isInstanceOf(InvalidTypeIdException.class)
                                    .hasMessageContaining("tool_call");
                        });
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
