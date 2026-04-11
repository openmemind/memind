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
package com.openmemind.ai.memory.core.plugin;

import com.openmemind.ai.memory.core.extraction.item.strategy.LlmToolCallItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.ToolCallCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ImageSegmentComposer;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ToolCallChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.AudioContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ImageContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ToolCallContentProcessor;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import java.util.List;
import java.util.Map;

/**
 * Transitional in-core plugin that contributes the current built-in non-conversation rawdata
 * processors through the same SPI path as external plugins.
 */
public final class CoreBuiltinRawDataPlugin implements RawDataPlugin {

    @Override
    public String pluginId() {
        return "core-builtin-rawdata";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        var rawdata = context.buildOptions().extraction().rawdata();
        return List.of(
                new ToolCallContentProcessor(
                        new ToolCallChunker(rawdata.toolCall()),
                        new ToolCallCaptionGenerator(),
                        new LlmToolCallItemExtractionStrategy(
                                context.chatClientRegistry()
                                        .resolve(ChatClientSlot.TOOL_CALL_EXTRACTION),
                                context.promptRegistry())),
                new ImageContentProcessor(new ImageSegmentComposer(), rawdata.image()),
                new AudioContentProcessor(new TranscriptSegmentChunker(), rawdata.audio()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(
                () -> Map.of("tool_call", ToolCallContent.class),
                () -> Map.of("image", ImageContent.class),
                () -> Map.of("audio", AudioContent.class));
    }
}
