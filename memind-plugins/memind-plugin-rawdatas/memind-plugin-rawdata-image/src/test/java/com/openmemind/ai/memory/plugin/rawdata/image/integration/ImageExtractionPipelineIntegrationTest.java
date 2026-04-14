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
package com.openmemind.ai.memory.plugin.rawdata.image.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.DefaultContentParserRegistry;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InMemoryInsightOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.InMemoryRawDataOperations;
import com.openmemind.ai.memory.core.store.resource.InMemoryResourceOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import com.openmemind.ai.memory.plugin.rawdata.image.ImageSemantics;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.plugin.ImageRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.image.processor.ImageContentProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ImageExtractionPipelineIntegrationTest {

    @Test
    void pluginOwnedParserCanParseFileAndPersistSingleCaptionedRawData() {
        MemoryId memoryId = DefaultMemoryId.of("user-1", "agent-1");
        var plugin =
                new ImageRawDataPlugin(
                        new ImageExtractionOptions(
                                new SourceLimitOptions(1024),
                                new ParsedContentLimitOptions(4096, null, null, null)),
                        List.of(testImageParser()));
        var pluginContext = pluginContext();
        var processor = (ImageContentProcessor) plugin.processors(pluginContext).getFirst();
        var parserRegistry = new DefaultContentParserRegistry(plugin.parsers(pluginContext));
        var store =
                MemoryStore.of(
                        new InMemoryRawDataOperations(),
                        new InMemoryItemOperations(),
                        new InMemoryInsightOperations(),
                        new InMemoryResourceOperations(),
                        null);
        var vector = new RecordingMemoryVector();
        var rawDataLayer =
                new RawDataLayer(
                        List.of(processor), processor.captionGenerator(), store, vector, 16);
        var extractor =
                new DefaultMemoryExtractor(
                        rawDataLayer,
                        (memoryId1, rawDataResult, config) -> Mono.just(MemoryItemResult.empty()),
                        (memoryId1, memoryItemResult) -> Mono.just(InsightResult.empty()),
                        null,
                        null,
                        null,
                        null,
                        new RawContentProcessorRegistry(List.of(processor)),
                        parserRegistry,
                        null,
                        null,
                        new RawDataIngestionPolicyRegistry(plugin.ingestionPolicies()),
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        var result =
                extractor
                        .extract(
                                ExtractionRequest.file(
                                                memoryId,
                                                "chart.png",
                                                new byte[] {1, 2, 3},
                                                "image/png")
                                        .withoutInsight())
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess())
                .withFailMessage("status=%s error=%s", result.status(), result.errorMessage())
                .isTrue();
        assertThat(store.rawDataOperations().listRawData(memoryId))
                .singleElement()
                .satisfies(
                        rawData -> {
                            assertThat(rawData.segment().content())
                                    .isEqualTo("Dashboard screenshot showing Total Revenue 30%");
                            assertThat(rawData.caption()).isEqualTo("Revenue dashboard screenshot");
                        });
        assertThat(vector.storedTexts()).containsExactly("Revenue dashboard screenshot");
    }

    private static ContentParser testImageParser() {
        return new ContentParser() {
            @Override
            public String parserId() {
                return "image-vision";
            }

            @Override
            public String contentType() {
                return ImageContent.TYPE;
            }

            @Override
            public String contentProfile() {
                return ImageSemantics.PROFILE_CAPTION_OCR;
            }

            @Override
            public String governanceType() {
                return ImageSemantics.GOVERNANCE_CAPTION_OCR;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of("image/png");
            }

            @Override
            public Set<String> supportedExtensions() {
                return Set.of(".png");
            }

            @Override
            public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                return Mono.just(
                        new ImageContent(
                                "image/png",
                                "Dashboard screenshot showing Total Revenue 30%",
                                "Revenue dashboard screenshot",
                                source.sourceUrl(),
                                Map.of("provider", "test")));
            }
        };
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

    private static final class RecordingMemoryVector implements MemoryVector {

        private final List<String> storedTexts = new ArrayList<>();

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            storedTexts.add(text);
            return Mono.just("vec-0");
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            storedTexts.addAll(texts);
            return Mono.just(IntStream.range(0, texts.size()).mapToObj(i -> "vec-" + i).toList());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(List.of());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(List.of());
        }

        private List<String> storedTexts() {
            return storedTexts;
        }
    }
}
