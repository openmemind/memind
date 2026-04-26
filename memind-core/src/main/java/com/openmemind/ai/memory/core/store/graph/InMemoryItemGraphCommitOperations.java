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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchRecord;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ItemGraphCommitReceipt;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.InMemoryCommittedBatchView;
import com.openmemind.ai.memory.core.store.InMemoryExtractionCommitState;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory commit coordinator that publishes items and graph snapshots atomically.
 */
public final class InMemoryItemGraphCommitOperations implements ItemGraphCommitOperations {

    private final InMemoryExtractionCommitState commitState;
    private final InMemoryItemOperations itemOperations;
    private final InMemoryGraphOperations graphOperations;

    public InMemoryItemGraphCommitOperations(
            InMemoryExtractionCommitState commitState,
            InMemoryItemOperations itemOperations,
            InMemoryGraphOperations graphOperations) {
        this.commitState = Objects.requireNonNull(commitState, "commitState");
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
    }

    @Override
    public ItemGraphCommitReceipt commit(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items,
            ItemGraphWritePlan writePlan) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(extractionBatchId, "extractionBatchId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(writePlan, "writePlan");

        var normalizedPlan = writePlan.normalized();
        commitState.stage(memoryId, extractionBatchId, List.copyOf(items), normalizedPlan);
        try {
            graphOperations.applyGraphWritePlan(memoryId, extractionBatchId, normalizedPlan);
            promoteCommittedBatch(memoryId, extractionBatchId);
            return ItemGraphCommitReceipt.success(extractionBatchId);
        } catch (RuntimeException error) {
            commitState.markRepairRequired(memoryId, extractionBatchId, error);
            throw error;
        }
    }

    @Override
    public Optional<ExtractionBatchRecord> getBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return commitState.getBatch(memoryId, extractionBatchId);
    }

    @Override
    public void retryFailedBatchPromotion(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        var stagedBatch = commitState.getRequiredStagedBatch(memoryId, extractionBatchId);
        graphOperations.applyGraphWritePlan(memoryId, extractionBatchId, stagedBatch.writePlan());
        commitState.retryPromotionAtomically(
                memoryId,
                extractionBatchId,
                staged ->
                        new InMemoryCommittedBatchView(
                                itemOperations.previewCommittedBatch(memoryId, staged.items()),
                                graphOperations.previewPromotedBatch(memoryId, extractionBatchId)),
                committedView -> installCommittedBatch(memoryId, extractionBatchId, committedView));
    }

    @Override
    public void discardFailedBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        graphOperations.discardPendingBatch(memoryId, extractionBatchId);
        commitState.discard(memoryId, extractionBatchId);
    }

    private void promoteCommittedBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        commitState.promoteAtomically(
                memoryId,
                extractionBatchId,
                staged ->
                        new InMemoryCommittedBatchView(
                                itemOperations.previewCommittedBatch(memoryId, staged.items()),
                                graphOperations.previewPromotedBatch(memoryId, extractionBatchId)),
                committedView -> installCommittedBatch(memoryId, extractionBatchId, committedView));
    }

    private void installCommittedBatch(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            InMemoryCommittedBatchView committedView) {
        itemOperations.installCommittedBatch(memoryId, committedView.items());
        graphOperations.installCommittedBatch(memoryId, extractionBatchId, committedView.graph());
    }
}
