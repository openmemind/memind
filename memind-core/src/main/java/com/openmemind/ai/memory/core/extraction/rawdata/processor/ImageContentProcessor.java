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
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed image content.
 */
public class ImageContentProcessor implements RawContentProcessor<ImageContent> {

    @Override
    public Class<ImageContent> contentClass() {
        return ImageContent.class;
    }

    @Override
    public String contentType() {
        return ContentTypes.IMAGE;
    }

    @Override
    public Mono<List<Segment>> chunk(ImageContent content) {
        String text = content.toContentString();
        return Mono.just(
                List.of(
                        new Segment(
                                text,
                                null,
                                new CharBoundary(0, text.length()),
                                Map.copyOf(content.metadata()))));
    }
}
