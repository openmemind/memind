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
package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;

/**
 * Deduplication result
 *
 * @param newEntries Confirmed new entries
 * @param matchedItems Hit existing memory items (carrying matching similarity)
 */
public record DeduplicationResult(
        List<ExtractedMemoryEntry> newEntries, List<MatchedItem> matchedItems) {

    /**
     * Deduplication hit existing memory items + matching similarity
     *
     * @param item Original store item (not enhanced)
     * @param similarity Matching similarity (0.0-1.0), hash exact match is 1.0
     */
    public record MatchedItem(MemoryItem item, double similarity) {}
}
