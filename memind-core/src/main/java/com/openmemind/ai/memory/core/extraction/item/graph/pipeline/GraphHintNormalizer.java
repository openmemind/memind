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
package com.openmemind.ai.memory.core.extraction.item.graph.pipeline;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityHintNormalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.link.causal.CausalHintNormalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.util.List;
import java.util.Objects;

/**
 * Normalizes extraction-side graph hints into bounded graph primitives.
 */
public final class GraphHintNormalizer {

    private final EntityHintNormalizer entityHintNormalizer;
    private final CausalHintNormalizer causalHintNormalizer;

    public GraphHintNormalizer() {
        this(new EntityHintNormalizer(), new CausalHintNormalizer());
    }

    GraphHintNormalizer(
            EntityHintNormalizer entityHintNormalizer, CausalHintNormalizer causalHintNormalizer) {
        this.entityHintNormalizer =
                Objects.requireNonNull(entityHintNormalizer, "entityHintNormalizer");
        this.causalHintNormalizer =
                Objects.requireNonNull(causalHintNormalizer, "causalHintNormalizer");
    }

    public NormalizedGraphBatch normalize(
            MemoryId memoryId,
            List<MemoryItem> persistedItems,
            List<ExtractedMemoryEntry> sourceEntries,
            ItemGraphOptions options) {
        if (options == null
                || !options.enabled()
                || persistedItems == null
                || persistedItems.isEmpty()
                || sourceEntries == null
                || sourceEntries.isEmpty()) {
            return NormalizedGraphBatch.empty();
        }

        int entryCount = Math.min(persistedItems.size(), sourceEntries.size());
        List<MemoryItem> batchItems = persistedItems.subList(0, entryCount);
        List<ExtractedMemoryEntry> batchEntries = sourceEntries.subList(0, entryCount);
        var entityResult =
                entityHintNormalizer.normalize(memoryId, batchItems, batchEntries, options);
        List<ItemLink> itemLinks =
                causalHintNormalizer.normalize(memoryId, batchItems, batchEntries, options);

        return new NormalizedGraphBatch(
                entityResult.candidates(), itemLinks, entityResult.diagnostics());
    }
}
