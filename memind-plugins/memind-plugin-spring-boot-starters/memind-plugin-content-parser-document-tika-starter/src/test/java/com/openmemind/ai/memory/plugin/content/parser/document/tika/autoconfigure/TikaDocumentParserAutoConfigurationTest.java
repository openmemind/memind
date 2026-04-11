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
package com.openmemind.ai.memory.plugin.content.parser.document.tika.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.plugin.content.parser.document.tika.TikaDocumentContentParser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class TikaDocumentParserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(TikaDocumentParserAutoConfiguration.class));

    @Test
    void registersDefaultTikaParserWhenEnabled() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(ContentParser.class);
                    assertThat(context.getBean(ContentParser.class))
                            .isInstanceOf(TikaDocumentContentParser.class);
                });
    }

    @Test
    void registersAlongsideUnrelatedContentParser() {
        contextRunner
                .withUserConfiguration(CustomParserConfiguration.class)
                .run(
                        context -> {
                            assertThat(context.getBeansOfType(ContentParser.class))
                                    .hasSize(2)
                                    .containsKeys(
                                            "tikaDocumentContentParser", "customContentParser");
                        });
    }

    @Test
    void backsOffWhenUserProvidesNamedTikaDocumentParser() {
        contextRunner
                .withUserConfiguration(NamedTikaParserConfiguration.class)
                .run(
                        context -> {
                            assertThat(context.getBeansOfType(ContentParser.class))
                                    .hasSize(1)
                                    .containsKey("tikaDocumentContentParser");
                            assertThat(context.getBean("tikaDocumentContentParser"))
                                    .isSameAs(
                                            context.getBean(NamedTikaParserConfiguration.class)
                                                    .tikaDocumentContentParser);
                        });
    }

    @Test
    void canBeDisabledByProperty() {
        contextRunner
                .withPropertyValues("memind.parser.document.tika.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ContentParser.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomParserConfiguration {

        @Bean
        ContentParser customContentParser() {
            return new ContentParser() {
                @Override
                public String parserId() {
                    return "custom-image-parser";
                }

                @Override
                public String contentType() {
                    return ContentTypes.IMAGE;
                }

                @Override
                public String contentProfile() {
                    return "image.caption-ocr";
                }

                @Override
                public Set<String> supportedMimeTypes() {
                    return Set.of("image/png");
                }

                @Override
                public reactor.core.publisher.Mono<RawContent> parse(
                        byte[] data, SourceDescriptor source) {
                    throw new UnsupportedOperationException("test stub");
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NamedTikaParserConfiguration {

        private final ContentParser tikaDocumentContentParser = new TikaDocumentContentParser();

        @Bean("tikaDocumentContentParser")
        ContentParser tikaDocumentContentParser() {
            return tikaDocumentContentParser;
        }
    }
}
