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
package com.openmemind.ai.memory.plugin.rawdata.document.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.document.plugin.DocumentRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure.RawDataJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class DocumentRawDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    RawDataJacksonAutoConfiguration.class,
                                    DocumentRawDataAutoConfiguration.class,
                                    DocumentNativeTextParserAutoConfiguration.class,
                                    DocumentTikaParserAutoConfiguration.class));

    @Test
    void registersDocumentRawDataPluginAndContentParsers() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RawDataPlugin.class);
                    assertThat(context.getBeansOfType(ContentParser.class))
                            .containsKeys(
                                    "documentNativeTextContentParser", "tikaDocumentContentParser");
                });
    }

    @Test
    void applicationObjectMapperCanDeserializeDocumentRawContentWhenSharedJacksonStarterPresent() {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            """
                                            {"type":"document","title":"Guide","mimeType":"text/plain","parsedText":"hello","sections":[],"metadata":{}}
                                            """,
                                            RawContent.class))
                            .isInstanceOf(DocumentContent.class);
                });
    }

    @Test
    void bindsDocumentExtractionOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.document.extraction.binary-source-limit.max-bytes=4096",
                        "memind.rawdata.document.extraction.whole-document-max-tokens=1024",
                        "memind.rawdata.document.extraction.binary-chunking.hard-max-tokens=1600",
                        "memind.rawdata.document.extraction.binary-min-chunk-tokens=256",
                        "memind.rawdata.document.extraction.pdf-max-merged-pages=5")
                .run(
                        context -> {
                            var plugin =
                                    (DocumentRawDataPlugin)
                                            context.getBean("documentRawDataPlugin");

                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            DocumentExtractionOptions.class)
                                                    .binarySourceLimit()
                                                    .maxBytes())
                                    .isEqualTo(4096L);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            DocumentExtractionOptions.class)
                                                    .wholeDocumentMaxTokens())
                                    .isEqualTo(1024);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            DocumentExtractionOptions.class)
                                                    .binaryChunking()
                                                    .hardMaxTokens())
                                    .isEqualTo(1600);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            DocumentExtractionOptions.class)
                                                    .binaryMinChunkTokens())
                                    .isEqualTo(256);
                            assertThat(
                                            readField(
                                                            plugin,
                                                            "options",
                                                            DocumentExtractionOptions.class)
                                                    .pdfMaxMergedPages())
                                    .isEqualTo(5);
                        });
    }

    @Test
    void bindsDocumentCaptionOptionsIntoPluginBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rawdata.document.extraction.llm-caption-enabled=false",
                        "memind.rawdata.document.extraction.caption-concurrency=2",
                        "memind.rawdata.document.extraction.fallback-caption-max-length=128")
                .run(
                        context -> {
                            var plugin =
                                    (DocumentRawDataPlugin)
                                            context.getBean("documentRawDataPlugin");
                            var options =
                                    readField(plugin, "options", DocumentExtractionOptions.class);

                            assertThat(options.llmCaptionEnabled()).isFalse();
                            assertThat(options.captionConcurrency()).isEqualTo(2);
                            assertThat(options.fallbackCaptionMaxLength()).isEqualTo(128);
                        });
    }

    @Test
    void canDisableNativeTextParserOnly() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.native-text-enabled=false")
                .run(
                        context ->
                                assertThat(context.getBeansOfType(ContentParser.class))
                                        .containsKey("tikaDocumentContentParser")
                                        .doesNotContainKey("documentNativeTextContentParser"));
    }

    @Test
    void canDisableTikaParserOnly() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.tika-enabled=false")
                .run(
                        context ->
                                assertThat(context.getBeansOfType(ContentParser.class))
                                        .containsKey("documentNativeTextContentParser")
                                        .doesNotContainKey("tikaDocumentContentParser"));
    }

    @Test
    void backsOffCompletelyWhenDocumentStarterDisabled() {
        contextRunner
                .withPropertyValues("memind.rawdata.document.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RawDataPlugin.class);
                            assertThat(context.getBeansOfType(ContentParser.class)).isEmpty();
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
