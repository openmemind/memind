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

import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.audio.parser.TranscriptionAudioContentParser;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        afterName =
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration")
@ConditionalOnClass({TranscriptionAudioContentParser.class, TranscriptionModel.class})
@ConditionalOnBean(TranscriptionModel.class)
@EnableConfigurationProperties(AudioRawDataProperties.class)
@ConditionalOnProperty(
        prefix = "memind.rawdata.audio",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "memind.rawdata.audio",
        name = "parser-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AudioTranscriptionParserAutoConfiguration {

    @Bean("audioTranscriptionContentParser")
    @ConditionalOnMissingBean(name = "audioTranscriptionContentParser")
    ContentParser audioTranscriptionContentParser(TranscriptionModel transcriptionModel) {
        return new TranscriptionAudioContentParser(transcriptionModel);
    }
}
