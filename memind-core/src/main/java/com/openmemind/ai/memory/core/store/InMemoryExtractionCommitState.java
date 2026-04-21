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
package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchRecord;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchState;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tracks staged in-memory extraction batches until atomic publish succeeds.
 */
public final class InMemoryExtractionCommitState {

    private final Map<String, Map<ExtractionBatchId, StagedExtractionBatch>> stagedBatches =
            new ConcurrentHashMap<>();
    private final Map<String, Map<ExtractionBatchId, ExtractionBatchRecord>> batchRecords =
            new ConcurrentHashMap<>();
    private final Map<String, Object> memoryLocks = new ConcurrentHashMap<>();

    public void stage(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items,
            ItemGraphWritePlan writePlan) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(extractionBatchId, "extractionBatchId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(writePlan, "writePlan");

        synchronized (memoryLock(memoryId)) {
            stagedBatches
                    .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>())
                    .put(
                            extractionBatchId,
                            new StagedExtractionBatch(
                                    memoryId,
                                    extractionBatchId,
                                    List.copyOf(items),
                                    writePlan.normalized()));
            batchRecords
                    .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>())
                    .put(
                            extractionBatchId,
                            new ExtractionBatchRecord(
                                    memoryId,
                                    extractionBatchId,
                                    ExtractionBatchState.PENDING,
                                    "",
                                    true));
        }
    }

    public Optional<ExtractionBatchRecord> getBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return Optional.ofNullable(
                batchRecords
                        .getOrDefault(memoryId.toIdentifier(), Map.of())
                        .get(extractionBatchId));
    }

    public StagedExtractionBatch getRequiredStagedBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        var staged =
                stagedBatches
                        .getOrDefault(memoryId.toIdentifier(), Map.of())
                        .get(extractionBatchId);
        if (staged == null) {
            throw new IllegalStateException(
                    "no staged extraction batch for extractionBatchId=" + extractionBatchId);
        }
        return staged;
    }

    public <T> T promoteAtomically(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            Function<StagedExtractionBatch, T> previewBuilder,
            Consumer<T> installer) {
        synchronized (memoryLock(memoryId)) {
            var staged = getRequiredStagedBatch(memoryId, extractionBatchId);
            ensureState(memoryId, extractionBatchId, ExtractionBatchState.PENDING);
            T prepared = previewBuilder.apply(staged);
            installer.accept(prepared);
            markCommitted(memoryId, extractionBatchId);
            clearStagedBatch(memoryId, extractionBatchId);
            return prepared;
        }
    }

    public <T> T retryPromotionAtomically(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            Function<StagedExtractionBatch, T> previewBuilder,
            Consumer<T> installer) {
        synchronized (memoryLock(memoryId)) {
            var staged = getRequiredStagedBatch(memoryId, extractionBatchId);
            ensureState(memoryId, extractionBatchId, ExtractionBatchState.REPAIR_REQUIRED);
            T prepared = previewBuilder.apply(staged);
            installer.accept(prepared);
            markCommitted(memoryId, extractionBatchId);
            clearStagedBatch(memoryId, extractionBatchId);
            return prepared;
        }
    }

    public void markRepairRequired(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, RuntimeException error) {
        synchronized (memoryLock(memoryId)) {
            batchRecords
                    .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>())
                    .put(
                            extractionBatchId,
                            new ExtractionBatchRecord(
                                    memoryId,
                                    extractionBatchId,
                                    ExtractionBatchState.REPAIR_REQUIRED,
                                    error == null ? "" : String.valueOf(error.getMessage()),
                                    true));
        }
    }

    public void discard(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        synchronized (memoryLock(memoryId)) {
            clearStagedBatch(memoryId, extractionBatchId);
            var scopedRecords = batchRecords.get(memoryId.toIdentifier());
            if (scopedRecords != null) {
                scopedRecords.remove(extractionBatchId);
                if (scopedRecords.isEmpty()) {
                    batchRecords.remove(memoryId.toIdentifier());
                }
            }
        }
    }

    private void ensureState(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            ExtractionBatchState expectedState) {
        var record =
                getBatch(memoryId, extractionBatchId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "missing extraction batch record for "
                                                        + extractionBatchId));
        if (record.state() != expectedState) {
            throw new IllegalStateException(
                    "extraction batch "
                            + extractionBatchId
                            + " must be "
                            + expectedState
                            + " but was "
                            + record.state());
        }
    }

    private void markCommitted(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        batchRecords
                .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>())
                .put(
                        extractionBatchId,
                        new ExtractionBatchRecord(
                                memoryId,
                                extractionBatchId,
                                ExtractionBatchState.COMMITTED,
                                "",
                                true));
    }

    private void clearStagedBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        var scopedBatches = stagedBatches.get(memoryId.toIdentifier());
        if (scopedBatches == null) {
            return;
        }
        scopedBatches.remove(extractionBatchId);
        if (scopedBatches.isEmpty()) {
            stagedBatches.remove(memoryId.toIdentifier());
        }
    }

    private Object memoryLock(MemoryId memoryId) {
        return memoryLocks.computeIfAbsent(memoryId.toIdentifier(), ignored -> new Object());
    }

    public record StagedExtractionBatch(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items,
            ItemGraphWritePlan writePlan) {

        public StagedExtractionBatch {
            Objects.requireNonNull(memoryId, "memoryId");
            Objects.requireNonNull(extractionBatchId, "extractionBatchId");
            items = items == null ? List.of() : List.copyOf(items);
            writePlan =
                    writePlan == null
                            ? ItemGraphWritePlan.builder().build()
                            : writePlan.normalized();
        }
    }
}
