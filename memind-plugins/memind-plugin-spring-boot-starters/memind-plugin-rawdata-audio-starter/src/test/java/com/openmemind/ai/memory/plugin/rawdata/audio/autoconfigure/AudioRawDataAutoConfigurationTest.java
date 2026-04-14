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
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.parser.TranscriptionAudioContentParser;
import com.openmemind.ai.memory.plugin.rawdata.audio.plugin.AudioRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

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
                        "memind.rawdata.audio.extraction.chunking.hard-max-tokens=1200",
                        "memind.rawdata.audio.extraction.whole-transcript-max-tokens=" + "2048")
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
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            AudioExtractionOptions.class)
                                                    .wholeTranscriptMaxTokens())
                                    .isEqualTo(2048);
                        });
    }

    @Test
    void backsOffCompletelyWhenAudioStarterDisabled() {
        contextRunner
                .withPropertyValues("memind.rawdata.audio.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RawDataPlugin.class));
    }

    @Test
    void parserDisabledStillRegistersPlugin() {
        contextRunner
                .withBean(TranscriptionModel.class, () -> mock(TranscriptionModel.class))
                .withPropertyValues("memind.rawdata.audio.parser-enabled=false")
                .run(
                        context -> {
                            var plugin = (AudioRawDataPlugin) context.getBean("audioRawDataPlugin");

                            assertThat(context).hasSingleBean(RawDataPlugin.class);
                            assertThat(readField(plugin, "parsers", List.class)).isEmpty();
                            assertThat(context).doesNotHaveBean(ContentParser.class);
                        });
    }

    @Test
    void pluginOwnsTranscriptionParserWhenModelPresent() {
        contextRunner
                .withBean(TranscriptionModel.class, () -> mock(TranscriptionModel.class))
                .run(
                        context -> {
                            var plugin = (AudioRawDataPlugin) context.getBean("audioRawDataPlugin");

                            assertThat(readField(plugin, "parsers", List.class))
                                    .singleElement()
                                    .isInstanceOf(TranscriptionAudioContentParser.class);
                            assertThat(context).doesNotHaveBean(ContentParser.class);
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
