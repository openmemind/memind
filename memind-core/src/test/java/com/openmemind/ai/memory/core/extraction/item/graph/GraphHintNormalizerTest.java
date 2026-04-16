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
package com.openmemind.ai.memory.core.extraction.item.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphHintNormalizerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void normalizeShouldCanonicalizeEntitiesAndDropHighNoiseMentions() {
        var normalizer = new GraphHintNormalizer();

        var batch =
                normalizer.normalize(
                        MEMORY_ID,
                        List.of(item(101L, "User discussed OpenAI deployment")),
                        List.of(
                                entry(
                                        "User discussed OpenAI deployment",
                                        List.of(
                                                entityHint(" OpenAI ", "organization", 0.91f),
                                                entityHint("昨天", "concept", 0.70f)),
                                        List.of())),
                        ItemGraphOptions.defaults().withEnabled(true));

        assertThat(batch.entities())
                .extracting(GraphEntity::entityKey)
                .containsExactly("organization:openai");
        assertThat(batch.mentions()).hasSize(1);
    }

    @Test
    void normalizeShouldBuildBackwardValidatedCausalAndWithinBatchTemporalLinks() {
        var normalizer = new GraphHintNormalizer();
        var items =
                List.of(
                        item(101L, "Cause item", Instant.parse("2026-04-15T09:00:00Z")),
                        item(102L, "Effect item", Instant.parse("2026-04-15T10:00:00Z")));
        var entries =
                List.of(
                        entry("Cause item", List.of(), List.of()),
                        entry(
                                "Effect item",
                                List.of(),
                                List.of(
                                        causalHint(0, "caused_by", 0.95f),
                                        causalHint(1, "caused_by", 0.99f))));

        var batch =
                normalizer.normalize(
                        MEMORY_ID, items, entries, ItemGraphOptions.defaults().withEnabled(true));

        assertThat(batch.itemLinks())
                .extracting(ItemLink::linkType, ItemLink::sourceItemId, ItemLink::targetItemId)
                .contains(tuple(ItemLinkType.CAUSAL, 101L, 102L))
                .contains(tuple(ItemLinkType.TEMPORAL, 101L, 102L));
        assertThat(batch.itemLinks())
                .filteredOn(link -> link.linkType() == ItemLinkType.CAUSAL)
                .hasSize(1);
    }

    private static MemoryItem item(Long id, String content) {
        return item(id, content, null);
    }

    private static MemoryItem item(Long id, String content, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry entry(
            String content,
            List<ExtractedGraphHints.ExtractedEntityHint> entities,
            List<ExtractedGraphHints.ExtractedCausalRelationHint> causalRelations) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                null,
                null,
                null,
                null,
                CREATED_AT,
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(entities, causalRelations));
    }

    private static ExtractedGraphHints.ExtractedEntityHint entityHint(
            String name, String entityType, Float salience) {
        return new ExtractedGraphHints.ExtractedEntityHint(name, entityType, salience);
    }

    private static ExtractedGraphHints.ExtractedCausalRelationHint causalHint(
            Integer targetIndex, String relationType, Float strength) {
        return new ExtractedGraphHints.ExtractedCausalRelationHint(
                targetIndex, relationType, strength);
    }
}
