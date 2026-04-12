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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.processor;

import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.caption.ToolCallCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.chunk.ToolCallChunker;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.content.ToolCallContent;
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
        return ToolCallContent.TYPE;
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
