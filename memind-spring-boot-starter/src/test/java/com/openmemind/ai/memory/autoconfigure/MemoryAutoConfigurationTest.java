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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryInsightBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.item.MemoryItemLayer;
import com.openmemind.ai.memory.core.extraction.item.extractor.DefaultMemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.strategy.LlmItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.strategy.LlmToolCallItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ToolCallContentProcessor;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategies;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DisplayName("Memory auto-configuration")
class MemoryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemoryLlmAutoConfiguration.class,
                                    MemoryAutoConfiguration.class));

    private final ApplicationContextRunner fullContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemoryLlmAutoConfiguration.class,
                                    com.openmemind.ai.memory.autoconfigure.extraction
                                            .MemoryExtractionAutoConfiguration.class,
                                    com.openmemind.ai.memory.autoconfigure.retrieval
                                            .MemoryRetrievalAutoConfiguration.class,
                                    MemoryAutoConfiguration.class))
                    .withPropertyValues("memind.retrieval.rerank.enabled=false");

    @Test
    @DisplayName("Create Memory from builder runtime inputs only")
    void createsMemoryFromBuilderRuntimeInputsOnly() {
        contextRunner
                .withUserConfiguration(RequiredRuntimeConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(Memory.class);
                            assertThat(context)
                                    .doesNotHaveBean(
                                            com.openmemind.ai.memory.core.extraction
                                                    .MemoryExtractionPipeline.class);
                            assertThat(context)
                                    .doesNotHaveBean(
                                            com.openmemind.ai.memory.core.retrieval.MemoryRetriever
                                                    .class);
                            assertThat(context).doesNotHaveBean(InsightBuffer.class);
                            assertThat(context).doesNotHaveBean(ConversationBuffer.class);
                        });
    }

    @Test
    @DisplayName("Use optional text search and build options when provided")
    void usesOptionalTextSearchAndBuildOptionsWhenProvided() {
        contextRunner
                .withUserConfiguration(OptionalRuntimeConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(Memory.class);
                        });
    }

    @Test
    @DisplayName("Create default PromptRegistry bean and wire it into top-level Memory")
    void createsDefaultPromptRegistryBeanAndUsesItForTopLevelMemory() {
        contextRunner
                .withUserConfiguration(RequiredRuntimeConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(PromptRegistry.class);

                            PromptRegistry promptRegistry = context.getBean(PromptRegistry.class);
                            DefaultMemory memory = (DefaultMemory) context.getBean(Memory.class);
                            MemoryExtractor extractor =
                                    readField(memory, "extractor", MemoryExtractor.class);
                            LlmContextCommitDetector boundaryDetector =
                                    readField(
                                            extractor,
                                            "contextCommitDetector",
                                            LlmContextCommitDetector.class);

                            assertThat(
                                            readField(
                                                    boundaryDetector,
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(promptRegistry);
                        });
    }

    @Test
    @DisplayName("Back off to user-provided PromptRegistry bean")
    void backsOffToUserProvidedPromptRegistryBean() {
        contextRunner
                .withUserConfiguration(
                        RequiredRuntimeConfig.class, CustomPromptRegistryConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            PromptRegistry custom =
                                    context.getBean("customPromptRegistry", PromptRegistry.class);
                            assertThat(context.getBean(PromptRegistry.class)).isSameAs(custom);

                            DefaultMemory memory = (DefaultMemory) context.getBean(Memory.class);
                            DefaultMemoryRetriever retriever =
                                    readField(memory, "retriever", DefaultMemoryRetriever.class);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> strategies =
                                    readField(retriever, "strategies", Map.class);
                            DeepRetrievalStrategy deepStrategy =
                                    (DeepRetrievalStrategy)
                                            strategies.get(RetrievalStrategies.DEEP_RETRIEVAL);
                            LlmTypedQueryExpander typedQueryExpander =
                                    readField(
                                            deepStrategy,
                                            "typedQueryExpander",
                                            LlmTypedQueryExpander.class);

                            assertThat(
                                            readField(
                                                    typedQueryExpander,
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                        });
    }

    @Test
    @DisplayName("Back off when required runtime inputs are missing")
    void backsOffWhenRequiredRuntimeInputsAreMissing() {
        contextRunner
                .withUserConfiguration(MissingVectorConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(Memory.class);
                        });
    }

    @Test
    @DisplayName("Back off when memory buffer is missing")
    void backsOffWhenMemoryBufferIsMissing() {
        contextRunner
                .withUserConfiguration(MissingBufferConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(Memory.class);
                        });
    }

    @Test
    @DisplayName("Route slot-specific clients from ChatClientRegistry into built memory")
    void routesSlotSpecificClientsFromRegistryIntoBuiltMemory() {
        contextRunner
                .withUserConfiguration(MultiClientRuntimeConfig.class)
                .withPropertyValues(
                        "memind.llm.slots.item-extraction=smartClient",
                        "memind.llm.slots.query-expander=fastClient")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            DefaultMemory memory = (DefaultMemory) context.getBean(Memory.class);
                            StructuredChatClient smartClient =
                                    context.getBean("smartClient", StructuredChatClient.class);
                            StructuredChatClient fastClient =
                                    context.getBean("fastClient", StructuredChatClient.class);

                            MemoryExtractor extractor =
                                    readField(memory, "extractor", MemoryExtractor.class);
                            MemoryItemLayer memoryItemLayer =
                                    readField(extractor, "memoryItemStep", MemoryItemLayer.class);
                            DefaultMemoryItemExtractor itemExtractor =
                                    readField(
                                            memoryItemLayer,
                                            "extractor",
                                            DefaultMemoryItemExtractor.class);
                            LlmItemExtractionStrategy itemStrategy =
                                    readField(
                                            itemExtractor,
                                            "defaultStrategy",
                                            LlmItemExtractionStrategy.class);
                            DefaultMemoryRetriever retriever =
                                    readField(memory, "retriever", DefaultMemoryRetriever.class);
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> strategies =
                                    readField(retriever, "strategies", java.util.Map.class);
                            DeepRetrievalStrategy deepStrategy =
                                    (DeepRetrievalStrategy)
                                            strategies.get(RetrievalStrategies.DEEP_RETRIEVAL);
                            LlmTypedQueryExpander typedQueryExpander =
                                    readField(
                                            deepStrategy,
                                            "typedQueryExpander",
                                            LlmTypedQueryExpander.class);

                            assertThat(
                                            readField(
                                                    itemStrategy,
                                                    "structuredChatClient",
                                                    StructuredChatClient.class))
                                    .isSameAs(smartClient);
                            assertThat(
                                            readField(
                                                    typedQueryExpander,
                                                    "structuredChatClient",
                                                    StructuredChatClient.class))
                                    .isSameAs(fastClient);
                        });
    }

    @Test
    @DisplayName("Wire custom PromptRegistry into auto-configured prompt-aware beans")
    void wiresPromptRegistryIntoAutoConfiguredPromptAwareBeans() {
        fullContextRunner
                .withUserConfiguration(
                        RequiredRuntimeConfig.class, CustomPromptRegistryConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            PromptRegistry custom =
                                    context.getBean("customPromptRegistry", PromptRegistry.class);

                            assertThat(
                                            readField(
                                                    context.getBean(InsightTypeRouter.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(SufficiencyGate.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(TypedQueryExpander.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(ContextCommitDetector.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(CaptionGenerator.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(LlmConversationChunker.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);

                            ToolCallContentProcessor toolCallProcessor =
                                    context.getBean(ToolCallContentProcessor.class);
                            LlmToolCallItemExtractionStrategy toolCallStrategy =
                                    readField(
                                            toolCallProcessor,
                                            "itemExtractionStrategy",
                                            LlmToolCallItemExtractionStrategy.class);
                            assertThat(
                                            readField(
                                                    toolCallStrategy,
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);

                            DefaultMemoryItemExtractor itemExtractor =
                                    (DefaultMemoryItemExtractor)
                                            context.getBean(
                                                    com.openmemind.ai.memory.core.extraction.item
                                                            .extractor.MemoryItemExtractor.class);
                            LlmItemExtractionStrategy defaultStrategy =
                                    readField(
                                            itemExtractor,
                                            "defaultStrategy",
                                            LlmItemExtractionStrategy.class);
                            assertThat(
                                            readField(
                                                    defaultStrategy,
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(InsightGenerator.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                            assertThat(
                                            readField(
                                                    context.getBean(InsightGroupClassifier.class),
                                                    "promptRegistry",
                                                    PromptRegistry.class))
                                    .isSameAs(custom);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredRuntimeConfig {

        @Bean
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MemoryBuffer memoryBuffer() {
            return MemoryBuffer.of(
                    new InMemoryInsightBuffer(),
                    new InMemoryConversationBuffer(),
                    new InMemoryRecentConversationBuffer());
        }

        @Bean
        MemoryVector memoryVector() {
            return new NoopMemoryVector();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OptionalRuntimeConfig extends RequiredRuntimeConfig {

        @Bean
        MemoryTextSearch memoryTextSearch() {
            return (memoryId, query, topK, target) ->
                    reactor.core.publisher.Mono.just(java.util.List.of());
        }

        @Bean
        MemoryBuildOptions memoryBuildOptions() {
            return MemoryBuildOptions.defaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingVectorConfig {

        @Bean
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MemoryBuffer memoryBuffer() {
            return MemoryBuffer.of(
                    new InMemoryInsightBuffer(),
                    new InMemoryConversationBuffer(),
                    new InMemoryRecentConversationBuffer());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingBufferConfig {

        @Bean
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MemoryVector memoryVector() {
            return new NoopMemoryVector();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultiClientRuntimeConfig extends RequiredRuntimeConfig {

        @Bean
        @Primary
        @Override
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        StructuredChatClient smartClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        StructuredChatClient fastClient() {
            return new NoopStructuredChatClient();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPromptRegistryConfig {

        @Bean
        PromptRegistry customPromptRegistry() {
            return InMemoryPromptRegistry.builder()
                    .override(PromptType.TYPED_QUERY_EXPAND, "custom query expand")
                    .build();
        }
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.empty();
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.empty();
        }
    }

    private static final class NoopMemoryVector implements MemoryVector {

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.empty();
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.just(List.of());
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
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Failed to read field '" + fieldName + "' from " + target.getClass(), e);
        }
    }
}
