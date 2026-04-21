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
package com.openmemind.ai.memory.plugin.store.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchState;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalRelationCode;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("MyBatis item-graph commit operations")
class MybatisItemGraphCommitOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Test
    @DisplayName("transactional commit publishes items and graph atomically")
    void transactionalCommitPublishesItemsAndGraphAtomically(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-commit-success.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var commitOps = store.itemGraphCommitOperations();
                            var batchId = new ExtractionBatchId("batch-1");

                            commitOps.commit(
                                    MEMORY_ID,
                                    batchId,
                                    List.of(item(101L, "first item"), item(102L, "second item")),
                                    validPlan());

                            assertThat(store.itemOperations().listItems(MEMORY_ID))
                                    .extracting(MemoryItem::id)
                                    .containsExactlyInAnyOrder(101L, 102L);
                            assertThat(store.graphOperations().listItemLinks(MEMORY_ID))
                                    .extracting(ItemLink::relationCode, ItemLink::evidenceSource)
                                    .containsExactlyInAnyOrder(
                                            tuple("before", null), tuple(null, "vector_search"));
                            assertThat(commitOps.getBatch(MEMORY_ID, batchId))
                                    .hasValueSatisfying(
                                            record -> {
                                                assertThat(record.state())
                                                        .isEqualTo(ExtractionBatchState.COMMITTED);
                                                assertThat(record.retryPromotionSupported())
                                                        .isFalse();
                                            });
                        });
    }

    @Test
    @DisplayName("transactional commit marks repair required without exposing source facts")
    void transactionalCommitMarksRepairRequiredWithoutExposingSourceFacts(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-commit-failure.db"))
                .run(
                        context -> {
                            MemoryStore store = context.getBean(MemoryStore.class);
                            var commitOps = store.itemGraphCommitOperations();
                            var batchId = new ExtractionBatchId("batch-2");

                            assertThatThrownBy(
                                            () ->
                                                    commitOps.commit(
                                                            MEMORY_ID,
                                                            batchId,
                                                            List.of(
                                                                    item(201L, "duplicate item"),
                                                                    item(201L, "duplicate item")),
                                                            validPlan()))
                                    .isInstanceOf(RuntimeException.class);

                            assertThat(store.itemOperations().listItems(MEMORY_ID)).isEmpty();
                            assertThat(store.graphOperations().listItemLinks(MEMORY_ID)).isEmpty();
                            assertThat(commitOps.getBatch(MEMORY_ID, batchId))
                                    .hasValueSatisfying(
                                            record -> {
                                                assertThat(record.state())
                                                        .isEqualTo(
                                                                ExtractionBatchState
                                                                        .REPAIR_REQUIRED);
                                                assertThat(record.lastErrorMessage())
                                                        .isNotBlank()
                                                        .doesNotContain("duplicate item");
                                            });
                        });
    }

    @Test
    @DisplayName("discard failed batch removes durable audit row")
    void discardFailedBatchRemovesDurableAuditRow(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-commit-discard.db"))
                .run(
                        context -> {
                            var commitOps =
                                    context.getBean(MemoryStore.class).itemGraphCommitOperations();
                            var batchId = new ExtractionBatchId("batch-3");

                            provokeCommitFailure(commitOps, batchId);
                            commitOps.discardFailedBatch(MEMORY_ID, batchId);

                            assertThat(commitOps.getBatch(MEMORY_ID, batchId)).isEmpty();
                        });
    }

    @Test
    @DisplayName("mybatis commit keeps retry promotion unsupported and audits that fact")
    void mybatisCommitKeepsRetryPromotionUnsupportedAndAuditsThatFact(@TempDir Path tempDir) {
        newContextRunner(tempDir.resolve("graph-commit-retry-unsupported.db"))
                .run(
                        context -> {
                            var commitOps =
                                    context.getBean(MemoryStore.class).itemGraphCommitOperations();
                            var batchId = new ExtractionBatchId("batch-4");

                            provokeCommitFailure(commitOps, batchId);

                            assertThat(commitOps.getBatch(MEMORY_ID, batchId))
                                    .hasValueSatisfying(
                                            record ->
                                                    assertThat(record.retryPromotionSupported())
                                                            .isFalse());
                            assertThatThrownBy(
                                            () ->
                                                    commitOps.retryFailedBatchPromotion(
                                                            MEMORY_ID, batchId))
                                    .isInstanceOf(UnsupportedOperationException.class);
                        });
    }

    private ApplicationContextRunner newContextRunner(Path dbPath) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MemoryMybatisPlusAutoConfiguration.class,
                                MemorySchemaAutoConfiguration.class,
                                MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(
                        MybatisPlusMemoryStoreBatchOperationsTest.TestInfrastructureConfig.class)
                .withPropertyValues(
                        "test.sqlite.path=" + dbPath,
                        "memind.store.init-schema=true",
                        "spring.main.web-application-type=none");
    }

    private static void provokeCommitFailure(
            com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations commitOps,
            ExtractionBatchId batchId) {
        assertThatThrownBy(
                        () ->
                                commitOps.commit(
                                        MEMORY_ID,
                                        batchId,
                                        List.of(
                                                item(301L, "duplicate item"),
                                                item(301L, "duplicate item")),
                                        validPlan()))
                .isInstanceOf(RuntimeException.class);
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
                NOW,
                NOW.minusSeconds(30),
                Map.of("content", content),
                NOW,
                MemoryItemType.FACT);
    }

    private static ItemGraphWritePlan validPlan() {
        return ItemGraphWritePlan.builder()
                .entities(
                        List.of(
                                new GraphEntity(
                                        "organization:openai",
                                        MEMORY_ID.toIdentifier(),
                                        "OpenAI",
                                        GraphEntityType.ORGANIZATION,
                                        Map.of("source", "item_extraction"),
                                        NOW,
                                        NOW)))
                .mentions(
                        List.of(
                                new ItemEntityMention(
                                        MEMORY_ID.toIdentifier(),
                                        101L,
                                        "organization:openai",
                                        0.95f,
                                        Map.of("source", "item_extraction"),
                                        NOW)))
                .aliases(
                        List.of(
                                new GraphEntityAlias(
                                        MEMORY_ID.toIdentifier(),
                                        "organization:openai",
                                        GraphEntityType.ORGANIZATION,
                                        "openai",
                                        1,
                                        Map.of("source", "item_extraction"),
                                        NOW,
                                        NOW)))
                .semanticRelations(
                        List.of(
                                new SemanticItemRelation(
                                        101L, 102L, SemanticEvidenceSource.VECTOR_SEARCH, 0.91d)))
                .temporalRelations(
                        List.of(
                                new TemporalItemRelation(
                                        101L, 102L, TemporalRelationCode.BEFORE, 0.87d)))
                .affectedEntityKeys(List.of("organization:openai"))
                .build();
    }
}
