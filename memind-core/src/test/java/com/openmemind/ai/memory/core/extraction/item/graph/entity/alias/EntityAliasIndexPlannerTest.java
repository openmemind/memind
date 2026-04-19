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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.GraphEntityNormalizationDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityAliasIndexPlannerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-19T00:00:00Z");

    @Test
    void plannerShouldPersistOnlyEligibleMergeSourcesAndAggregateDuplicates() {
        var planner = new EntityAliasIndexPlanner();
        var batch =
                new ResolvedGraphBatch(
                        List.of(
                                entity(
                                        "organization:google",
                                        "Google",
                                        GraphEntityType.ORGANIZATION),
                                entity("special:user", "用户", GraphEntityType.SPECIAL)),
                        List.of(),
                        List.of(
                                mention(
                                        101L,
                                        "organization:google",
                                        "谷歌",
                                        "historical_alias_hit",
                                        CREATED_AT.plusSeconds(20)),
                                mention(
                                        102L,
                                        "organization:google",
                                        "谷歌",
                                        "explicit_alias_evidence_hit",
                                        CREATED_AT),
                                mention(
                                        103L,
                                        "organization:google",
                                        "google",
                                        "exact_canonical_hit",
                                        CREATED_AT.plusSeconds(40)),
                                mention(
                                        104L,
                                        "special:user",
                                        "用户",
                                        "historical_alias_hit",
                                        CREATED_AT.plusSeconds(60))),
                        List.of(),
                        GraphEntityNormalizationDiagnostics.empty(),
                        EntityResolutionDiagnostics.empty());

        assertThat(planner.plan(MEMORY_ID, batch))
                .extracting(
                        GraphEntityAlias::entityKey,
                        GraphEntityAlias::normalizedAlias,
                        GraphEntityAlias::evidenceCount,
                        GraphEntityAlias::createdAt,
                        GraphEntityAlias::updatedAt)
                .containsExactly(
                        tuple(
                                "organization:google",
                                "谷歌",
                                2,
                                CREATED_AT,
                                CREATED_AT.plusSeconds(20)));
    }

    private static GraphEntity entity(
            String entityKey, String displayName, GraphEntityType entityType) {
        return new GraphEntity(
                entityKey,
                MEMORY_ID.toIdentifier(),
                displayName,
                entityType,
                Map.of(),
                CREATED_AT,
                CREATED_AT);
    }

    private static ItemEntityMention mention(
            long itemId,
            String entityKey,
            String normalizedName,
            String resolutionSource,
            Instant createdAt) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(),
                itemId,
                entityKey,
                0.95f,
                Map.of(
                        "source",
                        "item_extraction",
                        "normalizedName",
                        normalizedName,
                        "resolutionMode",
                        "conservative",
                        "resolutionSource",
                        resolutionSource),
                createdAt);
    }
}
