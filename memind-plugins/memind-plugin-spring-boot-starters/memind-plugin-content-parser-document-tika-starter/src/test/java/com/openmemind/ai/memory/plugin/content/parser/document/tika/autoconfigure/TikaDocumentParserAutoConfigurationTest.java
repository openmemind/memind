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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TikaDocumentParserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    TikaDocumentParserAutoConfiguration.class));

    @Test
    void legacyStarterKeepsHistoricalPropertyAndBeanName() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.getBeansOfType(ContentParser.class))
                            .containsKey("tikaDocumentContentParser")
                            .doesNotContainKey("documentNativeTextContentParser");
                });
    }

    @Test
    void legacyStarterProvidesDocumentSubtypeThroughSharedRawDataJacksonPath() {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            """
                                            {"type":"document","title":"Guide","mimeType":"application/pdf","parsedText":"hello","sections":[],"metadata":{}}
                                            """,
                                            RawContent.class))
                            .isInstanceOf(DocumentContent.class);
                });
    }

    @Test
    void legacyStarterStillHonorsOldDisableProperty() {
        contextRunner
                .withPropertyValues("memind.parser.document.tika.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                            assertThat(context.getBeansOfType(ContentParser.class)).isEmpty();
                        });
    }
}
