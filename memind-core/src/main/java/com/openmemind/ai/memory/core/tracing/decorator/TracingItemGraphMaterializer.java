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
package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Tracing decorator for post-commit graph materialization.
 */
public final class TracingItemGraphMaterializer extends TracingSupport
        implements ItemGraphMaterializer {

    private final ItemGraphMaterializer delegate;

    public TracingItemGraphMaterializer(ItemGraphMaterializer delegate, MemoryObserver observer) {
        super(observer);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<ItemGraphMaterializationResult> materialize(
            MemoryId memoryId, List<MemoryItem> items, List<ExtractedMemoryEntry> sourceEntries) {
        return trace(
                MemorySpanNames.GRAPH_MATERIALIZE,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        memoryId.toIdentifier(),
                        MemoryAttributes.EXTRACTION_ITEM_COUNT,
                        items != null ? items.size() : 0),
                result ->
                        Map.of(
                                MemoryAttributes.EXTRACTION_GRAPH_ENTITY_COUNT,
                                result.stats().entityCount(),
                                MemoryAttributes.EXTRACTION_GRAPH_MENTION_COUNT,
                                result.stats().mentionCount(),
                                MemoryAttributes.EXTRACTION_GRAPH_STRUCTURED_LINK_COUNT,
                                result.stats().structuredItemLinkCount(),
                                MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SEARCH_HIT_COUNT,
                                result.stats().semanticSearchHitCount(),
                                MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_LINK_COUNT,
                                result.stats().semanticLinkCount(),
                                MemoryAttributes.EXTRACTION_GRAPH_SEMANTIC_SAME_BATCH_HIT_COUNT,
                                result.stats().semanticSameBatchHitCount()),
                () -> delegate.materialize(memoryId, items, sourceEntries));
    }
}
