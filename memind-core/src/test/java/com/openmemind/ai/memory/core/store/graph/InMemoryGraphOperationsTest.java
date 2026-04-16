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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryGraphOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void noOpGraphOperationsSwallowWritesAndExposeEmptyViews() {
        GraphOperations ops = NoOpGraphOperations.INSTANCE;

        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));

        assertThat(ops.listEntities(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemEntityMentions(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemLinks(MEMORY_ID)).isEmpty();
    }

    @Test
    void inMemoryGraphOperationsUpsertEntitiesMentionsAndLinksIdempotently() {
        var ops = new InMemoryGraphOperations();

        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));
        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));

        assertThat(ops.listEntities(MEMORY_ID)).hasSize(1);
        assertThat(ops.listItemEntityMentions(MEMORY_ID)).hasSize(1);
        assertThat(ops.listItemLinks(MEMORY_ID)).hasSize(1);
    }

    @Test
    void rebuildEntityCooccurrencesShouldBeDeterministicAcrossRepeatedCalls() {
        var ops = new InMemoryGraphOperations();

        ops.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai"),
                        mention(101L, "person:sam_altman"),
                        mention(102L, "organization:openai"),
                        mention(102L, "person:sam_altman")));

        ops.rebuildEntityCooccurrences(
                MEMORY_ID, List.of("organization:openai", "person:sam_altman"));
        ops.rebuildEntityCooccurrences(
                MEMORY_ID, List.of("organization:openai", "person:sam_altman"));

        assertThat(ops.listEntityCooccurrences(MEMORY_ID))
                .singleElement()
                .extracting(EntityCooccurrence::cooccurrenceCount)
                .isEqualTo(2);
    }

    private static GraphEntity entity(String entityKey, String canonicalName) {
        return new GraphEntity(
                entityKey,
                MEMORY_ID.toIdentifier(),
                canonicalName,
                GraphEntityType.ORGANIZATION,
                Map.of("source", "test"),
                NOW,
                NOW);
    }

    private static ItemEntityMention mention(long itemId, String entityKey) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, 1.0f, Map.of("source", "test"), NOW);
    }

    private static ItemLink link(long sourceItemId, long targetItemId, ItemLinkType linkType) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                linkType,
                1.0d,
                Map.of("source", "test"),
                NOW);
    }
}
