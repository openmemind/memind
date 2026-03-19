package com.openmemind.ai.memory.core.extraction.step;

import com.openmemind.ai.memory.core.data.MemoryId;
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
     * @param contentType Content type identifier (e.g. ContentTypes.CONVERSATION)
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
     * @param contentType Content type identifier (e.g. ContentTypes.CONVERSATION)
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
