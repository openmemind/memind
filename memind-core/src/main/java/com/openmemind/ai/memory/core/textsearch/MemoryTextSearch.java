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
