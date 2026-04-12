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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.plugin.ImageRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
    void bindsImageExtractionOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.image.extraction.source-limit.max-bytes=8192",
                        "memind.rawdata.image.extraction.caption-ocr-merge-max-tokens=256")
                .run(
                        context -> {
                            var plugin = (ImageRawDataPlugin) context.getBean("imageRawDataPlugin");

                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            ImageExtractionOptions.class)
                                                    .sourceLimit()
                                                    .maxBytes())
                                    .isEqualTo(8192L);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            ImageExtractionOptions.class)
                                                    .captionOcrMergeMaxTokens())
                                    .isEqualTo(256);
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
}
