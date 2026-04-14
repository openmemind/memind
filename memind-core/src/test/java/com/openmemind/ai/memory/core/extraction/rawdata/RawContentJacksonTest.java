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
package com.openmemind.ai.memory.core.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;

class RawContentJacksonTest {

    @Test
    void rawContentBaseDoesNotUseAnnotationDrivenSubtypeRegistration() {
        assertThat(RawContent.class.getAnnotation(JsonSubTypes.class)).isNull();
    }

    @Test
    void registerCoreSubtypesSupportsConversationDeserializationWithoutPlugins() throws Exception {
        var mapper = RawContentJackson.registerCoreSubtypes(new ObjectMapper());

        String json = "{\"type\":\"conversation\",\"messages\":[]}";
        RawContent decoded = mapper.readValue(json, RawContent.class);

        assertThat(decoded).isInstanceOf(ConversationContent.class);
    }

    @Test
    void registerCoreSubtypesOnlySupportsConversationWithoutPlugins() {
        var mapper = RawContentJackson.registerCoreSubtypes(new ObjectMapper());

        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        """
                                        {"type":"tool_call","calls":[]}
                                        """,
                                        RawContent.class))
                .isInstanceOf(InvalidTypeIdException.class)
                .hasMessageContaining("tool_call");
    }

    @Test
    void registerPluginSubtypesAppliesRegistrarMappings() throws Exception {
        var mapper =
                RawContentJackson.registerPluginSubtypes(
                        RawContentJackson.registerCoreSubtypes(new ObjectMapper()),
                        List.of(() -> Map.of("plugin_test", PluginTestContent.class)));

        RawContent decoded =
                mapper.readValue(
                        "{\"type\":\"plugin_test\",\"text\":\"hello plugin\"}", RawContent.class);

        assertThat(decoded).isInstanceOf(PluginTestContent.class);
        assertThat(decoded.toContentString()).isEqualTo("hello plugin");
    }

    @Test
    void duplicateSubtypeNamesFailFast() {
        var mapper = new ObjectMapper();

        assertThatThrownBy(
                        () ->
                                RawContentJackson.registerPluginSubtypes(
                                        mapper,
                                        List.of(
                                                () -> Map.of("document", PluginTestContent.class),
                                                () ->
                                                        Map.of(
                                                                "document",
                                                                DuplicatePluginTestContent.class))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }

    private static final class PluginTestContent extends RawContent {

        private final String text;

        @JsonCreator
        private PluginTestContent(@JsonProperty("text") String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public String contentType() {
            return "PLUGIN_TEST";
        }

        @Override
        public String toContentString() {
            return text;
        }

        @Override
        public String getContentId() {
            return text;
        }
    }

    private static final class DuplicatePluginTestContent extends RawContent {

        @Override
        public String contentType() {
            return "PLUGIN_TEST_DUPLICATE";
        }

        @Override
        public String toContentString() {
            return "";
        }

        @Override
        public String getContentId() {
            return "duplicate";
        }
    }
}
