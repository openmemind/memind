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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.thread.marker.ThreadSemanticMarker;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Shared immutable extraction context for anchor providers.
 */
public record ThreadAnchorContext(
        MemoryItem triggerItem,
        List<ItemEntityMention> mentions,
        List<ItemLink> adjacentLinks,
        List<EntityCooccurrence> cooccurrences,
        ThreadSemanticMarker.SemanticsEnvelope semantics,
        Instant eventTime) {

    public ThreadAnchorContext {
        triggerItem = Objects.requireNonNull(triggerItem, "triggerItem");
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        adjacentLinks = adjacentLinks == null ? List.of() : List.copyOf(adjacentLinks);
        cooccurrences = cooccurrences == null ? List.of() : List.copyOf(cooccurrences);
        semantics = Objects.requireNonNull(semantics, "semantics");
        eventTime = Objects.requireNonNull(eventTime, "eventTime");
    }
}
