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
}
