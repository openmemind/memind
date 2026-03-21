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
package com.openmemind.ai.memory.autoconfigure;

import com.openmemind.ai.memory.autoconfigure.extraction.MemoryExtractionAutoConfiguration;
import com.openmemind.ai.memory.autoconfigure.retrieval.MemoryRetrievalAutoConfiguration;
import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.stats.DefaultToolStatsService;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * memind top-level auto-configuration
 *
 * <p>Register {@link DefaultMemory} unified facade Bean.
 *
 */
@AutoConfiguration
@AutoConfigureAfter({
    MemoryExtractionAutoConfiguration.class,
    MemoryRetrievalAutoConfiguration.class
})
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean
    @ConditionalOnMissingBean(ToolStatsService.class)
    public ToolStatsService toolStatsService(MemoryStore store) {
        return new DefaultToolStatsService(store);
    }

    @Bean
    @ConditionalOnMissingBean(Memory.class)
    public Memory memind(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            MemoryStore store,
            MemoryVector vector,
            ToolStatsService toolStatsService) {
        return new DefaultMemory(extractor, retriever, store, vector, toolStatsService);
    }
}
