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
package com.openmemind.ai.memory.core.extraction.rawdata.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.document.DocumentSection;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentContentProcessorTest {

    @Test
    void contentTypeShouldBeDocument() {
        var processor = new DocumentContentProcessor(new TextChunker(), TextChunkingConfig.DEFAULT);

        assertThat(processor.contentType()).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    void shouldChunkParsedTextWhenSectionsAreMissing() {
        var processor =
                new DocumentContentProcessor(
                        new TextChunker(),
                        new TextChunkingConfig(12, TextChunkingConfig.ChunkBoundary.LINE));
        var content = DocumentContent.of("Report", "application/pdf", "alpha\nbeta\ngamma");

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(2);
                            assertThat(segments.getFirst().content()).isEqualTo("alpha\nbeta");
                            assertThat(segments.getFirst().boundary())
                                    .isInstanceOf(CharBoundary.class);
                        })
                .verifyComplete();
    }

    @Test
    void shouldPreserveSectionMetadataWhenSectionsExist() {
        var processor = new DocumentContentProcessor(new TextChunker(), TextChunkingConfig.DEFAULT);
        var content =
                new DocumentContent(
                        "Report",
                        "application/pdf",
                        "summary\n\nappendix",
                        List.of(
                                new DocumentSection("Summary", "summary", 0, Map.of("page", 1)),
                                new DocumentSection("Appendix", "appendix", 1, Map.of("page", 2))),
                        "file:///tmp/report.pdf",
                        Map.of("author", "Alice"));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(2);
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("sectionIndex", 0)
                                    .containsEntry("sectionTitle", "Summary")
                                    .containsEntry("page", 1);
                            assertThat(segments.get(1).metadata())
                                    .containsEntry("sectionIndex", 1)
                                    .containsEntry("sectionTitle", "Appendix")
                                    .containsEntry("page", 2);
                        })
                .verifyComplete();
    }
}
