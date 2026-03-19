package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

/**
 * Text chunking configuration
 *
 * @param chunkSize Number of characters per chunk
 * @param boundary Chunk boundary
 */
public record TextChunkingConfig(int chunkSize, ChunkBoundary boundary) implements ChunkingConfig {

    /**
     * Chunk boundary type
     */
    public enum ChunkBoundary {
        CHARACTER,
        LINE,
        PARAGRAPH
    }

    public static final TextChunkingConfig DEFAULT =
            new TextChunkingConfig(2000, ChunkBoundary.LINE);

    public TextChunkingConfig {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
    }
}
