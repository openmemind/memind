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
package com.openmemind.ai.memory.plugin.rawdata.agent.plugin;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.plugin.rawdata.agent.AgentRawContentTypeRegistrar;
import com.openmemind.ai.memory.plugin.rawdata.agent.caption.AgentCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.agent.chunk.AgentTimelineChunker;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentRawDataOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.item.AgentItemExtractionStrategy;
import com.openmemind.ai.memory.plugin.rawdata.agent.processor.AgentTimelineContentProcessor;
import java.util.List;

/**
 * RawData plugin contribution for coding-agent timelines.
 */
public final class AgentRawDataPlugin implements RawDataPlugin {

    private final AgentRawDataOptions options;

    public AgentRawDataPlugin() {
        this(AgentRawDataOptions.defaults());
    }

    public AgentRawDataPlugin(AgentRawDataOptions options) {
        this.options = options == null ? AgentRawDataOptions.defaults() : options;
    }

    @Override
    public String pluginId() {
        return "rawdata-agent";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(
                new AgentTimelineContentProcessor(
                        new AgentTimelineChunker(options.chunking(), options.privacy()),
                        new AgentCaptionGenerator(context.chatClientRegistry().defaultClient()),
                        new AgentItemExtractionStrategy(
                                context.chatClientRegistry().defaultClient(),
                                context.promptRegistry(),
                                options.extraction())));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new AgentRawContentTypeRegistrar());
    }
}
