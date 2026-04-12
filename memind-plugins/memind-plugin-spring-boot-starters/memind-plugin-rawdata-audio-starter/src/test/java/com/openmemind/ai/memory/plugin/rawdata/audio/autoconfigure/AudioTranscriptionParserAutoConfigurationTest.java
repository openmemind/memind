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

import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.audio.parser.TranscriptionAudioContentParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("AudioTranscriptionParserAutoConfiguration Test")
class AudioTranscriptionParserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AudioTranscriptionParserAutoConfiguration.class));

    @Test
    @DisplayName("register audio parser when TranscriptionModel is present")
    void registersAudioParserWhenTranscriptionModelPresent() {
        contextRunner
                .withBean(TranscriptionModel.class, TestBeans::transcriptionModel)
                .run(
                        context -> {
                            assertThat(context.getBeansOfType(ContentParser.class))
                                    .containsKey("audioTranscriptionContentParser");
                            assertThat(context.getBean("audioTranscriptionContentParser"))
                                    .isInstanceOf(TranscriptionAudioContentParser.class);
                        });
    }

    @Test
    @DisplayName("back off audio parser when parser is disabled")
    void backsOffAudioParserWhenParserDisabled() {
        contextRunner
                .withBean(TranscriptionModel.class, TestBeans::transcriptionModel)
                .withPropertyValues("memind.rawdata.audio.parser-enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ContentParser.class)).isEmpty());
    }

    private static final class TestBeans {
        private static TranscriptionModel transcriptionModel() {
            return mock(TranscriptionModel.class);
        }
    }
}
