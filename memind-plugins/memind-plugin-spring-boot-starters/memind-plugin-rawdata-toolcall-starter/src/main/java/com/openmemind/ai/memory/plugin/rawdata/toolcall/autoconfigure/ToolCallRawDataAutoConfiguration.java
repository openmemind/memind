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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.autoconfigure;

import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.plugin.ToolCallRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.stats.DefaultToolCallStatsService;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.stats.ToolCallStatsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ToolCallRawDataPlugin.class)
@EnableConfigurationProperties(ToolCallRawDataProperties.class)
@ConditionalOnProperty(
        prefix = "memind.rawdata.toolcall",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ToolCallRawDataAutoConfiguration {

    @Bean("toolCallRawDataPlugin")
    @ConditionalOnMissingBean(name = "toolCallRawDataPlugin")
    RawDataPlugin toolCallRawDataPlugin(ToolCallRawDataProperties properties) {
        return new ToolCallRawDataPlugin(properties.chunkingOptions());
    }

    @Bean
    @ConditionalOnBean(MemoryStore.class)
    @ConditionalOnMissingBean(ToolCallStatsService.class)
    ToolCallStatsService toolCallStatsService(MemoryStore memoryStore) {
        return new DefaultToolCallStatsService(memoryStore);
    }
}
