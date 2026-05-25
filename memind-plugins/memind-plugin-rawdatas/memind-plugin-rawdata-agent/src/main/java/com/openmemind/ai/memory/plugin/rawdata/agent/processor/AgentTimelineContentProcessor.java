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
package com.openmemind.ai.memory.plugin.rawdata.agent.processor;

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.plugin.rawdata.agent.caption.AgentCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.agent.chunk.AgentTimelineChunker;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * RawData processor for coding-agent timelines.
 */
public final class AgentTimelineContentProcessor
        implements RawContentProcessor<AgentTimelineContent> {

    private final AgentTimelineChunker chunker;
    private final CaptionGenerator captionGenerator;
    private final ItemExtractionStrategy itemExtractionStrategy;

    public AgentTimelineContentProcessor(ItemExtractionStrategy itemExtractionStrategy) {
        this(new AgentTimelineChunker(), new AgentCaptionGenerator(), itemExtractionStrategy);
    }

    public AgentTimelineContentProcessor(
            AgentTimelineChunker chunker,
            CaptionGenerator captionGenerator,
            ItemExtractionStrategy itemExtractionStrategy) {
        this.chunker = Objects.requireNonNull(chunker, "chunker must not be null");
        this.captionGenerator =
                Objects.requireNonNull(captionGenerator, "captionGenerator must not be null");
        this.itemExtractionStrategy =
                Objects.requireNonNull(
                        itemExtractionStrategy, "itemExtractionStrategy must not be null");
    }

    @Override
    public Class<AgentTimelineContent> contentClass() {
        return AgentTimelineContent.class;
    }

    @Override
    public String contentType() {
        return AgentTimelineContent.TYPE;
    }

    @Override
    public Mono<List<Segment>> chunk(AgentTimelineContent content) {
        return Mono.just(chunker.chunk(content));
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
    public Set<MemoryCategory> allowedCategories() {
        return EnumSet.allOf(MemoryCategory.class);
    }

    @Override
    public boolean usesSourceIdentity() {
        return true;
    }

    @Override
    public boolean supportsInsight() {
        return true;
    }
}
