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
 * Optional graph storage domain for item graph primitives.
 */
public interface GraphOperations {

    void upsertEntities(MemoryId memoryId, List<GraphEntity> entities);

    void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> mentions);

    void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys);

    void upsertItemLinks(MemoryId memoryId, List<ItemLink> links);

    List<GraphEntity> listEntities(MemoryId memoryId);

    List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId);

    List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId);

    List<ItemLink> listItemLinks(MemoryId memoryId);
}
