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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityHintNormalizerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void normalizeShouldFilterNoiseCollapseDuplicateKeysAndKeepAliasEvidence() {
        var result =
                new EntityHintNormalizer()
                        .normalize(
                                MEMORY_ID,
                                List.of(item(101L, "我在 OpenAI（开放人工智能）团队工作")),
                                List.of(
                                        entry(
                                                "我在 OpenAI（开放人工智能）团队工作",
                                                List.of(
                                                        entityHint(
                                                                " OpenAI ",
                                                                "organization",
                                                                0.95f,
                                                                List.of(
                                                                        new EntityAliasObservation(
                                                                                "开放人工智能",
                                                                                EntityAliasClass
                                                                                        .EXPLICIT_PARENTHETICAL,
                                                                                "entity_inline",
                                                                                0.93f))),
                                                        entityHint("openai", "organization", 0.80f),
                                                        entityHint("昨天", "concept", 0.70f)))),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(result.candidates())
                .extracting(
                        NormalizedEntityMentionCandidate::rawName,
                        NormalizedEntityMentionCandidate::normalizedName,
                        NormalizedEntityMentionCandidate::entityType,
                        NormalizedEntityMentionCandidate::preResolutionEntityKey,
                        NormalizedEntityMentionCandidate::salience)
                .containsExactly(
                        tuple(
                                " OpenAI ",
                                "openai",
                                GraphEntityType.ORGANIZATION,
                                "organization:openai",
                                0.95f));
        assertThat(result.candidates().getFirst().aliasObservations())
                .singleElement()
                .extracting(
                        EntityAliasObservation::aliasSurface, EntityAliasObservation::aliasClass)
                .containsExactly("开放人工智能", EntityAliasClass.EXPLICIT_PARENTHETICAL);
        assertThat(result.diagnostics().droppedEntityCounts())
                .containsEntry(EntityDropReason.TEMPORAL, 1);
    }

    @Test
    void normalizeShouldPreserveSpecialAnchorsAndTrackFallbackTypeLabels() {
        var result =
                new EntityHintNormalizer()
                        .normalize(
                                MEMORY_ID,
                                List.of(item(101L, "用户让我联系张三")),
                                List.of(
                                        entry(
                                                "用户让我联系张三",
                                                List.of(
                                                        entityHint("张三", "人物", 0.95f),
                                                        entityHint("用户", "special", 0.90f),
                                                        entityHint("实体", "rare-type", 0.80f)))),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(result.candidates())
                .extracting(
                        NormalizedEntityMentionCandidate::rawName,
                        NormalizedEntityMentionCandidate::rawTypeLabel,
                        NormalizedEntityMentionCandidate::preResolutionEntityKey)
                .containsExactly(
                        tuple("张三", "人物", "person:张三"),
                        tuple("用户", "special", "special:user"),
                        tuple("实体", "rare-type", "other:实体"));

        GraphEntityNormalizationDiagnostics diagnostics = result.diagnostics();
        assertThat(diagnostics.typeFallbackToOtherCount()).isEqualTo(1);
        assertThat(diagnostics.topUnresolvedTypeLabels()).isEqualTo(Map.of("rare-type", 1));
    }

    private static MemoryItem item(Long id, String content) {
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
                null,
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry entry(
            String content, List<ExtractedGraphHints.ExtractedEntityHint> entities) {
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
                new ExtractedGraphHints(entities, List.of()));
    }

    private static ExtractedGraphHints.ExtractedEntityHint entityHint(
            String name, String entityType, Float salience) {
        return entityHint(name, entityType, salience, List.of());
    }

    private static ExtractedGraphHints.ExtractedEntityHint entityHint(
            String name,
            String entityType,
            Float salience,
            List<EntityAliasObservation> aliasObservations) {
        return new ExtractedGraphHints.ExtractedEntityHint(
                name, entityType, salience, aliasObservations);
    }
}
