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
package com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class RawDataJacksonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(RawDataJacksonAutoConfiguration.class));

    @Test
    void registersOnlyCoreRawContentTypesWithoutPluginStarters() {
        contextRunner.run(
                context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(
                                    mapper.readValue(
                                            """
                                            {"type":"conversation","messages":[{"role":"USER","content":[{"type":"text","text":"hi"}],"timestamp":null,"userName":null}]}
                                            """,
                                            RawContent.class))
                            .isInstanceOf(ConversationContent.class);
                    assertThatThrownBy(
                                    () ->
                                            mapper.readValue(
                                                    """
                                                    {"type":"tool_call","calls":[{"toolName":"search","input":"{}","output":"ok","status":"SUCCESS","durationMs":1,"inputTokens":1,"outputTokens":1,"contentHash":"abc","calledAt":"2026-04-11T00:00:00Z"}]}
                                                    """,
                                                    RawContent.class))
                            .isInstanceOf(InvalidTypeIdException.class)
                            .hasMessageContaining("tool_call");
                });
    }

    @Test
    void doesNotDeserializePluginOwnedRawContentWithoutMatchingPluginBean() {
        contextRunner.run(
                context ->
                        assertThatThrownBy(
                                        () ->
                                                context.getBean(ObjectMapper.class)
                                                        .readValue(
                                                                """
                                                                {"type":"test_raw","text":"hello","metadata":{}}
                                                                """,
                                                                RawContent.class))
                                .isInstanceOf(InvalidTypeIdException.class)
                                .hasMessageContaining("test_raw"));
    }

    @Test
    void picksUpPluginSubtypeRegistrarsFromSpringContext() {
        contextRunner
                .withBean(RawDataPlugin.class, TestRawDataPlugin::new)
                .run(
                        context -> {
                            ObjectMapper mapper = context.getBean(ObjectMapper.class);

                            assertThat(
                                            mapper.readValue(
                                                    """
                                                    {"type":"test_raw","text":"hello","metadata":{}}
                                                    """,
                                                    RawContent.class))
                                    .isInstanceOf(TestRawContent.class);
                        });
    }

    @Test
    void serverHttpMessageConvertersUseJackson2MapperThatUnderstandsRawContentTypes() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                HttpMessageConvertersAutoConfiguration.class,
                                RawDataJacksonAutoConfiguration.class))
                .run(
                        context -> {
                            HttpMessageConverters.ServerBuilder builder =
                                    HttpMessageConverters.forServer();
                            context.getBeanProvider(
                                            org.springframework.boot.http.converter.autoconfigure
                                                    .ServerHttpMessageConvertersCustomizer.class)
                                    .orderedStream()
                                    .forEach(customizer -> customizer.customize(builder));

                            MappingJackson2HttpMessageConverter jsonConverter =
                                    java.util.stream.StreamSupport.stream(
                                                    builder.build().spliterator(), false)
                                            .filter(
                                                    MappingJackson2HttpMessageConverter.class
                                                            ::isInstance)
                                            .map(MappingJackson2HttpMessageConverter.class::cast)
                                            .findFirst()
                                            .orElseThrow();

                            assertThat(
                                            jsonConverter
                                                    .getObjectMapper()
                                                    .readValue(
                                                            """
                                                            {"type":"conversation","messages":[{"role":"USER","content":[{"type":"text","text":"hi"}],"timestamp":null,"userName":null}]}
                                                            """,
                                                            RawContent.class))
                                    .isInstanceOf(ConversationContent.class);
                        });
    }

    private static final class TestRawDataPlugin implements RawDataPlugin {

        @Override
        public String pluginId() {
            return "test-rawdata-plugin";
        }

        @Override
        public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
            return List.of();
        }

        @Override
        public List<ContentParser> parsers(RawDataPluginContext context) {
            return List.of();
        }

        @Override
        public List<RawContentTypeRegistrar> typeRegistrars() {
            return List.of(() -> Map.of("test_raw", TestRawContent.class));
        }
    }

    private static final class TestRawContent extends RawContent {

        private final String text;
        private final Map<String, Object> metadata;

        @JsonCreator
        private TestRawContent(
                @JsonProperty("text") String text,
                @JsonProperty("metadata") Map<String, Object> metadata) {
            this.text = text;
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public String contentType() {
            return "TEST_RAW";
        }

        @Override
        public String toContentString() {
            return text == null ? "" : text;
        }

        @Override
        public String getContentId() {
            return text == null ? "empty" : text;
        }

        @Override
        public Map<String, Object> contentMetadata() {
            return metadata;
        }
    }
}
