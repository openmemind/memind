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
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.stats.DefaultToolStatsService;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * memind top-level auto-configuration
 *
 * <p>Register {@link Memory} unified facade bean.
 *
 */
@AutoConfiguration
@AutoConfigureAfter({
    MemoryExtractionAutoConfiguration.class,
    MemoryRetrievalAutoConfiguration.class
})
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ToolStatsService.class)
    public ToolStatsService toolStatsService(MemoryStore store) {
        return new DefaultToolStatsService(store);
    }

    @Bean
    @ConditionalOnMissingBean(Memory.class)
    @ConditionalOnBean({
        StructuredChatClient.class,
        MemoryStore.class,
        MemoryBuffer.class,
        MemoryVector.class
    })
    public Memory memind(
            StructuredChatClient chatClient,
            MemoryStore store,
            MemoryBuffer buffer,
            MemoryVector vector,
            ObjectProvider<MemoryTextSearch> textSearchProvider,
            ObjectProvider<MemoryBuildOptions> buildOptionsProvider) {
        var builder =
                Memory.builder().chatClient(chatClient).store(store).buffer(buffer).vector(vector);

        MemoryTextSearch textSearch = textSearchProvider.getIfAvailable();
        if (textSearch != null) {
            builder.textSearch(textSearch);
        }

        MemoryBuildOptions buildOptions = buildOptionsProvider.getIfAvailable();
        if (buildOptions != null) {
            builder.options(buildOptions);
        }

        return builder.build();
    }
}
