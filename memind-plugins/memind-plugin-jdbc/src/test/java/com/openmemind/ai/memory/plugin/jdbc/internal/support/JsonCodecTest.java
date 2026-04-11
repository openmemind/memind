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

import com.fasterxml.jackson.core.type.TypeReference;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    void defaultCodecRoundTripsBuiltinRawContent() {
        RawContent payload =
                new ImageContent(
                        "image/png",
                        "dashboard screenshot",
                        "Total revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        String json = jsonCodec.toJson(payload);
        RawContent restored = jsonCodec.fromJson(json, RawContent.class);

        assertThat(restored).isInstanceOf(ImageContent.class);
        assertThat((ImageContent) restored)
                .extracting(
                        ImageContent::mimeType,
                        ImageContent::description,
                        ImageContent::ocrText,
                        ImageContent::sourceUri)
                .containsExactly(
                        "image/png",
                        "dashboard screenshot",
                        "Total revenue 30%",
                        "file:///tmp/dashboard.png");
    }

    record SamplePayload(String name, Instant createdAt) {}
}
