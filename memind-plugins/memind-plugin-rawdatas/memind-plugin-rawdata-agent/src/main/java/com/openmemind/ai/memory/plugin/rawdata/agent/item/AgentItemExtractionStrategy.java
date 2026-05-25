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
package com.openmemind.ai.memory.plugin.rawdata.agent.item;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentExtractionOptions;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Agent-specific item extraction strategy.
 *
 * <p>Task 9 adds deterministic TOOL/RESOLUTION extraction and Task 10 adds LLM
 * PLAYBOOK/DIRECTIVE extraction.
 */
public class AgentItemExtractionStrategy implements ItemExtractionStrategy {

    private final StructuredChatClient chatClient;
    private final PromptRegistry promptRegistry;
    private final AgentExtractionOptions options;

    public AgentItemExtractionStrategy() {
        this(null, PromptRegistry.EMPTY, AgentExtractionOptions.defaults());
    }

    public AgentItemExtractionStrategy(
            StructuredChatClient chatClient,
            PromptRegistry promptRegistry,
            AgentExtractionOptions options) {
        this.chatClient = chatClient;
        this.promptRegistry = promptRegistry == null ? PromptRegistry.EMPTY : promptRegistry;
        this.options = options == null ? AgentExtractionOptions.defaults() : options;
    }

    @Override
    public Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config) {
        return Mono.just(List.of());
    }

    public StructuredChatClient chatClient() {
        return chatClient;
    }

    public PromptRegistry promptRegistry() {
        return promptRegistry;
    }

    public AgentExtractionOptions options() {
        return options;
    }
}
