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
package com.openmemind.ai.memory.core.store.graph;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.Collection;
import java.util.List;

/**
 * Disabled-mode graph operations implementation.
 */
public final class NoOpGraphOperations implements GraphOperations {

    public static final NoOpGraphOperations INSTANCE = new NoOpGraphOperations();

    private NoOpGraphOperations() {}

    @Override
    public void upsertEntities(MemoryId memoryId, List<GraphEntity> entities) {}

    @Override
    public void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> mentions) {}

    @Override
    public void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys) {}

    @Override
    public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {}

    @Override
    public List<GraphEntity> listEntities(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(
            MemoryId memoryId, Collection<Long> itemIds) {
        return List.of();
    }

    @Override
    public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public List<ItemLink> listItemLinks(MemoryId memoryId) {
        return List.of();
    }

    @Override
    public List<ItemLink> listItemLinks(
            MemoryId memoryId, Collection<Long> itemIds, Collection<ItemLinkType> linkTypes) {
        return List.of();
    }
}
