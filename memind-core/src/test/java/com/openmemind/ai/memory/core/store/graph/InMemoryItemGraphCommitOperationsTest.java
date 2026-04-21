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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.store.InMemoryExtractionCommitState;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryItemGraphCommitOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void commitShouldAtomicallyPublishItemsAndGraph() {
        var itemOperations = new InMemoryItemOperations();
        var graphOperations = new InMemoryGraphOperations();
        var commitOperations =
                new InMemoryItemGraphCommitOperations(
                        new InMemoryExtractionCommitState(), itemOperations, graphOperations);
        var batchId = new ExtractionBatchId("batch-1");

        var receipt = commitOperations.commit(MEMORY_ID, batchId, List.of(item(101L)), writePlan());

        assertThat(receipt.extractionBatchId()).isEqualTo(batchId);
        assertThat(commitOperations.getBatch(MEMORY_ID, batchId))
                .hasValueSatisfying(
                        record ->
                                assertThat(record.state())
                                        .isEqualTo(ExtractionBatchState.COMMITTED));
        assertThat(itemOperations.listItems(MEMORY_ID))
                .extracting(MemoryItem::id)
                .containsExactly(101L);
        assertThat(graphOperations.listItemEntityMentions(MEMORY_ID)).hasSize(1);
        assertThat(graphOperations.listEntityAliases(MEMORY_ID))
                .singleElement()
                .extracting(GraphEntityAlias::evidenceCount)
                .isEqualTo(1);
        assertThat(graphOperations.listItemLinks(MEMORY_ID))
                .singleElement()
                .extracting(
                        ItemLink::sourceItemId, ItemLink::targetItemId, ItemLink::evidenceSource)
                .containsExactly(101L, 202L, "vector_search");
    }

    @Test
    void failedPromotionShouldLeaveItemsAndGraphHiddenAndMarkBatchRepairRequired() {
        var itemOperations = new InMemoryItemOperations();
        var graphOperations = new FailingPreviewGraphOperations();
        graphOperations.failNextPreview("graph projection failed");
        var commitOperations =
                new InMemoryItemGraphCommitOperations(
                        new InMemoryExtractionCommitState(), itemOperations, graphOperations);
        var batchId = new ExtractionBatchId("batch-1");

        assertThatThrownBy(
                        () ->
                                commitOperations.commit(
                                        MEMORY_ID, batchId, List.of(item(101L)), writePlan()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("graph projection failed");

        assertThat(commitOperations.getBatch(MEMORY_ID, batchId))
                .hasValueSatisfying(this::assertRepairRequiredBatch);
        assertThat(itemOperations.listItems(MEMORY_ID)).isEmpty();
        assertThat(graphOperations.listItemEntityMentions(MEMORY_ID)).isEmpty();
        assertThat(graphOperations.listEntityAliases(MEMORY_ID)).isEmpty();
        assertThat(graphOperations.listItemLinks(MEMORY_ID)).isEmpty();
    }

    @Test
    void
            retryFailedPromotionShouldPublishPreviouslyStagedBatchWithoutDoubleCountingAliasEvidence() {
        var itemOperations = new InMemoryItemOperations();
        var graphOperations = new FailingPreviewGraphOperations();
        graphOperations.failNextPreview("graph projection failed");
        var commitOperations =
                new InMemoryItemGraphCommitOperations(
                        new InMemoryExtractionCommitState(), itemOperations, graphOperations);
        var batchId = new ExtractionBatchId("batch-1");

        assertThatThrownBy(
                        () ->
                                commitOperations.commit(
                                        MEMORY_ID, batchId, List.of(item(101L)), writePlan()))
                .isInstanceOf(IllegalStateException.class);

        commitOperations.retryFailedBatchPromotion(MEMORY_ID, batchId);

        assertThat(commitOperations.getBatch(MEMORY_ID, batchId))
                .hasValueSatisfying(
                        record ->
                                assertThat(record.state())
                                        .isEqualTo(ExtractionBatchState.COMMITTED));
        assertThat(itemOperations.listItems(MEMORY_ID))
                .extracting(MemoryItem::id)
                .containsExactly(101L);
        assertThat(graphOperations.listEntityAliases(MEMORY_ID))
                .singleElement()
                .extracting(GraphEntityAlias::evidenceCount)
                .isEqualTo(1);
        assertThat(graphOperations.listItemLinks(MEMORY_ID)).hasSize(1);
    }

    private void assertRepairRequiredBatch(ExtractionBatchRecord record) {
        assertThat(record.state()).isEqualTo(ExtractionBatchState.REPAIR_REQUIRED);
        assertThat(record.lastErrorMessage()).contains("graph projection failed");
        assertThat(record.retryPromotionSupported()).isTrue();
    }

    private static MemoryItem item(Long id) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                "Remember the OpenAI deployment",
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                CREATED_AT,
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ItemGraphWritePlan writePlan() {
        return ItemGraphWritePlan.builder()
                .mentions(
                        List.of(
                                new ItemEntityMention(
                                        MEMORY_ID.toIdentifier(),
                                        101L,
                                        "organization:openai",
                                        0.98f,
                                        Map.of(),
                                        CREATED_AT)))
                .aliases(
                        List.of(
                                new GraphEntityAlias(
                                        MEMORY_ID.toIdentifier(),
                                        "organization:openai",
                                        GraphEntityType.ORGANIZATION,
                                        "openai",
                                        1,
                                        Map.of(),
                                        CREATED_AT,
                                        CREATED_AT)))
                .semanticRelations(
                        List.of(
                                new SemanticItemRelation(
                                        101L, 202L, SemanticEvidenceSource.VECTOR_SEARCH, 0.91d)))
                .build();
    }

    private static final class FailingPreviewGraphOperations extends InMemoryGraphOperations {

        private RuntimeException previewFailure;

        private void failNextPreview(String message) {
            previewFailure = new IllegalStateException(message);
        }

        @Override
        public CommittedGraphView previewPromotedBatch(
                MemoryId memoryId, ExtractionBatchId extractionBatchId) {
            if (previewFailure != null) {
                RuntimeException nextFailure = previewFailure;
                previewFailure = null;
                throw nextFailure;
            }
            return super.previewPromotedBatch(memoryId, extractionBatchId);
        }
    }
}
