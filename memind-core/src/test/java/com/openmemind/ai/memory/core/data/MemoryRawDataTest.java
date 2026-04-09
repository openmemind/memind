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
package com.openmemind.ai.memory.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryRawData")
class MemoryRawDataTest {

    @Test
    @DisplayName("withVectorId should keep projected resource fields aligned with merged metadata")
    void withVectorIdShouldKeepProjectedResourceFieldsAlignedWithMergedMetadata() {
        var rawData =
                new MemoryRawData(
                        "raw-1",
                        "user-1:agent-1",
                        ContentTypes.DOCUMENT,
                        "content-1",
                        Segment.single("hello"),
                        "caption",
                        null,
                        Map.of(
                                "resourceId", "res-1",
                                "mimeType", "application/pdf",
                                "sourceUri", "file:///tmp/report.pdf"),
                        "res-1",
                        "application/pdf",
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:01Z"));

        var updated =
                rawData.withVectorId(
                        "vec-1", Map.of("mimeType", "image/png", "vectorSource", "reindex"));

        assertThat(updated.captionVectorId()).isEqualTo("vec-1");
        assertThat(updated.metadata())
                .containsEntry("resourceId", "res-1")
                .containsEntry("mimeType", "image/png")
                .containsEntry("vectorSource", "reindex");
        assertThat(updated.resourceId()).isEqualTo("res-1");
        assertThat(updated.mimeType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName(
            "withMetadata should recompute projected resource fields from replacement metadata")
    void withMetadataShouldRecomputeProjectedResourceFieldsFromReplacementMetadata() {
        var rawData =
                new MemoryRawData(
                        "raw-1",
                        "user-1:agent-1",
                        ContentTypes.DOCUMENT,
                        "content-1",
                        Segment.single("hello"),
                        "caption",
                        "vec-1",
                        Map.of(
                                "resourceId", "res-1",
                                "mimeType", "application/pdf",
                                "sourceUri", "file:///tmp/report.pdf"),
                        "res-1",
                        "application/pdf",
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:00Z"),
                        Instant.parse("2026-04-09T00:00:01Z"));

        var replaced =
                rawData.withMetadata(
                        Map.of(
                                "resourceId",
                                "res-9",
                                "mimeType",
                                "audio/mpeg",
                                "fileName",
                                "a.mp3"));

        assertThat(replaced.metadata())
                .containsEntry("resourceId", "res-9")
                .containsEntry("mimeType", "audio/mpeg")
                .containsEntry("fileName", "a.mp3");
        assertThat(replaced.resourceId()).isEqualTo("res-9");
        assertThat(replaced.mimeType()).isEqualTo("audio/mpeg");
    }
}
