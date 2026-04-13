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
package com.openmemind.ai.memory.plugin.rawdata.image.plugin;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openmemind.ai.memory.plugin.rawdata.image.ImageSemantics;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.processor.ImageContentProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ImageRawDataPluginTest {

    @Test
    void pluginExposesStableIdProcessorAndImageSubtypeRegistrar() {
        RawDataPlugin plugin = new ImageRawDataPlugin();

        assertThat(plugin.pluginId()).isEqualTo("rawdata-image");
        assertThat(plugin.processors(pluginContext()))
                .singleElement()
                .isInstanceOf(ImageContentProcessor.class);
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .extracting(RawContentTypeRegistrar::subtypes)
                .satisfies(
                        mappings ->
                                assertThat(mappings).containsEntry("image", ImageContent.class));
    }

    @Test
    void pluginUsesInjectedImageExtractionOptions() {
        var options =
                new ImageExtractionOptions(
                        new SourceLimitOptions(4096),
                        new ParsedContentLimitOptions(321, null, null, null),
                        new TokenChunkingOptions(111, 222),
                        12);
        var plugin = new ImageRawDataPlugin(options);

        assertThat(
                        readField(plugin, "options", ImageExtractionOptions.class)
                                .chunking()
                                .hardMaxTokens())
                .isEqualTo(222);
        assertThat(plugin.ingestionPolicies())
                .singleElement()
                .satisfies(
                        policy -> {
                            assertThat(policy.governanceType())
                                    .isEqualTo(ImageSemantics.GOVERNANCE_CAPTION_OCR);
                            assertThat(policy.sourceLimit().maxBytes()).isEqualTo(4096L);
                        });
    }

    @Test
    void pluginDoesNotExposeParserDirectly() {
        var plugin = new ImageRawDataPlugin(ImageExtractionOptions.defaults());

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
