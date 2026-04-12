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
package com.openmemind.ai.memory.core.extraction.step;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * RawData extraction step
 *
 * <p>Responsible for the standardization, chunking, summary generation, and persistence of raw content
 */
public interface RawDataExtractStep {

    /**
     * Extract RawData
     *
     * @param memoryId Memory identifier
     * @param content Raw content
     * @param contentType Content type identifier (e.g. {@link ConversationContent#TYPE})
     * @param metadata Metadata
     * @return Processing result
     */
    Mono<RawDataResult> extract(
            MemoryId memoryId,
            RawContent content,
            String contentType,
            Map<String, Object> metadata);

    /**
     * Extract RawData with language hint
     *
     * @param memoryId Memory identifier
     * @param content Raw content
     * @param contentType Content type identifier (e.g. {@link ConversationContent#TYPE})
     * @param metadata Metadata
     * @param language Target language, can be null
     * @return Processing result
     */
    default Mono<RawDataResult> extract(
            MemoryId memoryId,
            RawContent content,
            String contentType,
            Map<String, Object> metadata,
            String language) {
        return extract(memoryId, content, contentType, metadata);
    }
}
