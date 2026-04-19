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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.GraphEntityNormalizationDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExactCanonicalEntityResolutionStrategyTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-18T00:00:00Z");

    @Test
    void exactCanonicalResolverShouldPreserveStage1aEntityKeysAndAttachResolutionMetadata() {
        var resolved =
                new ExactCanonicalEntityResolutionStrategy()
                        .resolve(
                                MEMORY_ID,
                                normalizedBatch(
                                        candidate(
                                                101L,
                                                "张三",
                                                "人物",
                                                "张三",
                                                "张三",
                                                GraphEntityType.PERSON,
                                                "person:张三",
                                                0.95f),
                                        candidate(
                                                101L,
                                                "用户",
                                                "special",
                                                "用户",
                                                "用户",
                                                GraphEntityType.SPECIAL,
                                                "special:user",
                                                0.90f)),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(resolved.entities())
                .extracting(GraphEntity::entityKey)
                .containsExactly("person:张三", "special:user");
        assertThat(resolved.mentions())
                .extracting(ItemEntityMention::entityKey)
                .containsExactly("person:张三", "special:user");
        assertThat(resolved.entityAliases()).isEmpty();
        assertThat(resolved.mentions().getFirst().metadata())
                .containsEntry("resolutionMode", "exact")
                .containsEntry("resolutionSource", "exact_canonical_hit");
    }

    @Test
    void exactCanonicalResolverShouldCountObservedAliasEvidenceWithoutMerging() {
        var resolved =
                new ExactCanonicalEntityResolutionStrategy()
                        .resolve(
                                MEMORY_ID,
                                new NormalizedGraphBatch(
                                        List.of(
                                                new NormalizedEntityMentionCandidate(
                                                        101L,
                                                        MEMORY_ID.toIdentifier(),
                                                        "开放人工智能",
                                                        "organization",
                                                        "开放人工智能",
                                                        "开放人工智能",
                                                        GraphEntityType.ORGANIZATION,
                                                        "organization:开放人工智能",
                                                        0.93f,
                                                        List.of(
                                                                new EntityAliasObservation(
                                                                        "OpenAI",
                                                                        EntityAliasClass
                                                                                .EXPLICIT_PARENTHETICAL,
                                                                        "entity_inline",
                                                                        0.93f)),
                                                        CREATED_AT)),
                                        List.of(),
                                        GraphEntityNormalizationDiagnostics.empty()),
                                ItemGraphOptions.defaults().withEnabled(true));

        assertThat(resolved.mentions().getFirst().entityKey()).isEqualTo("organization:开放人工智能");
        assertThat(resolved.resolutionDiagnostics().aliasEvidenceObservedCount()).isEqualTo(1);
        assertThat(resolved.resolutionDiagnostics().aliasEvidenceMergedCount()).isZero();
        assertThat(resolved.resolutionDiagnostics().createNewCount()).isZero();
        assertThat(resolved.resolutionDiagnostics().exactFallbackCount()).isEqualTo(1);
        assertThat(
                        ((Number)
                                        resolved.entities()
                                                .getFirst()
                                                .metadata()
                                                .get("aliasEvidenceCount"))
                                .intValue())
                .isEqualTo(1);
        assertThat((List<String>) resolved.entities().getFirst().metadata().get("aliasClasses"))
                .containsExactly("explicit_parenthetical");
        assertThat((List<String>) resolved.entities().getFirst().metadata().get("topAliases"))
                .containsExactly("OpenAI");
    }

    private static NormalizedGraphBatch normalizedBatch(
            NormalizedEntityMentionCandidate... candidates) {
        return new NormalizedGraphBatch(
                List.of(candidates), List.of(), GraphEntityNormalizationDiagnostics.empty());
    }

    private static NormalizedEntityMentionCandidate candidate(
            long itemId,
            String rawName,
            String rawTypeLabel,
            String normalizedName,
            String displayName,
            GraphEntityType entityType,
            String preResolutionEntityKey,
            float salience) {
        return new NormalizedEntityMentionCandidate(
                itemId,
                MEMORY_ID.toIdentifier(),
                rawName,
                rawTypeLabel,
                normalizedName,
                displayName,
                entityType,
                preResolutionEntityKey,
                salience,
                List.of(),
                CREATED_AT);
    }
}
