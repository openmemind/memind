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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageTest {

    private final ObjectMapper mapper =
            JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void userMessage_serializesCorrectly() throws Exception {
        Message msg = Message.user("hello");
        String json = mapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"USER\"");
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hello\"");
    }

    @Test
    void assistantMessage_serializesCorrectly() throws Exception {
        Message msg = Message.assistant("hi there", Instant.parse("2026-01-01T00:00:00Z"));
        String json = mapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"ASSISTANT\"");
        assertThat(json).contains("\"text\":\"hi there\"");
        assertThat(json).contains("\"timestamp\":\"2026-01-01T00:00:00Z\"");
    }
}
