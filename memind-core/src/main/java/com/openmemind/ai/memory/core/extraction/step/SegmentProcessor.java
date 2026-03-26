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
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Segment Processor
 *
 * <p>Process the content of completed segments (skip normalize + chunk), directly execute caption → vectorize → persist.
 *
 * <p>Used in ConversationSession scenarios: segments have been completed by boundary detection, no need to chunk again.
 *
 */
public interface SegmentProcessor {

    /**
     * Process the segmented content
     *
     * @param memoryId Memory identifier
     * @param segment Segmented content
     * @param type Data type
     * @param contentId Content identifier (for idempotency)
     * @param metadata Metadata
     * @return Processing result
     */
    Mono<RawDataResult> processSegment(
            MemoryId memoryId,
            Segment segment,
            String type,
            String contentId,
            Map<String, Object> metadata);

    /**
     * Process segmented content with language hint.
     *
     * @param memoryId Memory identifier
     * @param segment Segmented content
     * @param type Data type
     * @param contentId Content identifier (for idempotency)
     * @param metadata Metadata
     * @param language Target language, can be null
     * @return Processing result
     */
    default Mono<RawDataResult> processSegment(
            MemoryId memoryId,
            Segment segment,
            String type,
            String contentId,
            Map<String, Object> metadata,
            String language) {
        return processSegment(memoryId, segment, type, contentId, metadata);
    }
}
