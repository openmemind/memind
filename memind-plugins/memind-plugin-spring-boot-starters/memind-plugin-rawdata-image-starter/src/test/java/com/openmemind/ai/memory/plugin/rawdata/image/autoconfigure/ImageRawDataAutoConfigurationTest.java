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
package com.openmemind.ai.memory.plugin.rawdata.image.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.plugin.ImageRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

class ImageRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    ImageRawDataAutoConfiguration.class));

    @Test
    void registersImageRawDataPlugin() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.containsBean("imageRawDataPlugin")).isTrue();
                });
    }

    @Test
    void applicationObjectMapperCanDeserializeImageRawContentWhenStarterPresent() {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            """
                                            {"type":"image","mimeType":"image/png","description":"cover","metadata":{}}
                                            """,
                                            RawContent.class))
                            .isInstanceOf(ImageContent.class);
                });
    }

    @Test
    void injectsVisionParserIntoPluginWhenChatModelPresent() {
        contextRunner
                .withBean(ChatModel.class, () -> mock(ChatModel.class))
                .run(
                        context -> {
                            var plugin = (ImageRawDataPlugin) context.getBean("imageRawDataPlugin");

                            assertThat(plugin.parsers(pluginContext()))
                                    .singleElement()
                                    .isInstanceOf(ImageContentParser.class);
                        });
    }

    @Test
    void parserRegistrationDoesNotResolveChatModelProvider() {
        var chatModelProvider = mock(org.springframework.beans.factory.ObjectProvider.class);
        var plugin =
                (ImageRawDataPlugin)
                        new ImageRawDataAutoConfiguration()
                                .imageRawDataPlugin(
                                        new ImageRawDataProperties(), chatModelProvider);

        assertThat(plugin.parsers(pluginContext()))
                .singleElement()
                .isInstanceOf(ImageContentParser.class);
        verifyNoInteractions(chatModelProvider);
    }

    @Test
    void parserDisabledLeavesPluginRegisteredWithoutParsers() {
        contextRunner
                .withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withPropertyValues("memind.rawdata.image.parser-enabled=false")
                .run(
                        context -> {
                            var plugin = (ImageRawDataPlugin) context.getBean("imageRawDataPlugin");

                            assertThat(context).hasSingleBean(RawDataPlugin.class);
                            assertThat(plugin.parsers(pluginContext())).isEmpty();
                        });
    }

    @Test
    void bindsImageExtractionOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.image.extraction.source-limit.max-bytes=8192",
                        "memind.rawdata.image.extraction.parsed-limit.max-tokens=2048")
                .run(
                        context -> {
                            var plugin = (ImageRawDataPlugin) context.getBean("imageRawDataPlugin");
                            var options =
                                    readField(plugin, "options", ImageExtractionOptions.class);

                            assertThat(options.sourceLimit().maxBytes()).isEqualTo(8192L);
                            assertThat(options.parsedLimit().maxTokens()).isEqualTo(2048);
                        });
    }

    @Test
    void backsOffCompletelyWhenImageStarterDisabled() {
        contextRunner
                .withPropertyValues("memind.rawdata.image.enabled=false")
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
}
