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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.caption.LlmDocumentCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
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
    void pluginPublishesDocumentIngestionPoliciesFromOwnedOptions() {
        var defaults = DocumentExtractionOptions.defaults();
        var plugin =
                new DocumentRawDataPlugin(
                        new DocumentExtractionOptions(
                                new SourceLimitOptions(512),
                                new SourceLimitOptions(4096),
                                defaults.textLikeParsedLimit(),
                                defaults.binaryParsedLimit(),
                                4096,
                                defaults.textLikeChunking(),
                                defaults.binaryChunking(),
                                240,
                                320,
                                4,
                                defaults.llmCaptionEnabled(),
                                defaults.captionConcurrency(),
                                defaults.fallbackCaptionMaxLength()));

        assertThat(plugin.ingestionPolicies())
                .extracting(
                        policy ->
                                Map.entry(policy.governanceType(), policy.sourceLimit().maxBytes()))
                .containsExactlyInAnyOrder(
                        Map.entry(DocumentSemantics.GOVERNANCE_TEXT_LIKE, 512L),
                        Map.entry(DocumentSemantics.GOVERNANCE_BINARY, 4096L));
    }

    @Test
    void pluginOwnsDocumentCaptionDefaults() {
        var defaults = DocumentExtractionOptions.defaults();

        assertThat(defaults.llmCaptionEnabled()).isTrue();
        assertThat(defaults.captionConcurrency()).isEqualTo(4);
        assertThat(defaults.fallbackCaptionMaxLength()).isEqualTo(240);
    }

    @Test
    void pluginUsesCaptionGeneratorSlotForDocumentProcessor() {
        var defaultClient = mock(StructuredChatClient.class);
        when(defaultClient.call(anyList(), eq(LlmDocumentCaptionGenerator.CaptionResponse.class)))
                .thenReturn(Mono.error(new AssertionError("default client should not be used")));

        var slotClient = mock(StructuredChatClient.class);
        when(slotClient.call(anyList(), eq(LlmDocumentCaptionGenerator.CaptionResponse.class)))
                .thenReturn(
                        Mono.just(
                                new LlmDocumentCaptionGenerator.CaptionResponse(
                                        "Retry policy", "Backoff for 429 and 503 responses.")));

        var registry =
                new ChatClientRegistry(
                        defaultClient, Map.of(ChatClientSlot.CAPTION_GENERATOR, slotClient));
        var plugin = new DocumentRawDataPlugin();

        var processor =
                (DocumentContentProcessor)
                        plugin.processors(
                                        new RawDataPluginContext(
                                                registry,
                                                PromptRegistry.EMPTY,
                                                MemoryBuildOptions.defaults()))
                                .getFirst();

        assertThat(
                        processor
                                .captionGenerator()
                                .generate("retry content", Map.of(), "English")
                                .block())
                .isEqualTo("Retry policy\n\nBackoff for 429 and 503 responses.");
        verify(slotClient).call(anyList(), eq(LlmDocumentCaptionGenerator.CaptionResponse.class));
        verify(defaultClient, never())
                .call(anyList(), eq(LlmDocumentCaptionGenerator.CaptionResponse.class));
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
