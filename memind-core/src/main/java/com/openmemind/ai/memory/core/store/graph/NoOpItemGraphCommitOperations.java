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
import java.util.List;
import java.util.Optional;

/**
 * Disabled-mode commit coordinator.
 */
public final class NoOpItemGraphCommitOperations implements ItemGraphCommitOperations {

    public static final NoOpItemGraphCommitOperations INSTANCE =
            new NoOpItemGraphCommitOperations();

    private NoOpItemGraphCommitOperations() {}

    @Override
    public ItemGraphCommitReceipt commit(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items,
            ItemGraphWritePlan writePlan) {
        return ItemGraphCommitReceipt.success(extractionBatchId);
    }

    @Override
    public Optional<ExtractionBatchRecord> getBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return Optional.empty();
    }
}
