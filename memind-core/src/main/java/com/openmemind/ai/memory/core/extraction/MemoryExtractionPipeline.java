package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import reactor.core.publisher.Mono;

/**
 * Memory extraction pipeline interface.
 *
 * <p>Defines the memory extraction contract: batch extraction via {@link #extract(ExtractionRequest)}
 * and streaming single-message extraction via {@link #addMessage(MemoryId, Message, ExtractionConfig)}.
 *
 * <p>The primary implementation is {@link MemoryExtractor}. Decorators (e.g., tracing) wrap this
 * interface to add cross-cutting concerns.
 */
public interface MemoryExtractionPipeline {

    /**
     * Execute the complete memory extraction process (batch mode).
     *
     * @param request extraction request containing content, config, and metadata
     * @return extraction result
     */
    Mono<ExtractionResult> extract(ExtractionRequest request);

    /**
     * Process a single message in streaming mode.
     *
     * <p>Messages are buffered internally. When boundary detection triggers sealing,
     * the buffered messages are extracted as a conversation segment.
     *
     * @param memoryId memory identifier (used as buffer key)
     * @param message single message to process
     * @param config extraction configuration
     * @return extraction result when sealing is triggered; empty Mono when buffered without extraction
     */
    Mono<ExtractionResult> addMessage(MemoryId memoryId, Message message, ExtractionConfig config);
}
