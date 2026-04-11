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
package com.openmemind.ai.memory.plugin.content.parser.document.tika.autoconfigure;

import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.content.parser.document.tika.TikaDocumentContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.plugin.DocumentRawDataPlugin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(TikaDocumentContentParser.class)
@EnableConfigurationProperties(TikaDocumentParserProperties.class)
@ConditionalOnProperty(
        prefix = "memind.parser.document.tika",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@Deprecated(forRemoval = false)
public class TikaDocumentParserAutoConfiguration {

    @Bean("documentRawDataPlugin")
    @ConditionalOnMissingBean(name = "documentRawDataPlugin")
    RawDataPlugin documentRawDataPlugin() {
        return new DocumentRawDataPlugin();
    }

    @Bean("tikaDocumentContentParser")
    @ConditionalOnMissingBean(name = "tikaDocumentContentParser")
    ContentParser tikaDocumentContentParser() {
        return new TikaDocumentContentParser();
    }
}
