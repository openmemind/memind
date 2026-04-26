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
package com.openmemind.ai.memory.core.extraction.item.graph.link.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityOverlapItemLinkerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void createsSemanticLinkWhenItemsShareTwoEntities() {
        var plan =
                new EntityOverlapItemLinker()
                        .plan(
                                MEMORY_ID,
                                List.of(item(101L), item(202L)),
                                List.of(
                                        mention(101L, "object:billing api"),
                                        mention(101L, "object:postgresql"),
                                        mention(202L, "object:billing api"),
                                        mention(202L, "object:postgresql")),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(plan.links())
                .extracting(
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        ItemLink::linkType,
                        ItemLink::evidenceSource)
                .containsExactly(tuple(101L, 202L, ItemLinkType.SEMANTIC, "entity_overlap"));
        assertThat(plan.stats().candidatePairCount()).isEqualTo(1);
        assertThat(plan.stats().createdLinkCount()).isEqualTo(1);
    }

    @Test
    void ignoresSingleSharedEntityAndSpecialEntities() {
        var plan =
                new EntityOverlapItemLinker()
                        .plan(
                                MEMORY_ID,
                                List.of(item(101L), item(202L)),
                                List.of(
                                        mention(101L, "object:billing api"),
                                        mention(202L, "object:billing api"),
                                        mention(101L, "special:user"),
                                        mention(202L, "special:user")),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(plan.links()).isEmpty();
        assertThat(plan.stats().candidatePairCount()).isEqualTo(1);
    }

    @Test
    void suppressesHighFanoutEntitiesAndLimitsLinksPerSource() {
        var options =
                ItemGraphOptions.defaults().toBuilder()
                        .enabled(true)
                        .maxItemsPerEntityForSemanticLink(2)
                        .maxEntityOverlapLinksPerItem(1)
                        .build();
        var plan =
                new EntityOverlapItemLinker()
                        .plan(
                                MEMORY_ID,
                                List.of(item(101L), item(202L), item(303L)),
                                List.of(
                                        mention(101L, "object:shared-high-fanout"),
                                        mention(202L, "object:shared-high-fanout"),
                                        mention(303L, "object:shared-high-fanout"),
                                        mention(101L, "object:a"),
                                        mention(202L, "object:a"),
                                        mention(101L, "object:b"),
                                        mention(202L, "object:b"),
                                        mention(101L, "object:c"),
                                        mention(303L, "object:c"),
                                        mention(101L, "object:d"),
                                        mention(303L, "object:d")),
                                options);

        assertThat(plan.stats().skippedFanoutEntityCount()).isEqualTo(1);
        assertThat(plan.links()).hasSize(1);
    }

    private static MemoryItem item(Long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "item-" + id,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                null,
                NOW,
                Map.of(),
                NOW,
                MemoryItemType.FACT);
    }

    private static ItemEntityMention mention(Long itemId, String entityKey) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, 0.9f, Map.of(), NOW);
    }
}
