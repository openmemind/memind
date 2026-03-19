package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import java.util.Map;

/**
 * RawData layer input
 *
 * @param memoryId Memory identifier
 * @param content Raw content
 * @param contentType Content type identifier (e.g. ContentTypes.CONVERSATION)
 * @param metadata Metadata
 */
public record RawDataInput(
        MemoryId memoryId, RawContent content, String contentType, Map<String, Object> metadata) {}
