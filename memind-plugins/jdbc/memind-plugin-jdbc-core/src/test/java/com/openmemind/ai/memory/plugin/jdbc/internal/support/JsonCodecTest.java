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
package com.openmemind.ai.memory.plugin.jdbc.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.ToolCallRawContentTypeRegistrar;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class JsonCodecTest {

    private final JsonCodec jsonCodec = new JsonCodec();

    @Test
    void roundTripsTypedJsonWithInstant() {
        SamplePayload payload = new SamplePayload("alpha", Instant.parse("2026-03-22T00:00:00Z"));

        String json = jsonCodec.toJson(payload);
        SamplePayload restored = jsonCodec.fromJson(json, SamplePayload.class);

        assertThat(restored).isEqualTo(payload);
    }

    @Test
    void roundTripsGenericMapJson() {
        Map<String, Object> payload = Map.of("name", "alice", "version", 1);

        String json = jsonCodec.toJson(payload);
        Map<String, Object> restored =
                jsonCodec.fromJson(json, new TypeReference<Map<String, Object>>() {});

        assertThat(restored).containsEntry("name", "alice");
        assertThat(restored.get("version")).isEqualTo(1);
    }

    @Test
    void roundTripsJsonArray() {
        List<String> payload = List.of("alpha", "beta");

        String json = jsonCodec.toJson(payload);
        List<String> restored = jsonCodec.fromJson(json, new TypeReference<List<String>>() {});

        assertThat(restored).containsExactly("alpha", "beta");
    }

    @Test
    void defaultCodecRoundTripsCoreConversationRawContent() {
        RawContent payload = new ConversationContent(List.of(Message.user("hello")));

        String json = jsonCodec.toJson(payload);
        RawContent restored = jsonCodec.fromJson(json, RawContent.class);

        assertThat(restored).isInstanceOf(ConversationContent.class);
        assertThat(restored.toContentString()).contains("hello");
    }

    @Test
    void codecRoundTripsToolCallWhenPluginSubtypeIsExplicitlyRegistered() {
        ObjectMapper mapper = JsonCodec.createDefaultObjectMapper();
        mapper = RawContentJackson.registerCoreSubtypes(mapper);
        mapper =
                RawContentJackson.registerPluginSubtypes(
                        mapper, List.of(new ToolCallRawContentTypeRegistrar()));
        JsonCodec codec = new JsonCodec(mapper);
        RawContent payload =
                new ToolCallContent(
                        List.of(
                                new ToolCallRecord(
                                        "search",
                                        "{}",
                                        "ok",
                                        "SUCCESS",
                                        1L,
                                        1,
                                        1,
                                        "abc",
                                        Instant.parse("2026-04-12T00:00:00Z"))));

        String json = codec.toJson(payload);
        RawContent restored = codec.fromJson(json, RawContent.class);

        assertThat(restored).isInstanceOf(ToolCallContent.class);
        assertThat(((ToolCallContent) restored).calls())
                .singleElement()
                .extracting(ToolCallRecord::toolName, ToolCallRecord::status)
                .containsExactly("search", "SUCCESS");
    }

    record SamplePayload(String name, Instant createdAt) {}

    private static final class TestRawContent extends RawContent {

        private final String text;

        @JsonCreator
        private TestRawContent(@JsonProperty("text") String text) {
            this.text = text == null ? "" : text;
        }

        @JsonProperty("text")
        private String text() {
            return text;
        }

        @Override
        public String contentType() {
            return "TEST_RAW";
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
}
