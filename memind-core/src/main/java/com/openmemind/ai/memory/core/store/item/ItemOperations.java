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
package com.openmemind.ai.memory.core.store.item;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.Collection;
import java.util.List;

/**
 * Operations for memory item persistence.
 */
public interface ItemOperations {

    void insertItems(MemoryId id, List<MemoryItem> items);

    List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds);

    List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds);

    List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes);

    List<MemoryItem> listItems(MemoryId id);

    default List<TemporalCandidateMatch> listTemporalCandidateMatches(
            MemoryId memoryId,
            List<TemporalCandidateRequest> requests,
            Collection<Long> excludeItemIds) {
        return TemporalCandidateLookupSupport.correctnessFirstLookup(
                this, memoryId, requests, excludeItemIds);
    }

    boolean hasItems(MemoryId id);

    void deleteItems(MemoryId id, Collection<Long> itemIds);
}
