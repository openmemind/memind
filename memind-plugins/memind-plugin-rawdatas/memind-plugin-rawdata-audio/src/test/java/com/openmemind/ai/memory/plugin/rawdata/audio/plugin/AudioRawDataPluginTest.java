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
package com.openmemind.ai.memory.plugin.rawdata.audio.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.audio.AudioSemantics;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.processor.AudioContentProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AudioRawDataPluginTest {

    @Test
    void pluginExposesStableIdProcessorAndAudioSubtypeRegistrar() {
        RawDataPlugin plugin = new AudioRawDataPlugin();

        assertThat(plugin.pluginId()).isEqualTo("rawdata-audio");
        assertThat(plugin.processors(pluginContext()))
                .singleElement()
                .isInstanceOf(AudioContentProcessor.class);
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .extracting(RawContentTypeRegistrar::subtypes)
                .satisfies(
                        mappings ->
                                assertThat(mappings).containsEntry("audio", AudioContent.class));
    }

    @Test
    void pluginUsesInjectedAudioExtractionOptions() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(8192),
                        new ParsedContentLimitOptions(999, null, null, Duration.ofMinutes(10)),
                        777,
                        new TokenChunkingOptions(333, 444));
        var plugin = new AudioRawDataPlugin(options);

        assertThat(
                        readField(plugin, "options", AudioExtractionOptions.class)
                                .parsedLimit()
                                .maxTokens())
                .isEqualTo(999);
        assertThat(
                        readField(plugin, "options", AudioExtractionOptions.class)
                                .wholeTranscriptMaxTokens())
                .isEqualTo(777);
        assertThat(plugin.ingestionPolicies())
                .singleElement()
                .satisfies(
                        policy -> {
                            assertThat(policy.governanceType())
                                    .isEqualTo(AudioSemantics.GOVERNANCE_TRANSCRIPT);
                            assertThat(policy.sourceLimit().maxBytes()).isEqualTo(8192L);
                        });
    }

    @Test
    void pluginDoesNotExposeParserDirectly() {
        var plugin = new AudioRawDataPlugin(AudioExtractionOptions.defaults());

        assertThat(plugin.parsers(pluginContext())).isEmpty();
    }

    @Test
    void pluginExposesProvidedParsers() {
        ContentParser parser = mock(ContentParser.class);
        when(parser.parserId()).thenReturn("audio-transcription");

        var plugin = new AudioRawDataPlugin(AudioExtractionOptions.defaults(), List.of(parser));

        assertThat(plugin.parsers(pluginContext())).containsExactly(parser);
    }

    @Test
    void pluginWithoutSuppliedParserExposesNoParsers() {
        var plugin = new AudioRawDataPlugin(AudioExtractionOptions.defaults());

        assertThat(plugin.parsers(pluginContext())).isEmpty();
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
