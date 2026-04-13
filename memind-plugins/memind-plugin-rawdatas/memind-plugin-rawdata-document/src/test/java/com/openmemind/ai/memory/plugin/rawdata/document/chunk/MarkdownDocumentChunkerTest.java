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
package com.openmemind.ai.memory.plugin.rawdata.document.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownDocumentChunkerTest {

    @Test
    void chunkKeepsPreambleAndHeadingBlocksSeparated() {
        var chunker = new MarkdownDocumentChunker(new TokenAwareSegmentAssembler());
        var text = "Lead in\n\n# Intro\nhello\n\n## Usage\nworld";

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(16, 24));

        assertThat(segments).hasSize(3);
        assertThat(segments)
                .extracting(Segment::content)
                .containsExactly("Lead in", "# Intro\nhello", "## Usage\nworld");
        assertThat(segments)
                .extracting(
                        segment -> {
                            CharBoundary boundary = (CharBoundary) segment.boundary();
                            return text.substring(boundary.startChar(), boundary.endChar());
                        })
                .containsExactly("Lead in", "# Intro\nhello", "## Usage\nworld");
    }
}
