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
package com.openmemind.ai.memory.core.extraction.rawdata.processor;

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for conversation content. Handles chunking (fixed or LLM-based)
 * and LLM caption generation.
 */
public class ConversationContentProcessor implements RawContentProcessor<ConversationContent> {

    private final ConversationChunker conversationChunker;
    private final LlmConversationChunker llmConversationChunker;
    private final ConversationChunkingConfig chunkingConfig;
    private final CaptionGenerator captionGenerator;
    private final ItemExtractionStrategy itemExtractionStrategy;

    /**
     * Creates a conversation content processor.
     *
     * @param conversationChunker fixed-size chunker (required)
     * @param llmConversationChunker LLM-based chunker (nullable, used when strategy is LLM)
     * @param chunkingConfig chunking configuration
     * @param captionGenerator caption generator for segments
     * @param itemExtractionStrategy item extraction strategy (nullable, uses framework default)
     */
    public ConversationContentProcessor(
            ConversationChunker conversationChunker,
            LlmConversationChunker llmConversationChunker,
            ConversationChunkingConfig chunkingConfig,
            CaptionGenerator captionGenerator,
            ItemExtractionStrategy itemExtractionStrategy) {
        this.conversationChunker =
                Objects.requireNonNull(conversationChunker, "conversationChunker must not be null");
        this.llmConversationChunker = llmConversationChunker;
        this.chunkingConfig =
                Objects.requireNonNull(chunkingConfig, "chunkingConfig must not be null");
        this.captionGenerator =
                Objects.requireNonNull(captionGenerator, "captionGenerator must not be null");
        this.itemExtractionStrategy = itemExtractionStrategy;
    }

    @Override
    public Class<ConversationContent> contentClass() {
        return ConversationContent.class;
    }

    @Override
    public String contentType() {
        return "CONVERSATION";
    }

    @Override
    public Mono<List<Segment>> chunk(ConversationContent content) {
        var messages = content.getMessages();
        if (messages.isEmpty()) {
            return Mono.just(List.of());
        }
        if (chunkingConfig.strategy() == ConversationSegmentStrategy.LLM
                && llmConversationChunker != null) {
            return llmConversationChunker.chunk(messages, chunkingConfig);
        }
        return Mono.just(conversationChunker.chunk(messages, chunkingConfig));
    }

    @Override
    public CaptionGenerator captionGenerator() {
        return captionGenerator;
    }

    @Override
    public ItemExtractionStrategy itemExtractionStrategy() {
        return itemExtractionStrategy;
    }

    @Override
    public java.util.Set<MemoryCategory> allowedCategories() {
        return EnumSet.of(
                MemoryCategory.PROFILE,
                MemoryCategory.BEHAVIOR,
                MemoryCategory.EVENT,
                MemoryCategory.DIRECTIVE,
                MemoryCategory.PLAYBOOK,
                MemoryCategory.RESOLUTION);
    }

    @Override
    public Instant resolveSegmentStartTime(
            ConversationContent content, Segment segment, Instant fallback) {
        if (segment.runtimeContext() != null && segment.runtimeContext().startTime() != null) {
            return segment.runtimeContext().startTime();
        }
        if (segment.boundary() instanceof MessageBoundary boundary) {
            int index = boundary.startMessage();
            if (index >= 0 && index < content.getMessages().size()) {
                Instant timestamp = content.getMessages().get(index).timestamp();
                return timestamp != null ? timestamp : fallback;
            }
        }
        return fallback;
    }

    @Override
    public Instant resolveSegmentEndTime(
            ConversationContent content, Segment segment, Instant fallback) {
        if (segment.runtimeContext() != null && segment.runtimeContext().observedAt() != null) {
            return segment.runtimeContext().observedAt();
        }
        if (segment.boundary() instanceof MessageBoundary boundary) {
            int index = boundary.endMessage() - 1;
            if (index >= 0 && index < content.getMessages().size()) {
                Instant timestamp = content.getMessages().get(index).timestamp();
                return timestamp != null ? timestamp : fallback;
            }
        }
        return fallback;
    }

    @Override
    public boolean supportsInsight() {
        return true;
    }
}
