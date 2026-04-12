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
package com.openmemind.ai.memory.plugin.rawdata.audio.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.plugin.AudioRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AudioRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    AudioRawDataAutoConfiguration.class));

    @Test
    void registersAudioRawDataPlugin() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.containsBean("audioRawDataPlugin")).isTrue();
                });
    }

    @Test
    void applicationObjectMapperCanDeserializeAudioRawContentWhenStarterPresent() {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            """
                                            {"type":"audio","mimeType":"audio/mpeg","transcript":"hello","segments":[],"metadata":{}}
                                            """,
                                            RawContent.class))
                            .isInstanceOf(AudioContent.class);
                });
    }

    @Test
    void bindsAudioExtractionOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.audio.extraction.source-limit.max-bytes=4096",
                        "memind.rawdata.audio.extraction.chunking.hard-max-tokens=1200")
                .run(
                        context -> {
                            var plugin = (AudioRawDataPlugin) context.getBean("audioRawDataPlugin");

                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            AudioExtractionOptions.class)
                                                    .sourceLimit()
                                                    .maxBytes())
                                    .isEqualTo(4096L);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            AudioExtractionOptions.class)
                                                    .chunking()
                                                    .hardMaxTokens())
                                    .isEqualTo(1200);
                        });
    }

    @Test
    void backsOffCompletelyWhenAudioStarterDisabled() {
        contextRunner
                .withPropertyValues("memind.rawdata.audio.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RawDataPlugin.class));
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
