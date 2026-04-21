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

import java.util.Map;
import java.util.Set;

/**
 * Immutable committed graph snapshot used for in-memory promotion.
 */
public record CommittedGraphView(
        Map<String, GraphEntity> entitiesByKey,
        Map<String, ItemEntityMention> mentionsByIdentity,
        Map<String, GraphEntityAlias> aliasesByIdentity,
        Map<String, ItemLink> itemLinksByIdentity,
        Map<String, EntityCooccurrence> entityCooccurrencesByIdentity,
        Set<String> aliasBatchReceipts) {

    public CommittedGraphView {
        entitiesByKey = entitiesByKey == null ? Map.of() : Map.copyOf(entitiesByKey);
        mentionsByIdentity = mentionsByIdentity == null ? Map.of() : Map.copyOf(mentionsByIdentity);
        aliasesByIdentity = aliasesByIdentity == null ? Map.of() : Map.copyOf(aliasesByIdentity);
        itemLinksByIdentity =
                itemLinksByIdentity == null ? Map.of() : Map.copyOf(itemLinksByIdentity);
        entityCooccurrencesByIdentity =
                entityCooccurrencesByIdentity == null
                        ? Map.of()
                        : Map.copyOf(entityCooccurrencesByIdentity);
        aliasBatchReceipts = aliasBatchReceipts == null ? Set.of() : Set.copyOf(aliasBatchReceipts);
    }

    public static CommittedGraphView empty() {
        return new CommittedGraphView(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }
}
