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
package com.openmemind.ai.memory.core.extraction.result;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import java.util.List;

/**
 * MemoryItem extraction result
 *
 * @param newItems Newly created memory items
 * @param resolvedInsightTypes Resolved insight types (used to pass to InsightLayer)
 */
public record MemoryItemResult(
        List<MemoryItem> newItems,
        List<MemoryInsightType> resolvedInsightTypes,
        ItemGraphMaterializationResult graphMaterializationResult) {

    public MemoryItemResult(
            List<MemoryItem> newItems, List<MemoryInsightType> resolvedInsightTypes) {
        this(newItems, resolvedInsightTypes, ItemGraphMaterializationResult.empty());
    }

    public MemoryItemResult {
        newItems = newItems == null ? List.of() : List.copyOf(newItems);
        resolvedInsightTypes =
                resolvedInsightTypes == null ? List.of() : List.copyOf(resolvedInsightTypes);
        graphMaterializationResult =
                graphMaterializationResult == null
                        ? ItemGraphMaterializationResult.empty()
                        : graphMaterializationResult;
    }

    /**
     * Create empty result
     */
    public static MemoryItemResult empty() {
        return new MemoryItemResult(List.of(), List.of());
    }

    /**
     * Is empty
     */
    public boolean isEmpty() {
        return newItems.isEmpty();
    }

    /**
     * New count
     */
    public int newCount() {
        return newItems.size();
    }
}
