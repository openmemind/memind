package com.openmemind.ai.memory.core.extraction.rawdata.processor;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.ToolCallCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ToolCallChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for tool call content. Groups records by toolName and uses
 * simple (non-LLM) caption generation.
 */
public class ToolCallContentProcessor implements RawContentProcessor<ToolCallContent> {

    private final ToolCallChunker toolCallChunker;
    private final CaptionGenerator captionGenerator;
    private final ItemExtractionStrategy itemExtractionStrategy;

    /**
     * Creates a tool call content processor with all dependencies.
     *
     * @param toolCallChunker chunker for tool call records (required)
     * @param captionGenerator caption generator for segments (required)
     * @param itemExtractionStrategy item extraction strategy for tool calls (required)
     */
    public ToolCallContentProcessor(
            ToolCallChunker toolCallChunker,
            CaptionGenerator captionGenerator,
            ItemExtractionStrategy itemExtractionStrategy) {
        this.toolCallChunker =
                Objects.requireNonNull(toolCallChunker, "toolCallChunker must not be null");
        this.captionGenerator =
                Objects.requireNonNull(captionGenerator, "captionGenerator must not be null");
        this.itemExtractionStrategy =
                Objects.requireNonNull(
                        itemExtractionStrategy, "itemExtractionStrategy must not be null");
    }

    /**
     * Creates a tool call content processor with default chunker and caption generator.
     *
     * @param itemExtractionStrategy item extraction strategy for tool calls (required)
     */
    public ToolCallContentProcessor(ItemExtractionStrategy itemExtractionStrategy) {
        this(new ToolCallChunker(), new ToolCallCaptionGenerator(), itemExtractionStrategy);
    }

    @Override
    public Class<ToolCallContent> contentClass() {
        return ToolCallContent.class;
    }

    @Override
    public String contentType() {
        return "TOOL_CALL";
    }

    @Override
    public Mono<List<Segment>> chunk(ToolCallContent content) {
        return Mono.just(toolCallChunker.chunk(content.calls()));
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
    public boolean supportsInsight() {
        return false;
    }
}
