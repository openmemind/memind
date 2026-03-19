package com.openmemind.ai.memory.core;

import com.openmemind.ai.memory.core.data.MemoryId;
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
    private final ToolStatsService toolStatsService;

    public DefaultMemory(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            MemoryStore store,
            ToolStatsService toolStatsService) {
        this.extractor = Objects.requireNonNull(extractor, "extractor must not be null");
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
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
