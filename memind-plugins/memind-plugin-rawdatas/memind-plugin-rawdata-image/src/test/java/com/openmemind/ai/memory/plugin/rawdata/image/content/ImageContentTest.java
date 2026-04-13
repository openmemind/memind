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
package com.openmemind.ai.memory.plugin.rawdata.image.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.ImageSemantics;
import com.openmemind.ai.memory.plugin.rawdata.image.plugin.ImageRawContentTypeRegistrar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageContentTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RawContentJackson.registerAll(mapper, List.of(new ImageRawContentTypeRegistrar()));
        return mapper;
    }

    @Test
    void contentIdShouldTrackVisibleSourceTextIncludingOcr() {
        var content =
                new ImageContent(
                        "image/png",
                        "A screenshot of the dashboard",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        assertThat(content.contentType()).isEqualTo(ImageContent.TYPE);
        assertThat(content.toContentString())
                .isEqualTo("A screenshot of the dashboard\nTotal Revenue 30%");
        assertThat(content.getContentId())
                .isEqualTo(
                        HashUtils.sampledSha256(
                                "A screenshot of the dashboard\nTotal Revenue 30%"));
    }

    @Test
    void optionalFieldsMayBeNullOrEmpty() {
        var content = ImageContent.of("A screenshot of the dashboard");

        assertThat(content.mimeType()).isNull();
        assertThat(content.ocrText()).isNull();
        assertThat(content.sourceUri()).isNull();
        assertThat(content.metadata()).isEmpty();
    }

    @Test
    void contentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new ImageContent(
                        "image/png",
                        "chart screenshot",
                        "Q1 revenue",
                        "file:///tmp/chart.png",
                        Map.of("width", 1280));

        assertThat(content.contentMetadata()).containsEntry("width", 1280);
        assertThat(content.directGovernanceType()).isEqualTo(ImageSemantics.GOVERNANCE_CAPTION_OCR);
        assertThat(content.directContentProfile()).isEqualTo(ImageSemantics.PROFILE_CAPTION_OCR);
        assertThat(content.withMetadata(Map.of("parserId", "vision")))
                .isInstanceOf(ImageContent.class)
                .extracting(value -> ((ImageContent) value).metadata().get("parserId"))
                .isEqualTo("vision");
    }

    @Test
    void jacksonRoundTripPreservesSubtypeAndMimeType() throws Exception {
        var content =
                new ImageContent(
                        "image/png",
                        "A screenshot of the dashboard",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        String json = OBJECT_MAPPER.writeValueAsString(content);
        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        assertThat(json).contains("\"type\":\"image\"");
        assertThat(decoded).isInstanceOf(ImageContent.class);
        assertThat((ImageContent) decoded)
                .extracting(
                        ImageContent::mimeType,
                        ImageContent::description,
                        ImageContent::ocrText,
                        ImageContent::sourceUri)
                .containsExactly(
                        "image/png",
                        "A screenshot of the dashboard",
                        "Total Revenue 30%",
                        "file:///tmp/dashboard.png");
    }
}
