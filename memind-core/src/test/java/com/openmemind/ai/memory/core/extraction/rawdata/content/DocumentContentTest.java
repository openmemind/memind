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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.content.document.DocumentSection;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentContentTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void contentTypeToContentStringAndContentIdMatchParsedText() {
        var content = DocumentContent.of("Quarterly Report", "application/pdf", "Revenue grew 30%");

        assertThat(content.contentType()).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(content.toContentString()).isEqualTo("Revenue grew 30%");
        assertThat(content.getContentId()).isEqualTo(HashUtils.sampledSha256("Revenue grew 30%"));
    }

    @Test
    void defaultsAreEmptyAndNullableWhereExpected() {
        var content = DocumentContent.of("Quarterly Report", null, "Revenue grew 30%");

        assertThat(content.sections()).isEmpty();
        assertThat(content.sourceUri()).isNull();
        assertThat(content.mimeType()).isNull();
        assertThat(content.metadata()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyCollections() {
        var sections =
                new java.util.ArrayList<>(
                        List.of(
                                new DocumentSection(
                                        "Summary", "Revenue grew 30%", 0, Map.of("page", 1))));
        var metadata = new java.util.HashMap<String, Object>();
        metadata.put("author", "Alice");

        var content =
                new DocumentContent(
                        "Quarterly Report",
                        "application/pdf",
                        "Revenue grew 30%",
                        sections,
                        "file:///tmp/report.pdf",
                        metadata);

        sections.clear();
        metadata.put("author", "Bob");

        assertThat(content.sections()).hasSize(1);
        assertThat(content.metadata()).containsEntry("author", "Alice");
    }

    @Test
    void jacksonRoundTripPreservesSubtypeAndFields() throws Exception {
        var content =
                new DocumentContent(
                        "Quarterly Report",
                        "application/pdf",
                        "Revenue grew 30%",
                        List.of(
                                new DocumentSection(
                                        "Summary", "Revenue grew 30%", 0, Map.of("page", 1))),
                        "file:///tmp/report.pdf",
                        Map.of("author", "Alice"));

        String json = OBJECT_MAPPER.writeValueAsString(content);
        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        assertThat(json).contains("\"type\":\"document\"");
        assertThat(decoded).isInstanceOf(DocumentContent.class);
        assertThat((DocumentContent) decoded)
                .extracting(
                        DocumentContent::title,
                        DocumentContent::mimeType,
                        DocumentContent::parsedText,
                        DocumentContent::sourceUri)
                .containsExactly(
                        "Quarterly Report",
                        "application/pdf",
                        "Revenue grew 30%",
                        "file:///tmp/report.pdf");
    }
}
