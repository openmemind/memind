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
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.context.ContextWindow;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
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
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
    private final MemoryStore memoryStore;
    private final MemoryVector vector;
    private final ToolStatsService toolStatsService;
    private final InsightLayer insightLayer;
    private final AutoCloseable lifecycle;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultMemory(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            MemoryStore memoryStore,
            MemoryVector vector,
            ToolStatsService toolStatsService) {
        this(extractor, retriever, memoryStore, vector, toolStatsService, null, null);
    }

    public DefaultMemory(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            MemoryStore memoryStore,
            MemoryVector vector,
            ToolStatsService toolStatsService,
            InsightLayer insightLayer,
            AutoCloseable lifecycle) {
        this.extractor = Objects.requireNonNull(extractor, "extractor must not be null");
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.vector = Objects.requireNonNull(vector, "vector must not be null");
        this.toolStatsService =
                Objects.requireNonNull(toolStatsService, "toolStatsService must not be null");
        this.insightLayer = insightLayer;
        this.lifecycle = lifecycle;
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

    // ===== Context =====

    @Override
    public Mono<ContextWindow> getContext(ContextRequest request) {
        Objects.requireNonNull(request, "request is required");

        ConversationBuffer buffer = memoryStore.conversationBufferStore();
        var bufferKey = request.memoryId().toIdentifier();
        List<Message> messages = List.copyOf(buffer.load(bufferKey));
        int bufferTokens = TokenUtils.countTokens(messages);

        if (!request.includeMemories() || messages.isEmpty()) {
            return Mono.just(ContextWindow.bufferOnly(messages, bufferTokens));
        }

        String query = buildQueryFromRecentMessages(messages);
        return retriever
                .retrieve(RetrievalRequest.of(request.memoryId(), query, request.strategy()))
                .map(
                        memories -> {
                            int memoriesTokens = TokenUtils.countTokens(memories.formattedResult());
                            return new ContextWindow(
                                    messages, memories, bufferTokens + memoriesTokens);
                        });
    }

    @Override
    public Mono<ExtractionResult> commit(MemoryId memoryId) {
        return commit(memoryId, ExtractionConfig.defaults());
    }

    @Override
    public Mono<ExtractionResult> commit(MemoryId memoryId, ExtractionConfig config) {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(config, "config is required");

        ConversationBuffer buffer = memoryStore.conversationBufferStore();
        var bufferKey = memoryId.toIdentifier();
        List<Message> messages = List.copyOf(buffer.drain(bufferKey));

        if (messages.isEmpty()) {
            return Mono.just(
                    ExtractionResult.success(
                            memoryId,
                            com.openmemind.ai.memory.core.extraction.result.RawDataResult.empty(),
                            com.openmemind.ai.memory.core.extraction.result.MemoryItemResult
                                    .empty(),
                            com.openmemind.ai.memory.core.extraction.result.InsightResult.empty(),
                            Duration.ZERO));
        }

        return extract(memoryId, new ConversationContent(messages), config);
    }

    private static String buildQueryFromRecentMessages(List<Message> messages) {
        int tail = Math.min(messages.size(), 5);
        return messages.subList(messages.size() - tail, messages.size()).stream()
                .map(Message::textContent)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));
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

        return Mono.fromCallable(
                        () -> memoryStore.itemOperations().getItemsByIds(memoryId, requestedIds))
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
                .then(
                        Mono.<Void>fromRunnable(
                                () ->
                                        memoryStore
                                                .itemOperations()
                                                .deleteItems(memoryId, requestedIds)))
                .doOnSuccess(ignored -> retriever.onDataChanged(memoryId));
    }

    @Override
    public Mono<Void> deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
        var requestedIds = List.copyOf(insightIds);
        if (requestedIds.isEmpty()) {
            return Mono.<Void>empty();
        }

        return Mono.<Void>fromRunnable(
                        () ->
                                memoryStore
                                        .insightOperations()
                                        .deleteInsights(memoryId, requestedIds))
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

    @Override
    public void flushInsights(MemoryId memoryId, String language) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (insightLayer == null) {
            return;
        }
        if (language == null || language.isBlank()) {
            insightLayer.flush(memoryId);
            return;
        }
        insightLayer.flush(memoryId, language);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || lifecycle == null) {
            return;
        }

        try {
            lifecycle.close();
        } catch (Exception e) {
            log.warn("Failed to close memory lifecycle", e);
        }
    }
}
