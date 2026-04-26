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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchRecord;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchState;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.GraphWritePlanApplier;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ItemGraphCommitReceipt;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ItemConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemGraphBatchDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemGraphBatchMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional SQL commit coordinator for item-graph extraction batches.
 */
public final class MybatisItemGraphCommitOperations implements ItemGraphCommitOperations {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final MemoryItemMapper itemMapper;
    private final MemoryItemGraphBatchMapper batchMapper;
    private final GraphWritePlanApplier graphWritePlanApplier;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate auditTransaction;

    public MybatisItemGraphCommitOperations(
            MemoryItemMapper itemMapper,
            MemoryItemGraphBatchMapper batchMapper,
            GraphWritePlanApplier graphWritePlanApplier,
            PlatformTransactionManager transactionManager) {
        this.itemMapper = Objects.requireNonNull(itemMapper, "itemMapper");
        this.batchMapper = Objects.requireNonNull(batchMapper, "batchMapper");
        this.graphWritePlanApplier =
                Objects.requireNonNull(graphWritePlanApplier, "graphWritePlanApplier");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

        recordPending(memoryId, extractionBatchId);
        var itemRows =
                items.stream()
                        .map(item -> ItemConverter.toDO(memoryId, extractionBatchId, item))
                        .toList();
        try {
            writeTransaction.executeWithoutResult(
                    ignored -> {
                        if (!itemRows.isEmpty()) {
                            itemMapper.insertBatch(itemRows);
                        }
                        graphWritePlanApplier.apply(memoryId, extractionBatchId, writePlan);
                        markCommitted(memoryId, extractionBatchId);
                    });
            return ItemGraphCommitReceipt.success(extractionBatchId);
        } catch (RuntimeException error) {
            markRepairRequired(memoryId, extractionBatchId, error);
            throw error;
        }
    }

    @Override
    public Optional<ExtractionBatchRecord> getBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return Optional.ofNullable(loadBatch(memoryId, extractionBatchId)).map(this::toRecord);
    }

    @Override
    public void discardFailedBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        batchMapper.delete(batchQuery(memoryId, extractionBatchId));
    }

    private void recordPending(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        auditTransaction.executeWithoutResult(
                ignored ->
                        batchMapper.insert(
                                newBatch(
                                        memoryId,
                                        extractionBatchId,
                                        ExtractionBatchState.PENDING,
                                        null)));
    }

    private void markCommitted(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        MemoryItemGraphBatchDO batch = requireBatch(memoryId, extractionBatchId);
        batch.setState(ExtractionBatchState.COMMITTED.name());
        batch.setErrorMessage(null);
        batch.setRetryPromotionSupported(Boolean.FALSE);
        batch.setUpdatedAt(Instant.now());
        batchMapper.updateById(batch);
    }

    private void markRepairRequired(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, RuntimeException error) {
        auditTransaction.executeWithoutResult(
                ignored -> {
                    MemoryItemGraphBatchDO batch = loadBatch(memoryId, extractionBatchId);
                    if (batch == null) {
                        batch =
                                newBatch(
                                        memoryId,
                                        extractionBatchId,
                                        ExtractionBatchState.REPAIR_REQUIRED,
                                        error);
                        batchMapper.insert(batch);
                        return;
                    }
                    batch.setState(ExtractionBatchState.REPAIR_REQUIRED.name());
                    batch.setErrorMessage(truncateErrorMessage(error));
                    batch.setRetryPromotionSupported(Boolean.FALSE);
                    batch.setUpdatedAt(Instant.now());
                    batchMapper.updateById(batch);
                });
    }

    private MemoryItemGraphBatchDO newBatch(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            ExtractionBatchState state,
            RuntimeException error) {
        MemoryItemGraphBatchDO batch = new MemoryItemGraphBatchDO();
        batch.setUserId(memoryId.getAttribute("userId"));
        batch.setAgentId(memoryId.getAttribute("agentId"));
        batch.setMemoryId(memoryId.toIdentifier());
        batch.setExtractionBatchId(extractionBatchId.value());
        batch.setState(state.name());
        batch.setErrorMessage(truncateErrorMessage(error));
        batch.setRetryPromotionSupported(Boolean.FALSE);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        return batch;
    }

    private ExtractionBatchRecord toRecord(MemoryItemGraphBatchDO batch) {
        return new ExtractionBatchRecord(
                toMemoryId(batch.getMemoryId()),
                new ExtractionBatchId(batch.getExtractionBatchId()),
                ExtractionBatchState.valueOf(batch.getState()),
                batch.getErrorMessage(),
                Boolean.TRUE.equals(batch.getRetryPromotionSupported()));
    }

    private MemoryItemGraphBatchDO requireBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        MemoryItemGraphBatchDO batch = loadBatch(memoryId, extractionBatchId);
        if (batch == null) {
            throw new IllegalStateException(
                    "missing item-graph batch audit row for extractionBatchId="
                            + extractionBatchId.value());
        }
        return batch;
    }

    private MemoryItemGraphBatchDO loadBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return batchMapper.selectList(batchQuery(memoryId, extractionBatchId)).stream()
                .findFirst()
                .orElse(null);
    }

    private QueryWrapper<MemoryItemGraphBatchDO> batchQuery(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        return new QueryWrapper<MemoryItemGraphBatchDO>()
                .eq("memory_id", memoryId.toIdentifier())
                .eq("extraction_batch_id", extractionBatchId.value());
    }

    private static String truncateErrorMessage(RuntimeException error) {
        String message = resolveAuditMessage(error);
        if (message.isBlank()) {
            return "";
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String resolveAuditMessage(RuntimeException error) {
        if (error == null) {
            return "";
        }
        String fallback = simpleName(error);
        String candidate = normalizeMessage(error.getMessage());
        Throwable cursor = error.getCause();
        while (cursor != null) {
            String cursorName = simpleName(cursor);
            if (!cursorName.isBlank()) {
                fallback = cursorName;
            }
            String cursorMessage = normalizeMessage(cursor.getMessage());
            if (cursorMessage != null) {
                candidate = cursorMessage;
            }
            cursor = cursor.getCause();
        }
        return candidate != null ? candidate : fallback;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.replaceAll("\\s+", " ").trim();
    }

    private static String simpleName(Throwable error) {
        String simpleName = error.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? error.getClass().getName() : simpleName;
    }

    private static MemoryId toMemoryId(String memoryId) {
        String[] parts = memoryId.split(":", 2);
        return parts.length == 2
                ? DefaultMemoryId.of(parts[0], parts[1])
                : DefaultMemoryId.of(memoryId, null);
    }
}
