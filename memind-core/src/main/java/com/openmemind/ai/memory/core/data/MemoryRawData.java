package com.openmemind.ai.memory.core.data;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record MemoryRawData(
        String id,
        String memoryId,

        /* Content type identifier (e.g. ContentTypes.CONVERSATION) */
        String contentType,

        /* Original content ID */
        String contentId,

        /* Segmented content */
        Segment segment,

        /* One-sentence summary (for vector retrieval) */
        String caption,

        /* Caption ID in the vector database */
        String captionVectorId,

        /* Additional metadata */
        Map<String, Object> metadata,

        /* Creation time */
        Instant createdAt) {

    /**
     * Returns a new {@link MemoryRawData} instance, using the given vector ID, and merging patches in the metadata.
     * <p>Metadata merge rules: Based on the original {@code metadata}, overwrite/add the key-value pairs in {@code metadataPatch}.
     */
    public MemoryRawData withVectorId(String vectorId, Map<String, Object> metadataPatch) {
        Map<String, Object> merged = new HashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        if (metadataPatch != null) {
            merged.putAll(metadataPatch);
        }
        return new MemoryRawData(
                id,
                memoryId,
                contentType,
                contentId,
                segment,
                caption,
                vectorId,
                merged,
                createdAt);
    }

    /**
     * Returns a new {@link MemoryRawData} instance, replacing the metadata with the given {@code metadata}.
     */
    public MemoryRawData withMetadata(Map<String, Object> metadata) {
        return new MemoryRawData(
                id,
                memoryId,
                contentType,
                contentId,
                segment,
                caption,
                captionVectorId,
                metadata,
                createdAt);
    }
}
