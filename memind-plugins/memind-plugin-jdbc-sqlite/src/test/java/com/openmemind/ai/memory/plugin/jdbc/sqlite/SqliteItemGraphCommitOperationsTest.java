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

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchRecord;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchState;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteItemGraphCommitOperationsTest {

    private static final Instant BASE_TIME = Instant.parse("2026-03-22T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    void commitsItemsGraphRowsAndBatchStateAtomically() {
        DataSource dataSource = dataSource();
        SqliteMemoryStore store = new SqliteMemoryStore(dataSource);
        SqliteGraphOperations graphOperations = new SqliteGraphOperations(dataSource);
        SqliteItemGraphCommitOperations commitOperations =
                new SqliteItemGraphCommitOperations(dataSource);

        commitOperations.commit(
                memoryId(), extractionBatchId(), List.of(memoryItem(1L)), writePlan());

        assertThat(store.getItemsByIds(memoryId(), List.of(1L))).hasSize(1);
        assertThat(graphOperations.listEntitiesByEntityKeys(memoryId(), Set.of("person:alice")))
                .extracting(GraphEntity::entityKey)
                .containsExactly("person:alice");
        assertThat(graphOperations.listItemEntityMentions(memoryId())).hasSize(1);
        assertThat(
                        graphOperations.listEntityAliasesByNormalizedAlias(
                                memoryId(), GraphEntityType.PERSON, "alice"))
                .extracting(GraphEntityAlias::entityKey)
                .containsExactly("person:alice");
        assertThat(commitOperations.getBatch(memoryId(), extractionBatchId()))
                .get()
                .extracting(ExtractionBatchRecord::state)
                .isEqualTo(ExtractionBatchState.COMMITTED);
    }

    private DataSource dataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("item-graph-commit.db"));
        return dataSource;
    }

    private MemoryId memoryId() {
        return DefaultMemoryId.of("u1", "a1");
    }

    private ExtractionBatchId extractionBatchId() {
        return new ExtractionBatchId("batch-1");
    }

    private MemoryItem memoryItem(long id) {
        return new MemoryItem(
                id,
                memoryId().toIdentifier(),
                "Alice met Bob",
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                null,
                null,
                "hash-" + id,
                BASE_TIME,
                BASE_TIME,
                BASE_TIME.plusSeconds(60),
                "minute",
                BASE_TIME,
                Map.of(),
                BASE_TIME,
                MemoryItemType.FACT);
    }

    private ItemGraphWritePlan writePlan() {
        return ItemGraphWritePlan.builder()
                .entities(
                        List.of(
                                new GraphEntity(
                                        "person:alice",
                                        memoryId().toIdentifier(),
                                        "Alice",
                                        GraphEntityType.PERSON,
                                        Map.of(),
                                        BASE_TIME,
                                        BASE_TIME)))
                .mentions(
                        List.of(
                                new ItemEntityMention(
                                        memoryId().toIdentifier(),
                                        1L,
                                        "person:alice",
                                        0.9f,
                                        Map.of(),
                                        BASE_TIME)))
                .aliases(
                        List.of(
                                new GraphEntityAlias(
                                        memoryId().toIdentifier(),
                                        "person:alice",
                                        GraphEntityType.PERSON,
                                        "alice",
                                        1,
                                        Map.of(),
                                        BASE_TIME,
                                        BASE_TIME)))
                .build();
    }
}
