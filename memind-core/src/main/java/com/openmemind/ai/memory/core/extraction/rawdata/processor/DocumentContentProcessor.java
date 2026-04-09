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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.document.DocumentSection;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed document content.
 */
public class DocumentContentProcessor implements RawContentProcessor<DocumentContent> {

    private final TextChunker textChunker;
    private final TextChunkingConfig chunkingConfig;

    public DocumentContentProcessor(TextChunker textChunker, TextChunkingConfig chunkingConfig) {
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker must not be null");
        this.chunkingConfig =
                Objects.requireNonNull(chunkingConfig, "chunkingConfig must not be null");
    }

    @Override
    public Class<DocumentContent> contentClass() {
        return DocumentContent.class;
    }

    @Override
    public String contentType() {
        return ContentTypes.DOCUMENT;
    }

    @Override
    public Mono<List<Segment>> chunk(DocumentContent content) {
        if (content.sections().isEmpty()) {
            return Mono.just(textChunker.chunk(content.toContentString(), chunkingConfig));
        }

        List<Segment> segments = new ArrayList<>();
        String fullText = content.toContentString();
        int searchFrom = 0;
        for (DocumentSection section : content.sections()) {
            String sectionText = section.content();
            if (sectionText == null || sectionText.isBlank()) {
                continue;
            }
            int sectionStart = fullText.indexOf(sectionText, searchFrom);
            if (sectionStart < 0) {
                sectionStart = Math.max(0, searchFrom);
            }
            searchFrom = sectionStart + sectionText.length();
            for (Segment local : textChunker.chunk(sectionText, chunkingConfig)) {
                CharBoundary localBoundary = (CharBoundary) local.boundary();
                Map<String, Object> metadata = new LinkedHashMap<>(section.metadata());
                metadata.put("sectionIndex", section.index());
                if (section.title() != null && !section.title().isBlank()) {
                    metadata.put("sectionTitle", section.title());
                }
                segments.add(
                        new Segment(
                                local.content(),
                                null,
                                new CharBoundary(
                                        sectionStart + localBoundary.startChar(),
                                        sectionStart + localBoundary.endChar()),
                                Map.copyOf(metadata)));
            }
        }
        return Mono.just(segments);
    }
}
