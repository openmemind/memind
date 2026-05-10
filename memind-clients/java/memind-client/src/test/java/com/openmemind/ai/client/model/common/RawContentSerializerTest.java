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
package com.openmemind.ai.client.model.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentSerializerTest {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void conversationContent_serializesWithRegisteredTypeField() throws Exception {
        ConversationContent content =
                ConversationContent.of(List.of(Message.user("hello"), Message.assistant("hi")));

        String json = mapper.writeValueAsString(content);

        assertThat(json).contains("\"type\":\"conversation\"");
        assertThat(json).contains("\"messages\":[");
        assertThat(json).contains("\"role\":\"USER\"");
    }

    @Test
    void mapRawContent_serializesPropertiesFlat() throws Exception {
        MapRawContent content =
                MapRawContent.of(
                        "document",
                        Map.of("fileName", "test.pdf", "mimeType", "application/pdf"));

        String json = mapper.writeValueAsString(content);

        assertThat(json).contains("\"type\":\"document\"");
        assertThat(json).contains("\"fileName\":\"test.pdf\"");
        assertThat(json).contains("\"mimeType\":\"application/pdf\"");
    }
}
