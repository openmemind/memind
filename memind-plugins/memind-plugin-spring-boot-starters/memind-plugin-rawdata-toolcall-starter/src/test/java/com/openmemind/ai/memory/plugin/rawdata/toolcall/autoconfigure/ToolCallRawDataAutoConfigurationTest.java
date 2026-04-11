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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
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
    void registersToolCallRawDataPluginAndKeepsToolCallJsonBinding() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
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
    void disablingStarterRemovesPluginBeanButNotToolCallJsonCompatibility() {
        contextRunner
                .withPropertyValues("memind.rawdata.toolcall.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                            ObjectMapper mapper = context.getBean(ObjectMapper.class);
                            assertThat(
                                            mapper.readValue(
                                                    """
                                                    {"type":"tool_call","calls":[]}
                                                    """,
                                                    RawContent.class))
                                    .isInstanceOf(ToolCallContent.class);
                        });
    }
}
