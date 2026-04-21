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
package com.openmemind.ai.memory.core.extraction.item.graph.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalRelationCode;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalRelationCode;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemGraphWritePlanTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-20T00:00:00Z");

    @Test
    void rejectsConflictingTemporalRelationsForSameIdentity() {
        var before = new TemporalItemRelation(101L, 202L, TemporalRelationCode.BEFORE, 1.0d);
        var overlap = new TemporalItemRelation(101L, 202L, TemporalRelationCode.OVERLAP, 1.0d);

        assertThatThrownBy(
                        () ->
                                ItemGraphWritePlan.builder()
                                        .temporalRelations(List.of(before, overlap))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting temporal relation");
    }

    @Test
    void rejectsConflictingCausalRelationsForSameIdentity() {
        var causedBy = new CausalItemRelation(101L, 202L, CausalRelationCode.CAUSED_BY, 0.91d);
        var enabledBy = new CausalItemRelation(101L, 202L, CausalRelationCode.ENABLED_BY, 0.77d);

        assertThatThrownBy(
                        () ->
                                ItemGraphWritePlan.builder()
                                        .causalRelations(List.of(causedBy, enabledBy))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting causal relation");
    }

    @Test
    void collapsesDuplicateMentionsAndAliasesByCanonicalIdentity() {
        var mentionA = mention(101L, "person:sam_altman", 0.40f);
        var mentionB = mention(101L, "person:sam_altman", 0.95f);
        var aliasA = alias("person:sam_altman", "sam altman", 1);
        var aliasB = alias("person:sam_altman", "sam altman", 1);

        var plan =
                ItemGraphWritePlan.builder()
                        .mentions(List.of(mentionA, mentionB))
                        .aliases(List.of(aliasA, aliasB))
                        .build();

        assertThat(plan.mentions())
                .singleElement()
                .extracting(ItemEntityMention::confidence)
                .isEqualTo(0.95f);
        assertThat(plan.aliases())
                .singleElement()
                .extracting(GraphEntityAlias::evidenceCount)
                .isEqualTo(2);
    }

    @Test
    void canonicalizesSemanticEvidenceAndFlattensTypedRelationsIntoPersistedLinks() {
        var plan =
                ItemGraphWritePlan.builder()
                        .semanticRelations(
                                List.of(
                                        new SemanticItemRelation(
                                                101L,
                                                202L,
                                                SemanticEvidenceSource.VECTOR_SEARCH_FALLBACK,
                                                1.4d),
                                        new SemanticItemRelation(
                                                101L,
                                                202L,
                                                SemanticEvidenceSource.VECTOR_SEARCH,
                                                0.81d)))
                        .temporalRelations(
                                List.of(
                                        new TemporalItemRelation(
                                                101L, 303L, TemporalRelationCode.NEARBY, 1.8d)))
                        .causalRelations(
                                List.of(
                                        new CausalItemRelation(
                                                404L,
                                                505L,
                                                CausalRelationCode.MOTIVATED_BY,
                                                -0.2d)))
                        .build();

        assertThat(plan.semanticRelations())
                .singleElement()
                .extracting(SemanticItemRelation::evidenceSource, SemanticItemRelation::strength)
                .containsExactly(SemanticEvidenceSource.VECTOR_SEARCH, 1.0d);

        assertThat(plan.toPersistedLinks(MEMORY_ID))
                .extracting(
                        ItemLink::sourceItemId,
                        ItemLink::targetItemId,
                        ItemLink::linkType,
                        ItemLink::relationCode,
                        ItemLink::evidenceSource,
                        ItemLink::strength)
                .containsExactly(
                        tuple(101L, 202L, ItemLinkType.SEMANTIC, null, "vector_search", 1.0d),
                        tuple(101L, 303L, ItemLinkType.TEMPORAL, "nearby", null, 1.0d),
                        tuple(404L, 505L, ItemLinkType.CAUSAL, "motivated_by", null, 0.0d));
    }

    private static ItemEntityMention mention(Long itemId, String entityKey, Float confidence) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, confidence, Map.of(), NOW);
    }

    private static GraphEntityAlias alias(String entityKey, String normalizedAlias, int evidence) {
        return new GraphEntityAlias(
                MEMORY_ID.toIdentifier(),
                entityKey,
                GraphEntityType.PERSON,
                normalizedAlias,
                evidence,
                Map.of(),
                NOW,
                NOW);
    }
}
