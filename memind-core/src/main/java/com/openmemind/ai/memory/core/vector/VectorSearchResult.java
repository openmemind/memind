package com.openmemind.ai.memory.core.vector;

import java.util.Map;

/**
 * Vector search result
 *
 */
public record VectorSearchResult(
        /* Vector ID */
        String vectorId,

        /* Original text */
        String text,

        /* Similarity score (0.0-1.0) */
        float score,

        /* Metadata */
        Map<String, Object> metadata) {}
