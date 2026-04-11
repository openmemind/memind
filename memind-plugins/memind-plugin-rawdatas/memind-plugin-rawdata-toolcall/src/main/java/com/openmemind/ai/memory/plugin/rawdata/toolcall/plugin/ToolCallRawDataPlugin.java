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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.plugin;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.caption.ToolCallCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.chunk.ToolCallChunker;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.item.strategy.LlmToolCallItemExtractionStrategy;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.processor.ToolCallContentProcessor;
import java.util.List;

public final class ToolCallRawDataPlugin implements RawDataPlugin {

    @Override
    public String pluginId() {
        return "rawdata-toolcall";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(
                new ToolCallContentProcessor(
                        new ToolCallChunker(
                                context.buildOptions().extraction().rawdata().toolCall()),
                        new ToolCallCaptionGenerator(),
                        new LlmToolCallItemExtractionStrategy(
                                context.chatClientRegistry()
                                        .resolve(ChatClientSlot.TOOL_CALL_EXTRACTION),
                                context.promptRegistry())));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of();
    }
}
