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
package com.openmemind.ai.memory.plugin.rawdata.document.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.document.processor.DocumentContentProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DocumentRawDataPluginTest {

    @Test
    void pluginExposesStableIdAndDocumentSubtypeRegistrar() {
        RawDataPlugin plugin = new DocumentRawDataPlugin();

        assertThat(plugin.pluginId()).isEqualTo("rawdata-document");
        assertThat(plugin.typeRegistrars())
                .singleElement()
                .extracting(RawContentTypeRegistrar::subtypes)
                .satisfies(mappings -> assertThat(mappings).containsKey("document"));
    }

    @Test
    void pluginExposesDocumentProcessorAndParsers() {
        RawDataPlugin plugin = new DocumentRawDataPlugin();

        assertThat(plugin.processors(pluginContext()))
                .singleElement()
                .isInstanceOf(DocumentContentProcessor.class);
        assertThat(plugin.parsers(pluginContext()))
                .extracting(ContentParser::parserId)
                .containsExactly("document-native-text", "document-tika");
    }

    @Test
    void rawContentJacksonRegistersDocumentSubtypeWithoutJsonSubTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RawContentJackson.registerAll(mapper, List.of(new DocumentRawContentTypeRegistrar()));

        var decoded =
                mapper.readValue(
                        "{\"type\":\"document\",\"title\":\"Guide\",\"mimeType\":\"text/plain\",\"parsedText\":\"hello\",\"sections\":[],\"metadata\":{}}",
                        com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent.class);

        assertThat(decoded).isInstanceOf(DocumentContent.class);
    }

    private static RawDataPluginContext pluginContext() {
        return new RawDataPluginContext(
                new ChatClientRegistry(noopClient(), Map.of()),
                PromptRegistry.EMPTY,
                MemoryBuildOptions.defaults());
    }

    private static StructuredChatClient noopClient() {
        return new StructuredChatClient() {
            @Override
            public Mono<String> call(List<ChatMessage> messages) {
                return Mono.error(new UnsupportedOperationException("not used by this test"));
            }

            @Override
            public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
                return Mono.error(new UnsupportedOperationException("not used by this test"));
            }
        };
    }
}
