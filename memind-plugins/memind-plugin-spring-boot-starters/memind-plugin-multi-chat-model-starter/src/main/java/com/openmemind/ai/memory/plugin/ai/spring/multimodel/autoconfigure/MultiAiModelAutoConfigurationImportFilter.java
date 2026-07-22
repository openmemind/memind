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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure;

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

public final class MultiAiModelAutoConfigurationImportFilter
        implements AutoConfigurationImportFilter {

    private static final Set<String> FILTERED_SINGLE_MODEL_AUTO_CONFIGURATIONS =
            Set.of(
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
                    "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration",
                    "org.springframework.ai.model.google.genai.autoconfigure.chat."
                            + "GoogleGenAiChatAutoConfiguration",
                    "org.springframework.ai.model.google.genai.autoconfigure.embedding."
                            + "GoogleGenAiEmbeddingConnectionAutoConfiguration",
                    "org.springframework.ai.model.google.genai.autoconfigure.embedding."
                            + "GoogleGenAiTextEmbeddingAutoConfiguration");

    @Override
    public boolean @NonNull [] match(
            String[] autoConfigurationClasses,
            @NonNull AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String configurationClass = autoConfigurationClasses[index];
            if (configurationClass == null) {
                continue;
            }
            matches[index] =
                    !FILTERED_SINGLE_MODEL_AUTO_CONFIGURATIONS.contains(configurationClass);
        }
        return matches;
    }
}
