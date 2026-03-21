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
package com.openmemind.ai.memory.core;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Memory default implementation
 *
 * <p>Internally combines MemoryExtractionPipeline + MemoryRetriever + MemoryStore,
 * unifying all memory operations into a single facade.
 *
 */
public class DefaultMemory implements Memory {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemory.class);

    private final MemoryExtractionPipeline extractor;
    private final MemoryRetriever retriever;
    private final MemoryStore store;
    private final MemoryVector vector;
    private final ToolStatsService toolStatsService;

    public DefaultMemory(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            MemoryStore store,
            MemoryVector vector,
            ToolStatsService toolStatsService) {
        this.extractor = Objects.requireNonNull(extractor, "extractor must not be null");
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.vector = Objects.requireNonNull(vector, "vector must not be null");
        this.toolStatsService =
                Objects.requireNonNull(toolStatsService, "toolStatsService must not be null");
    }

    // ===== Generic extraction =====

    @Override
    public Mono<ExtractionResult> extract(MemoryId memoryId, RawContent content) {
        return extract(memoryId, content, ExtractionConfig.defaults());
    }

    @Override
    public Mono<ExtractionResult> extract(
            MemoryId memoryId, RawContent content, ExtractionConfig config) {
        var request = ExtractionRequest.of(memoryId, content).withConfig(config);
        return extractor.extract(request);
    }

    // ===== Dialogue memory extraction =====

    @Override
    public Mono<ExtractionResult> addMessages(MemoryId memoryId, List<Message> messages) {
        return extract(memoryId, new ConversationContent(messages));
    }

    @Override
    public Mono<ExtractionResult> addMessages(
            MemoryId memoryId, List<Message> messages, ExtractionConfig config) {
        return extract(memoryId, new ConversationContent(messages), config);
    }

    @Override
    public Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message) {
        return extractor.addMessage(memoryId, message, ExtractionConfig.defaults());
    }

    @Override
    public Mono<ExtractionResult> addMessage(
            MemoryId memoryId, Message message, ExtractionConfig config) {
        return extractor.addMessage(memoryId, message, config);
    }

    // ===== Memory retrieval =====

    @Override
    public Mono<RetrievalResult> retrieve(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
        return retriever.retrieve(RetrievalRequest.of(memoryId, query, strategy));
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        return retriever.retrieve(request);
    }

    @Override
    public Mono<Void> deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
        var requestedIds = List.copyOf(itemIds);
        if (requestedIds.isEmpty()) {
            return Mono.<Void>empty();
        }

        return Mono.fromCallable(() -> store.getItemsByIds(memoryId, requestedIds))
                .map(
                        items ->
                                items.stream()
                                        .map(MemoryItem::vectorId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .toList())
                .flatMap(
                        vectorIds ->
                                vectorIds.isEmpty()
                                        ? Mono.<Void>empty()
                                        : vector.deleteBatch(memoryId, vectorIds))
                .then(Mono.<Void>fromRunnable(() -> store.deleteItems(memoryId, requestedIds)))
                .doOnSuccess(ignored -> retriever.onDataChanged(memoryId));
    }

    @Override
    public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
        var requestedIds = List.copyOf(insightIds);
        if (requestedIds.isEmpty()) {
            return Mono.<Void>empty();
        }

        return Mono.<Void>fromRunnable(() -> store.deleteInsights(memoryId, requestedIds))
                .doOnSuccess(ignored -> retriever.onDataChanged(memoryId));
    }

    // ===== Agent memory reporting =====

    @Override
    public Mono<ExtractionResult> reportToolCall(MemoryId memoryId, ToolCallRecord record) {
        return reportToolCalls(memoryId, List.of(record));
    }

    @Override
    public Mono<ExtractionResult> reportToolCalls(MemoryId memoryId, List<ToolCallRecord> records) {
        var content = new ToolCallContent(records);
        var request = ExtractionRequest.toolCall(memoryId, content);
        return extractor.extract(request);
    }

    @Override
    public Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName) {
        return toolStatsService.getToolStats(memoryId, toolName);
    }

    @Override
    public Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId) {
        return toolStatsService.getAllToolStats(memoryId);
    }
}
