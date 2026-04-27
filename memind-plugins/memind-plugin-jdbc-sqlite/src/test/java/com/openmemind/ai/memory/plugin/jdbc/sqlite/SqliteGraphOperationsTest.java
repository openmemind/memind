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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteGraphOperationsTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-22T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    void persistsAndQueriesGraphEntitiesAliasesAndLinks() {
        SqliteGraphOperations graphOperations = new SqliteGraphOperations(dataSource());

        graphOperations.upsertEntities(
                memoryId(), List.of(graphEntity("person:alice", GraphEntityType.PERSON, "Alice")));

        assertThat(graphOperations.listEntitiesByEntityKeys(memoryId(), Set.of("person:alice")))
                .extracting(GraphEntity::entityKey)
                .containsExactly("person:alice");

        graphOperations.upsertEntityAliases(
                memoryId(), List.of(graphAlias("person:alice", GraphEntityType.PERSON, "alice")));

        assertThat(
                        graphOperations.listEntityAliasesByNormalizedAlias(
                                memoryId(), GraphEntityType.PERSON, "alice"))
                .extracting(GraphEntityAlias::entityKey)
                .containsExactly("person:alice");
    }

    @Test
    void appliesGraphWritePlanAndReportsTransactionalBatchSupport() {
        SqliteGraphOperations graphOperations = new SqliteGraphOperations(dataSource());

        graphOperations.applyGraphWritePlan(memoryId(), extractionBatchId(), writePlan());

        assertThat(graphOperations.supportsTransactionalBatchCommit()).isTrue();
        assertThat(
                        graphOperations.listEntitiesByEntityKeys(
                                memoryId(), Set.of("person:alice", "person:bob")))
                .extracting(GraphEntity::entityKey)
                .containsExactly("person:alice", "person:bob");
        assertThat(graphOperations.listItemEntityMentions(memoryId())).hasSize(2);
        assertThat(graphOperations.listItemLinks(memoryId()))
                .extracting(link -> link.linkType())
                .containsExactly(ItemLinkType.SEMANTIC);
        assertThat(
                        graphOperations.listEntityAliasesByNormalizedAlias(
                                memoryId(), GraphEntityType.PERSON, "alice"))
                .hasSize(1);
        assertThat(graphOperations.listEntityCooccurrences(memoryId()))
                .extracting(
                        cooccurrence ->
                                cooccurrence.leftEntityKey() + ":" + cooccurrence.rightEntityKey())
                .containsExactly("person:alice:person:bob");
    }

    private DataSource dataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("graph.db"));
        return dataSource;
    }

    private MemoryId memoryId() {
        return new TestMemoryId("u1", "a1");
    }

    private ExtractionBatchId extractionBatchId() {
        return new ExtractionBatchId("batch-1");
    }

    private ItemGraphWritePlan writePlan() {
        return ItemGraphWritePlan.builder()
                .entities(
                        List.of(
                                graphEntity("person:alice", GraphEntityType.PERSON, "Alice"),
                                graphEntity("person:bob", GraphEntityType.PERSON, "Bob")))
                .mentions(
                        List.of(
                                new ItemEntityMention(
                                        memoryId().toIdentifier(),
                                        1L,
                                        "person:alice",
                                        0.9f,
                                        Map.of(),
                                        BASE_TIME),
                                new ItemEntityMention(
                                        memoryId().toIdentifier(),
                                        1L,
                                        "person:bob",
                                        0.8f,
                                        Map.of(),
                                        BASE_TIME)))
                .aliases(List.of(graphAlias("person:alice", GraphEntityType.PERSON, "alice")))
                .semanticRelations(
                        List.of(
                                new SemanticItemRelation(
                                        1L, 2L, SemanticEvidenceSource.SAME_BATCH_VECTOR, 0.7d)))
                .build();
    }

    private GraphEntity graphEntity(String entityKey, GraphEntityType type, String displayName) {
        return new GraphEntity(
                entityKey,
                memoryId().toIdentifier(),
                displayName,
                type,
                Map.of(),
                BASE_TIME,
                BASE_TIME);
    }

    private GraphEntityAlias graphAlias(
            String entityKey, GraphEntityType entityType, String normalizedAlias) {
        return new GraphEntityAlias(
                memoryId().toIdentifier(),
                entityKey,
                entityType,
                normalizedAlias,
                1,
                Map.of(),
                BASE_TIME,
                BASE_TIME);
    }

    private record TestMemoryId(String userId, String agentId) implements MemoryId {

        @Override
        public String toIdentifier() {
            return userId + ":" + agentId;
        }

        @Override
        public String getAttribute(String key) {
            return switch (key) {
                case "userId" -> userId;
                case "agentId" -> agentId;
                default -> null;
            };
        }
    }
}
