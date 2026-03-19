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
