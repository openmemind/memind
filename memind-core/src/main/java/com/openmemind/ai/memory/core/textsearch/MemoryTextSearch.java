package com.openmemind.ai.memory.core.textsearch;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Keyword text search interface
 *
 */
public interface MemoryTextSearch {

    Mono<List<TextSearchResult>> search(
            MemoryId memoryId, String query, int topK, SearchTarget target);

    /**
     * Clear the cache for the specified memoryId (if any)
     *
     * <p>Default empty implementation, classes with a cache should override this method
     */
    default void invalidate(MemoryId memoryId) {}

    enum SearchTarget {
        ITEM,
        INSIGHT,
        RAW_DATA
    }
}
