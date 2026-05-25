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
package com.openmemind.ai.memory.plugin.rawdata.agent.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentRawDataOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.plugin.AgentRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;

class AgentRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    AgentRawDataAutoConfiguration.class));

    @Test
    void registersAgentRawDataPluginAndAgentTimelineJsonBinding() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.getBean("agentRawDataPlugin"))
                            .isInstanceOf(AgentRawDataPlugin.class);

                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(
                                    mapper.readValue(
                                            """
                                            {
                                              "type": "agent_timeline",
                                              "sourceClient": "claude-code",
                                              "sessionId": "s",
                                              "agentTurnId": "s-agent-turn-1-1",
                                              "timelineId": "t",
                                              "events": []
                                            }
                                            """,
                                            RawContent.class))
                            .isInstanceOf(AgentTimelineContent.class);
                });
    }

    @Test
    void bindsAgentOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.agent.chunking.target-episode-tokens=1600",
                        "memind.rawdata.agent.chunking.hard-max-tokens=3200",
                        "memind.rawdata.agent.chunking.max-events-per-episode=40",
                        "memind.rawdata.agent.chunking.max-event-gap=PT10M",
                        "memind.rawdata.agent.extraction.extract-playbook=false",
                        "memind.rawdata.agent.extraction.min-events-for-extraction=2",
                        "memind.rawdata.agent.privacy.max-input-chars=1200",
                        "memind.rawdata.agent.privacy.capture-file-content=true",
                        "memind.rawdata.agent.privacy.deny-path-patterns[0]=.env",
                        "memind.rawdata.agent.privacy.deny-path-patterns[1]=*.secret")
                .run(
                        context -> {
                            var plugin = (AgentRawDataPlugin) context.getBean("agentRawDataPlugin");
                            AgentRawDataOptions options =
                                    readField(plugin, "options", AgentRawDataOptions.class);

                            assertThat(options.chunking().targetEpisodeTokens()).isEqualTo(1600);
                            assertThat(options.chunking().hardMaxTokens()).isEqualTo(3200);
                            assertThat(options.chunking().maxEventsPerEpisode()).isEqualTo(40);
                            assertThat(options.chunking().maxEventGap()).hasMinutes(10);
                            assertThat(options.extraction().extractPlaybook()).isFalse();
                            assertThat(options.extraction().minEventsForExtraction()).isEqualTo(2);
                            assertThat(options.privacy().maxInputChars()).isEqualTo(1200);
                            assertThat(options.privacy().captureFileContent()).isTrue();
                            assertThat(options.privacy().denyPathPatterns())
                                    .containsExactly(".env", "*.secret");
                        });
    }

    @Test
    void disablingStarterRemovesPluginAndAgentTimelineJsonBinding() {
        contextRunner
                .withPropertyValues("memind.rawdata.agent.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);

                            assertThatThrownBy(
                                            () ->
                                                    context.getBean(ObjectMapper.class)
                                                            .readValue(
                                                                    """
                                                                    {
                                                                      "type": "agent_timeline",
                                                                      "sourceClient": "claude-code",
                                                                      "sessionId": "s",
                                                                      "timelineId": "t",
                                                                      "events": []
                                                                    }
                                                                    """,
                                                                    RawContent.class))
                                    .isInstanceOf(InvalidTypeIdException.class)
                                    .hasMessageContaining("agent_timeline");
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
